package com.nova.browser

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * نقطة الدخول الوحيدة لكل ما يخص يوتيوب (مجلد yt/):
 *   - YtHub     : التركيب في الـ WebView (مستمعو الرسائل + حقن assets/yt-all.js مرة واحدة)
 *   - YtApp     : جسر الواجهة الأصلية (حالة القوائم/المشاهدة)           — YtApp.kt
 *   - YtScreen  : شاشة Compose الأصلية                                    — YtScreen.kt
 *   - YtMedia / YtBridge / YtLog : إشعار الوسائط والخلفية والتشخيص       — YtMedia.kt
 *   - YtMini    : المشغّل المصغّر                                         — YtMini.kt
 *   - YtDownload: تنزيل الفيديو/الصوت                                     — YtDownload.kt
 * بقية التطبيق (MainActivity وغيرها) لا تستدعي سوى YtHub.install وYtHub.onPageStart.
 *
 * قبل: ثلاثة سكربتات (yt-app/yt-fix/yt) تُقرأ من assets وتُحقن كلٌّ على حدة في كل WebView جديد.
 * الآن: ملف واحد يُقرأ ويُجهَّز مرة واحدة ويُحقن بنداء واحد.
 */
object YtHub {
    private val docStart by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }

    /** المصادر المسموحة للحقن (تتضمن Music لقسم MEDIA فقط؛ بقية الأقسام تتجاهله بنفسها). */
    private val origins get() = YtBridge.origins
    private val ytHosts = setOf("youtube.com", "m.youtube.com", "www.youtube.com", "music.youtube.com")

    private val raw: String? by lazy { runCatching { Perf.asset("yt-all.js") }.getOrNull() }

    // النص النهائي بعد استبدال الإعدادات؛ يُعاد حسابه فقط عند تغيّر قيمتَي ytNative/ytBg
    private var built: String? = null
    private var builtKey = ""

    fun ready() = raw != null

    private fun script(): String? {
        val r = raw ?: return null
        val key = "${Prefs.ytNative}|${Prefs.ytBg}|${Prefs.ytResume}|${Prefs.ytKeepRate}|${Prefs.ytHold2x}"
        if (built == null || builtKey != key) {
            built = r.replace("__CFG__", "{\"on\":${Prefs.ytNative}}").replace("__BG__", Prefs.ytBg.toString())
                .replace("__PLAY__", "{\"resume\":${Prefs.ytResume},\"keep\":${Prefs.ytKeepRate},\"hold\":${Prefs.ytHold2x}}")
            builtKey = key
        }
        return built
    }

    private fun isYt(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return u.scheme == "https" && u.host in ytHosts
    }

    /** يُستدعى عند إنشاء كل WebView: مستمعو الرسائل ثم حقن الملف الموحّد قبل أي سكربت للصفحة. */
    fun install(wv: WebView, tab: BrowserTab, h: Handlers) {
        YtApp.listen(wv, tab)
        YtBridge.listen(wv, tab, h)
        val js = script() ?: return
        if (docStart) runCatching { WebViewCompat.addDocumentStartJavaScript(wv, js, origins) }
    }

    /** احتياطي للأجهزة بلا حقن مبكر. */
    fun onPageStart(wv: WebView, url: String) {
        if (docStart || !isYt(url)) return
        script()?.let { wv.evaluateJavascript(it, null) }
    }
}
