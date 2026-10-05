package com.nova.browser

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/** نوع الصفحة من منظور «وضع التطبيق». */
enum class SiteKind { NONE, YT, YT_VIDEO, AI }

/** هوية الموقع المعروضة في الشريط العلوي. */
class SiteInfo(val kind: SiteKind, val name: String, val host: String)

/**
 * وضع PWA متقدم ليوتيوب ومواقع الذكاء الاصطناعي:
 * - يكتشف نوع الصفحة (يوتيوب / فيديو / ذكاء اصطناعي) لعرض الشريط العلوي المناسب.
 * - يحقن pwa.js على هذه النطاقات فقط (محاكاة standalone، إخفاء لافتة «افتح التطبيق»، مظهر تطبيق).
 */
object Pwa {
    const val BAR_H = 54   // dp: ارتفاع الشريط العلوي (يشمل خط التقدّم)

    private val aiNames = mapOf(
        "gemini.google.com" to "Gemini", "chatgpt.com" to "ChatGPT", "chat.openai.com" to "ChatGPT",
        "claude.ai" to "Claude", "perplexity.ai" to "Perplexity", "copilot.microsoft.com" to "Copilot",
        "chat.deepseek.com" to "DeepSeek", "grok.com" to "Grok", "poe.com" to "Poe", "chat.mistral.ai" to "Le Chat"
    )

    /** النطاقات التي يُحقن فيها pwa.js (الدالة داخل السكربت تضيّق google.com على udm=50 فقط). */
    val origins: Set<String> = buildSet {
        add("https://m.youtube.com"); add("https://www.youtube.com"); add("https://music.youtube.com"); add("https://youtube.com")
        add("https://www.google.com")
        aiNames.keys.forEach { add("https://$it") }
        add("https://www.perplexity.ai")
    }

    private val js: String? by lazy {
        runCatching { Perf.asset("pwa.js") }.getOrNull()
    }

    private val docStart by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }

    fun install(wv: WebView) {
        val s = js ?: return
        if (Prefs.pwaMode && docStart) runCatching { WebViewCompat.addDocumentStartJavaScript(wv, s, origins) }
    }

    @Volatile private var lastUrl: String? = null
    @Volatile private var lastInfo: SiteInfo? = null

    /** معلومات الموقع أو null إن لم يكن ضمن وضع التطبيق. مُخزَّنة لآخر رابط لأنها تُستدعى مع كل حركة لمس. */
    fun info(url: String?): SiteInfo? {
        if (url.isNullOrEmpty()) return null
        if (url == lastUrl) return lastInfo
        val r = compute(url)
        lastUrl = url; lastInfo = r
        return r
    }

    fun kind(url: String?): SiteKind = info(url)?.kind ?: SiteKind.NONE

    private fun compute(url: String): SiteInfo? {
        val p = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (p.scheme != "https") return null
        val raw = (p.host ?: return null).lowercase()
        val h = raw.removePrefix("www.")
        if (h == "youtu.be" || h == "youtube.com" || h.endsWith(".youtube.com")) {
            val music = h == "music.youtube.com"
            return SiteInfo(if (isYtVideo(url)) SiteKind.YT_VIDEO else SiteKind.YT, if (music) "YouTube Music" else "YouTube", raw)
        }
        aiNames[h]?.let { return SiteInfo(SiteKind.AI, it, raw) }
        if (h.startsWith("google.") && (p.getQueryParameter("udm") == "50" || (p.path ?: "").startsWith("/ai")))
            return SiteInfo(SiteKind.AI, "Google AI Mode", raw)
        return null
    }
}
