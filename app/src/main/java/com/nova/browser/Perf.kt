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
    val privacyHeaders = mapOf("DNT" to "1", "Sec-GPC" to "1")

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

    private fun isBlocked(host: String?): Boolean {
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
        wv.isScrollbarFadingEnabled = true
        wv.overScrollMode = android.view.View.OVER_SCROLL_NEVER
        // ملاحظة: لا نستخدم LAYER_TYPE_HARDWARE؛ يضيف مخزناً خارج الشاشة ويزيد الذاكرة
    }

    // ---------- الحماية من البصمة ----------
    private val seed: Long = java.security.SecureRandom().nextLong() and 0xFFFFFFFFL

    private val fpScript: String? by lazy {
        runCatching { app.assets.open("privacy.js").bufferedReader().use { it.readText() }.replace("__SEED__", seed.toString()) }.getOrNull()
    }

    private val docStartSupported by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }

    /** يحقن سكربت الحماية قبل أي سكربت للصفحة (إن كان مدعوماً). */
    fun installPrivacy(wv: WebView) {
        val js = fpScript ?: return
        if (Prefs.antiFingerprint && docStartSupported)
            runCatching { WebViewCompat.addDocumentStartJavaScript(wv, js, setOf("*")) }
    }

    /** احتياطي للأجهزة التي لا تدعم الحقن المبكر: يُنفَّذ عند بدء الصفحة. */
    fun onPageStart(wv: WebView) {
        if (Prefs.antiFingerprint && !docStartSupported) fpScript?.let { wv.evaluateJavascript(it, null) }
    }

    /** تحميل كسول للصور والإطارات التي لا تحدد loading، لتسريع الصفحات الثقيلة. */
    private const val LAZY_JS = "(function(){try{var f=function(){document.querySelectorAll('img:not([loading]),iframe:not([loading])').forEach(function(e){e.loading='lazy';e.decoding='async'})};f();new MutationObserver(f).observe(document.documentElement,{childList:true,subtree:true})}catch(e){}})();"

    fun onPageDone(wv: WebView) {
        if (Prefs.lazyMedia) wv.evaluateJavascript(LAZY_JS, null)
    }

    /** تسخين محرك الويب وفحص الأمان مبكراً لتسريع أول تصفح. */
    fun warmUp(ctx: Context) {
        runCatching { WebView.startSafeBrowsing(ctx.applicationContext, null) }
    }
}
