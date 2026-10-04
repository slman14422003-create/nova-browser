package com.nova.browser

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream

/** تحسينات الأداء والخصوصية: حجب إعلانات/متتبعات، حماية من البصمة، تحميل كسول للصور، وضبط الـ WebView. */
object Perf {
    private lateinit var app: Context
    fun init(c: Context) { app = c.applicationContext }

    /** ترويسات الخصوصية للتحميلات التي يبدؤها التطبيق (Do Not Track + Global Privacy Control). */
    val privacyHeaders: Map<String, String>
        get() {
            val h = linkedMapOf("DNT" to "1", "Sec-GPC" to "1")
            val al = LangUtil.acceptLanguage(Prefs.siteLangCode())   // لغة المواقع المفضّلة
            if (al.isNotEmpty()) h["Accept-Language"] = al
            return h
        }

    /** قائمة النطاقات تُقرأ مرة واحدة من assets/blocklist.txt. */
    private val blocked: Set<String> by lazy {
        runCatching {
            app.assets.open("blocklist.txt").bufferedReader().useLines { ls ->
                ls.map { it.trim().lowercase() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toHashSet()
            }
        }.getOrDefault(emptySet())
    }

    /** بادئات نطاقات فرعية دالّة على إعلانات/تتبع. */
    private val trackerLabels = setOf("ads", "adserver", "adservice", "tracking", "tracker", "pixel", "telemetry", "beacon")

    // ذاكرة قرارات الحجب لكل نطاق: كل صفحة تطلب عشرات الموارد من نفس النطاقات
    private val verdicts = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private fun isBlocked(host: String?): Boolean {
        val h = host ?: return false
        verdicts[h]?.let { return it }
        val r = computeBlocked(h)
        if (verdicts.size > 4000) verdicts.clear()
        verdicts[h] = r
        return r
    }

    private var lastFlush = 0L
    /** حفظ الكوكيز على القرص بحدّ أقصى مرة كل 15 ثانية (الاستدعاء المتكرر يسبب تقطيعاً). */
    fun flushCookies(force: Boolean = false) {
        val n = android.os.SystemClock.elapsedRealtime()
        if (force || n - lastFlush > 15000) { lastFlush = n; android.webkit.CookieManager.getInstance().flush() }
    }

    private fun computeBlocked(host: String?): Boolean {
        val h0 = host?.lowercase() ?: return false
        if (h0.substringBefore('.') in trackerLabels && h0.contains('.')) return true
        var h = h0
        while (true) {
            if (h in blocked) return true
            val i = h.indexOf('.')
            if (i < 0) return false
            h = h.substring(i + 1)
        }
    }

    private val empty get() = WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))

    /** يُستدعى من shouldInterceptRequest؛ يعيد null للسماح بالطلب. */
    fun intercept(url: Uri, isMainFrame: Boolean): WebResourceResponse? =
        if (Prefs.blockAds && !isMainFrame && isBlocked(url.host)) { Security.countBlocked(); empty } else null

    /** يضبط إعدادات الأداء لكل WebView جديد. */
    fun tune(wv: WebView) {
        val s = wv.settings
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.setSafeBrowsingEnabled(true)
        s.allowContentAccess = false
        s.allowFileAccess = false
        s.loadsImagesAutomatically = !Prefs.dataSaver
        s.blockNetworkImage = Prefs.dataSaver
        s.setOffscreenPreRaster(false)   // يوفر الذاكرة للتبويبات الخلفية
        // التبويب الظاهر بأولوية عالية؛ وعملية العرض للتبويب المخفي تفقد أولويتها فيُحرَّر المعالج والذاكرة للتبويب الحالي
        runCatching { wv.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true) }
        s.textZoom = Prefs.zoomValues[Prefs.textZoom]
        applyDark(wv)
        wv.isScrollbarFadingEnabled = true
        wv.overScrollMode = android.view.View.OVER_SCROLL_NEVER
        // ملاحظة: لا نستخدم LAYER_TYPE_HARDWARE؛ يضيف مخزناً خارج الشاشة ويزيد الذاكرة
    }

    // ---------- الحماية من البصمة ----------
    private val seed: Long = java.security.SecureRandom().nextLong() and 0xFFFFFFFFL

    private val fpTemplate: String? by lazy {
        runCatching { app.assets.open("privacy.js").bufferedReader().use { it.readText() } }.getOrNull()
    }
    private fun fpScript(): String? = fpTemplate?.replace("__SEED__", seed.toString())?.replace("__LANG__", Prefs.siteLangCode())

    /** الوضع الداكن للمواقع (يتبع وضع النظام الداكن). */
    fun applyDark(wv: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))
            runCatching { androidx.webkit.WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, Prefs.siteDark) }
    }

    /** تطبيق حجم الخط والوضع الداكن على تبويب موجود. */
    fun applyDisplay(wv: WebView) { wv.settings.textZoom = Prefs.zoomValues[Prefs.textZoom]; applyDark(wv) }

    private val docStartSupported by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }

    /** يحقن سكربت الحماية قبل أي سكربت للصفحة (إن كان مدعوماً). */
    fun installPrivacy(wv: WebView) {
        if (Prefs.fastMode && docStartSupported) runCatching { WebViewCompat.addDocumentStartJavaScript(wv, FAST_JS, setOf("*")) }
        val js = fpScript() ?: return
        if (Prefs.antiFingerprint && docStartSupported)
            runCatching { WebViewCompat.addDocumentStartJavaScript(wv, js, setOf("*")) }
    }

    /** احتياطي للأجهزة التي لا تدعم الحقن المبكر: يُنفَّذ عند بدء الصفحة. */
    fun onPageStart(wv: WebView) {
        if (Prefs.fastMode && !docStartSupported) wv.evaluateJavascript(FAST_JS, null)
        if (Prefs.antiFingerprint && !docStartSupported) fpScript()?.let { wv.evaluateJavascript(it, null) }
    }

    /** تحميل كسول للصور والإطارات التي لا تحدد loading، لتسريع الصفحات الثقيلة. */
    private const val LAZY_JS = "(function(){try{if(window.__nz)return;window.__nz=1;var t=0;var f=function(){t=0;var l=document.querySelectorAll('img:not([loading]),iframe:not([loading])');for(var i=0;i<l.length;i++){l[i].loading='lazy';if(l[i].tagName==='IMG')l[i].decoding='async'}};f();var o=new MutationObserver(function(){if(!t)t=setTimeout(f,600)});o.observe(document.documentElement,{childList:true,subtree:true});setTimeout(function(){o.disconnect()},15000)}catch(e){}})();"

    fun onPageDone(wv: WebView) {
        if (Prefs.lazyMedia) wv.evaluateJavascript(LAZY_JS, null)
    }

    /** وضع السلاسة (اختياري): مستمعو اللمس/العجلة سلبيون افتراضياً (تمرير أنعم)، بلا تمرير متحرك ولا ضبابية خلفية ثقيلة. */
    private const val FAST_JS = "(function(){try{if(window.__nf)return;window.__nf=1;var o=EventTarget.prototype.addEventListener,p={touchstart:1,touchmove:1,wheel:1,mousewheel:1};EventTarget.prototype.addEventListener=function(t,l,x){if(p[t]){if(x===undefined||x===false||x===true)x={capture:x===true,passive:true};else if(typeof x==='object'&&x&&x.passive===undefined)x=Object.assign({},x,{passive:true})}return o.call(this,t,l,x)};var s=document.createElement('style');s.textContent='html{scroll-behavior:auto!important}*{backdrop-filter:none!important;-webkit-backdrop-filter:none!important}';var r=document.head||document.documentElement;if(r)r.appendChild(s)}catch(e){}})();"

    // ---------- تسخين الشبكة ----------
    private val dnsPool by lazy { java.util.concurrent.Executors.newFixedThreadPool(2) { r -> Thread(r, "nova-dns").apply { isDaemon = true } } }
    private val dnsSeen = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** يحلّ اسم النطاق مسبقاً في خيط خلفي (يُسخّن كاش DNS للنظام) فيبدأ أول تحميل أسرع. */
    fun prefetchDns(url: String?) {
        val host = runCatching { Uri.parse(url ?: return).host }.getOrNull() ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        val last = dnsSeen[host]
        if (last != null && now - last < 120_000) return
        dnsSeen[host] = now
        dnsPool.execute { runCatching { java.net.InetAddress.getAllByName(host) } }
    }

    /** تسخين محرك الويب وفحص الأمان مبكراً لتسريع أول تصفح. */
    fun warmUp(ctx: Context) {
        runCatching { WebView.startSafeBrowsing(ctx.applicationContext, null) }
        prefetchDns(Prefs.engines[Prefs.engine].second)
    }
}
