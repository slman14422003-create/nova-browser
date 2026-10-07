package com.nova.browser

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * ملف دعم يوتيوب: يحقن طبقات الإصلاح والتحسين (assets/yt-fix.js) مبكراً في كل صفحات يوتيوب،
 * بغضّ النظر عن وضع الواجهة (الأصلية أو عرض الموقع أو المشغّل المصغّر):
 *  - مدير الترجمة (لغات حقيقية + ترجمة تلقائية + تذكّر الاختيار) وشكلها الحديث في منتصف المشغّل.
 *  - إخفاء لافتات «افتح التطبيق» وإصلاح قياس الفيديو بعد تدوير الشاشة.
 * لا يمسّ الإعلانات ولا يستخرج روابط البث. التفاصيل في docs/YOUTUBE.md.
 */
object YtSupport {
    private val js: String? by lazy { runCatching { Perf.asset("yt-fix.js") }.getOrNull() }
    private val origins = setOf("https://m.youtube.com", "https://www.youtube.com", "https://youtube.com")
    private val docStart by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }

    private fun isYt(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return u.scheme == "https" && "https://${u.host}" in origins
    }

    /** يُستدعى عند إنشاء كل WebView: الحقن المبكر قبل أي سكربت للصفحة. */
    fun install(wv: WebView) {
        val src = js ?: return
        if (docStart) runCatching { WebViewCompat.addDocumentStartJavaScript(wv, src, origins) }
    }

    /** احتياطي للأجهزة التي لا تدعم الحقن المبكر. */
    fun onPageStart(wv: WebView, url: String) {
        if (docStart || !isYt(url)) return
        val src = js ?: return
        wv.evaluateJavascript(src, null)
    }
}
