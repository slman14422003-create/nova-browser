package com.nova.browser

import android.net.Uri
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

class YtVideo(val id: String, val title: String, val channel: String, val meta: String, val dur: String) {
    val url get() = "https://www.youtube.com/watch?v=$id"
}
class YtComment(val author: String, val text: String, val time: String, val likes: String)
class YtWatchData(
    val id: String, val title: String, val channel: String, val subs: String, val info: String, val likes: String,
    val liked: Boolean, val subscribed: Boolean, val desc: String, val commentsLabel: String, val commentPreview: String
)

/** حالة واجهة يوتيوب الأصلية لتبويب واحد. المصدر هو DOM الصفحة (يصل عبر yt-app.js). */
class YtSession {
    var key by mutableStateOf("")                       // مفتاح الصفحة التي جاءت منها آخر لقطة
    var items by mutableStateOf<List<YtVideo>>(emptyList())
    var chips by mutableStateOf<List<Pair<String, Boolean>>>(emptyList())
    var watch by mutableStateOf<YtWatchData?>(null)
    var comments by mutableStateOf<List<YtComment>>(emptyList())
    var showComments by mutableStateOf(false)
    var showSite by mutableStateOf(false)               // عرض الموقع الأصلي بدل الواجهة الأصلية
    var searching by mutableStateOf(false)
    var query by mutableStateOf("")
    var diag by mutableStateOf<String?>(null)
    val recent = mutableStateListOf<String>()
    var wantMode = true                                 // هل يجب تفعيل وضع المشغّل في الصفحة (تضبطه الواجهة)
    var lastMore = 0L
}

/**
 * جسر «يوتيوب كمصدر بيانات»: الصفحة تبقى حيّة خلف الواجهة الأصلية وتوفّر القوائم والبيانات،
 * ومشغّلها الحقيقي يظهر في أعلى شاشة المشاهدة. لا نستخرج روابط البث ولا نعدّل الإعلانات.
 */
object YtApp {
    private val js: String? by lazy { runCatching { Perf.asset("yt-app.js") }.getOrNull() }
    private val origins = setOf("https://m.youtube.com", "https://www.youtube.com", "https://youtube.com")
    private val docStart by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }
    private val listenerOk by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) }

    private fun cfg() = "{\"on\":${Prefs.ytNative}}"

    fun available() = js != null && listenerOk

    fun supports(url: String?): Boolean {
        if (!available() || url.isNullOrEmpty()) return false
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return u.scheme == "https" && "https://${u.host}" in origins
    }

    fun pageOf(url: String?): String {
        val p = runCatching { Uri.parse(url ?: "").path }.getOrNull().orEmpty().ifEmpty { "/" }
        return when {
            p == "/" -> "home"
            p == "/results" -> "search"
            p == "/watch" -> "watch"
            p.startsWith("/shorts") -> "shorts"
            p == "/feed/subscriptions" -> "subs"
            p.startsWith("/feed/library") || p.startsWith("/feed/history") || p.startsWith("/feed/you") -> "library"
            p == "/playlist" -> "playlist"
            p.startsWith("/@") || p.startsWith("/channel/") || p.startsWith("/c/") || p.startsWith("/user/") -> "channel"
            else -> "other"
        }
    }

    /** صفحات تعرضها الواجهة الأصلية؛ غيرها (Shorts وغيرها) يبقى بعرض الموقع. */
    fun nativePage(url: String?) = pageOf(url) in setOf("home", "search", "watch", "subs", "library", "channel", "playlist")

    /** نفس صيغة المفتاح في yt-app.js: المسار + معرّف الفيديو أو كلمة البحث. */
    fun urlKey(url: String?): String {
        val u = runCatching { Uri.parse(url ?: "") }.getOrNull() ?: return ""
        val p = (u.path ?: "").ifEmpty { "/" }
        return p + "?" + (u.getQueryParameter("v") ?: u.getQueryParameter("search_query") ?: "")
    }

    fun install(wv: WebView, tab: BrowserTab) {
        val base = js ?: return
        if (!Prefs.pwaMode || !Prefs.ytNative || !listenerOk) return
        runCatching {
            WebViewCompat.addWebMessageListener(wv, "NovaYtApp", origins, WebViewCompat.WebMessageListener { _, message, _, isMain, _ ->
                if (!isMain) return@WebMessageListener
                val data = message.data ?: return@WebMessageListener
                if (data.length > 800_000) return@WebMessageListener
                handle(tab, data)
            })
        }
        if (docStart) runCatching { WebViewCompat.addDocumentStartJavaScript(wv, base.replace("__CFG__", cfg()), origins) }
    }

    /** احتياطي للأجهزة بلا حقن مبكر. */
    fun onPageStart(wv: WebView, url: String) {
        if (docStart || !Prefs.ytNative || !Prefs.pwaMode || !supports(url)) return
        val base = js ?: return
        wv.evaluateJavascript(base.replace("__CFG__", cfg()), null)
    }

    private fun handle(tab: BrowserTab, data: String) {
        val o = runCatching { JSONObject(data) }.getOrNull() ?: return
        val s = tab.yt
        when (o.optString("t")) {
            "ys" -> {
                if (o.optBoolean("on") != s.wantMode) call(tab, "mode(${s.wantMode})")   // مزامنة وضع المشغّل (دوران الشاشة/عرض الموقع)
                s.key = o.optString("key")
                val a = o.optJSONArray("items")
                if (a != null) {
                    val l = ArrayList<YtVideo>(a.length())
                    for (i in 0 until a.length()) {
                        val v = a.optJSONObject(i) ?: continue
                        l += YtVideo(v.optString("id"), v.optString("t"), v.optString("c"), v.optString("m"), v.optString("d"))
                    }
                    s.items = l.distinctBy { it.id }   // مفاتيح LazyColumn يجب أن تكون فريدة
                }
                val ch = o.optJSONArray("chips")
                s.chips = if (ch == null) emptyList() else (0 until ch.length()).mapNotNull { i ->
                    ch.optJSONObject(i)?.let { it.optString("x") to it.optBoolean("s") }
                }
                val w = o.optJSONObject("w")
                s.watch = if (w == null) null else YtWatchData(
                    w.optString("id"), w.optString("title"), w.optString("chan"), w.optString("subs"), w.optString("info"), w.optString("likes"),
                    w.optBoolean("liked"), w.optBoolean("subbed"), w.optString("desc"), w.optString("cl"), w.optString("cp")
                )
                val c = o.optJSONArray("c")
                if (c != null) s.comments = (0 until c.length()).mapNotNull { i ->
                    c.optJSONObject(i)?.let { YtComment(it.optString("a"), it.optString("x"), it.optString("t"), it.optString("l")) }
                }
            }
            "diag" -> s.diag = o.optString("x")
        }
    }

    private fun call(tab: BrowserTab, expr: String) {
        tab.webView?.evaluateJavascript("window.__novaYtApp&&window.__novaYtApp.$expr", null)
    }

    fun mode(tab: BrowserTab, on: Boolean) { tab.yt.wantMode = on; call(tab, "mode($on)") }
    fun open(tab: BrowserTab, v: YtVideo) {
        tab.yt.showComments = false
        call(tab, "open(${JSONObject.quote("/watch?v=" + v.id)})")
    }
    fun go(tab: BrowserTab, path: String) { tab.yt.showComments = false; call(tab, "go(${JSONObject.quote(path)})") }
    fun search(tab: BrowserTab, q: String) { tab.yt.showComments = false; call(tab, "search(${JSONObject.quote(q)})") }
    fun more(tab: BrowserTab) {
        val now = System.currentTimeMillis()
        if (now - tab.yt.lastMore < 1200) return
        tab.yt.lastMore = now
        call(tab, "more()")
    }
    fun chip(tab: BrowserTab, i: Int) = call(tab, "chip($i)")
    fun like(tab: BrowserTab) = call(tab, "like()")
    fun subscribe(tab: BrowserTab) = call(tab, "subscribe()")
    fun openComments(tab: BrowserTab) { tab.yt.comments = emptyList(); tab.yt.showComments = true; call(tab, "comments()") }
    fun closeComments(tab: BrowserTab) { tab.yt.showComments = false; call(tab, "closeComments()") }
    fun expand(tab: BrowserTab) = call(tab, "expand()")
    fun diagnose(tab: BrowserTab) = call(tab, "diag()")
}
