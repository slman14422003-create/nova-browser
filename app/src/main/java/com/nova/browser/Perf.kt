package com.nova.browser

import android.content.Context
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import java.io.ByteArrayInputStream

/** تحسينات الأداء والتصفح: حاجب إعلانات/متتبعات خفيف، تحميل كسول للصور، وضبط إعدادات الـ WebView. */
object Perf {
    /** نطاقات إعلانات/تتبع شائعة (مطابقة على النطاق وما تحته). */
    private val blocked = hashSetOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com", "facebook.net",
        "connect.facebook.net", "adnxs.com", "adsrvr.org", "taboola.com", "outbrain.com",
        "scorecardresearch.com", "criteo.com", "pubmatic.com", "rubiconproject.com", "openx.net",
        "amazon-adsystem.com", "hotjar.com", "mixpanel.com", "moatads.com", "quantserve.com"
    )

    private fun isBlocked(host: String?): Boolean {
        var h = host ?: return false
        while (true) {
            if (h in blocked) return true
            val i = h.indexOf('.')
            if (i < 0) return false
            h = h.substring(i + 1)
        }
    }

    private val empty get() = WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))

    /** يُستدعى من shouldInterceptRequest؛ يعيد null للسماح بالطلب. */
    fun intercept(url: android.net.Uri, isMainFrame: Boolean): WebResourceResponse? =
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
        wv.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        wv.isScrollbarFadingEnabled = true
        wv.overScrollMode = android.view.View.OVER_SCROLL_NEVER
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
