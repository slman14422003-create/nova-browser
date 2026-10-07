package com.nova.browser

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewRenderProcess
import androidx.webkit.WebViewRenderProcessClient
import java.net.InetAddress
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

/**
 * أدوات دعم الـ WebView: كل ما يخص صحّة المحرك وتسريع التنقل وأدوات الصفحة (قراءة/تمرير/تحديث كامل).
 * كل استدعاء لميزة في androidx.webkit محمي بفحص الدعم، فلا ينهار التطبيق على محركات قديمة.
 */
object WebSupport {
    private val main = Handler(Looper.getMainLooper())
    private val attached: MutableSet<WebView> = Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())
    private val hard: MutableSet<WebView> = Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())
    private val stuck = WeakHashMap<WebView, Runnable>()
    @Volatile var online = true; private set
    private var registered = false

    private val docStart by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }
    private val boostJs: String? by lazy { runCatching { Perf.asset("boost.js") }.getOrNull() }
    private val readerJs: String? by lazy { runCatching { Perf.asset("reader.js") }.getOrNull() }

    // ---------- تهيئة عامة ----------
    fun init(c: Context) {
        val app = c.applicationContext
        // تصحيح الصفحات عن بُعد (chrome://inspect) في نسخة debug فقط
        runCatching { WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG) }
        if (registered) return
        registered = true
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            online = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: true
            // مراقبة الشبكة: navigator.onLine صحيح داخل الصفحات + إعادة تحميل صفحات الخطأ تلقائياً عند عودة الاتصال
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { main.post { setOnline(true) } }
                override fun onLost(network: Network) { main.post { setOnline(false) } }
            })
        }
    }

    private fun setOnline(v: Boolean) {
        val was = online
        online = v
        attached.toList().forEach { runCatching { it.setNetworkAvailable(v) } }
        if (v && !was) retryFailed()
    }

    /** يضغط زر «إعادة المحاولة» في صفحات الخطأ المعروضة حالياً (علامتها data-nova-err). */
    private fun retryFailed() {
        val js = "(function(){var b=document.querySelector('[data-nova-err] button');if(b){b.click();return 1}return 0})()"
        attached.toList().forEach { runCatching { it.evaluateJavascript(js, null) } }
    }

    // ---------- إعداد كل WebView جديد ----------
    fun configure(wv: WebView) {
        attached.add(wv)
        runCatching { wv.setNetworkAvailable(online) }
        // المحرك يخفّض أولوية عملية العرض للتبويبات غير الظاهرة (توفير ذاكرة وحرارة)
        runCatching { wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true) }
        // إزالة ترويسة X-Requested-With (تكشف اسم الحزمة وتجعل بعض المواقع تعامل التطبيق كمتصفح مضمَّن)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST))
            runCatching { WebSettingsCompat.setRequestedWithHeaderOriginAllowList(wv.settings, emptySet()) }
        watchRenderer(wv)
    }

    /** إن توقفت عملية العرض عن الاستجابة أكثر من 10 ثوانٍ نُنهيها فيُعيد التطبيق إنشاء التبويب (بدل تجمّد الصفحة). */
    private fun watchRenderer(wv: WebView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE)) return
        runCatching {
            WebViewCompat.setWebViewRenderProcessClient(wv, Executor { r -> main.post(r) }, object : WebViewRenderProcessClient() {
                override fun onRenderProcessUnresponsive(view: WebView, renderer: WebViewRenderProcess?) {
                    if (stuck.containsKey(view)) return
                    val kill = Runnable {
                        stuck.remove(view)
                        if (renderer != null && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_TERMINATE)) {
                            toast(view.context, L("الصفحة لا تستجيب — جارٍ إعادة تحميلها"))
                            runCatching { renderer.terminate() }
                        }
                    }
                    stuck[view] = kill
                    main.postDelayed(kill, 10_000)
                }
                override fun onRenderProcessResponsive(view: WebView, renderer: WebViewRenderProcess?) {
                    stuck.remove(view)?.let { main.removeCallbacks(it) }
                }
            })
        }
    }

    // ---------- تسريع التنقل ----------
    /** يحقن boost.js (جلب مسبق للروابط عند اللمس الطويل + إيقاف الفيديوهات الصامتة خارج الشاشة). */
    fun installBoost(wv: WebView) {
        val js = boostJs ?: return
        if (Prefs.boost && docStart) runCatching { WebViewCompat.addDocumentStartJavaScript(wv, js, setOf("*")) }
    }

    fun onPageStart(wv: WebView) {
        if (Prefs.boost && !docStart) boostJs?.let { wv.evaluateJavascript(it, null) }
    }

    fun onPageDone(wv: WebView) {
        // التحديث الكامل يعطّل الكاش لتحميل واحد فقط ثم يعود الوضع الافتراضي
        if (hard.remove(wv)) runCatching { wv.settings.cacheMode = WebSettings.LOAD_DEFAULT }
    }

    private val dnsPool = Executors.newFixedThreadPool(2, ThreadFactory { r -> Thread(r, "nova-dns").apply { isDaemon = true } })
    private val warmed = ConcurrentHashMap<String, Long>()

    /** يحلّ اسم النطاق مسبقاً في خيط خلفي (يملأ كاش DNS للنظام) كي يبدأ الاتصال أسرع عند الفتح. */
    fun prefetchDns(host: String?) {
        if (!Prefs.boost) return
        val h = host?.trim()?.lowercase()?.takeIf { it.length in 3..100 && it.contains('.') && !it.contains(' ') } ?: return
        val now = SystemClock.elapsedRealtime()
        val last = warmed[h]
        if (last != null && now - last < 120_000) return
        if (warmed.size > 200) warmed.clear()
        warmed[h] = now
        dnsPool.execute { runCatching { InetAddress.getAllByName(h) } }
    }

    // ---------- أدوات الصفحة ----------
    fun hardReload(wv: WebView) {
        runCatching { wv.settings.cacheMode = WebSettings.LOAD_NO_CACHE }
        hard.add(wv)
        wv.reload()
    }

    fun scroll(wv: WebView, top: Boolean) {
        val js = if (top) "window.scrollTo({top:0,behavior:'smooth'})"
        else "window.scrollTo({top:Math.max(document.body?document.body.scrollHeight:0,document.documentElement.scrollHeight),behavior:'smooth'})"
        wv.evaluateJavascript(js, null)
    }

    /** وضع القراءة: يعرض نص المقالة فقط فوق الصفحة (إعادة الضغط تُغلقه). */
    fun reader(wv: WebView) {
        val js = readerJs ?: return
        wv.evaluateJavascript(js) { r ->
            if (r != null && r.contains("none")) toast(wv.context, L("لا توجد مقالة يمكن عرضها في وضع القراءة"))
        }
    }
}

/** أيقونات المواقع (favicon) التي تصل من الصفحات نفسها: لا طلبات شبكة إضافية ولا تسريب للنطاقات. */
object Favicons {
    private val cache = android.util.LruCache<String, Bitmap>(64)
    /** يتغيّر عند وصول أيقونة جديدة ليُعاد رسم الواجهات التي تعرضها. */
    var version by mutableIntStateOf(0); private set

    fun get(host: String): Bitmap? = cache.get(host)

    fun put(host: String, icon: Bitmap) {
        if (host.isBlank() || icon.width <= 0 || icon.height <= 0 || cache.get(host) != null) return
        val b = runCatching { Bitmap.createScaledBitmap(icon, 48, 48, true) }.getOrNull() ?: return
        cache.put(host, b)
        version++
    }
}
