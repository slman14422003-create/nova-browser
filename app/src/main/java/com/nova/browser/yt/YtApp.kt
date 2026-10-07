package com.nova.browser

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

data class YtVideo(val id: String, val title: String, val channel: String, val meta: String, val dur: String, val avatar: String = "") {
    val url get() = "https://www.youtube.com/watch?v=$id"
}
data class YtPlaylist(val id: String, val title: String, val meta: String, val thumb: String)
data class YtPanel(val id: String, val title: String, val items: List<YtVideo>)
class YtSaveOpt(val name: String, val sub: String, val checked: Boolean)
data class YtComment(val author: String, val text: String, val time: String, val likes: String)
data class YtWatchData(
    val id: String, val title: String, val channel: String, val subs: String, val info: String, val likes: String,
    val liked: Boolean, val subscribed: Boolean, val desc: String, val commentsLabel: String, val commentPreview: String,
    val avatar: String = ""
)

/** مسار ترجمة متاح للفيديو: المفتاح (vss_id) والاسم وهل هو مُولَّد تلقائياً. */
class YtCap(val key: String, val name: String, val auto: Boolean)

/**
 * حالة المشغّل كما يعرضها يوتيوب: السرعة والجودة والترجمة (تصل عند فتح قائمة الإعدادات).
 * ccReady=false يعني أن لغات الترجمة ما زالت تُحمَّل (تظهر حلقة انتظار بدل قسم فارغ).
 */
class YtPlayerInfo(
    val rate: Double, val loop: Boolean, val qualities: List<String>, val quality: String,
    val captions: List<YtCap>, val caption: String,
    val translations: List<Pair<String, String>> = emptyList(), val translateTo: String = "",
    val ccReady: Boolean = false, val ccSize: String = "m", val ccBg: String = "glass", val ccPos: String = "b", val ccOff: Int = 0
)

/** حالة واجهة يوتيوب الأصلية لتبويب واحد. المصدر هو DOM الصفحة (يصل عبر yt-all.js). */
class YtSession {
    var key by mutableStateOf("")                       // مفتاح الصفحة التي جاءت منها آخر لقطة
    var items by mutableStateOf<List<YtVideo>>(emptyList())
    var chips by mutableStateOf<List<Pair<String, Boolean>>>(emptyList())
    var watch by mutableStateOf<YtWatchData?>(null)
    var comments by mutableStateOf<List<YtComment>>(emptyList())
    var showComments by mutableStateOf(false)
    var showSite by mutableStateOf(false)               // عرض الموقع الأصلي بدل الواجهة الأصلية
    var searching by mutableStateOf(false)
    var nudge by mutableStateOf(false)                  // الصفحة الرئيسية فارغة (يوتيوب يطلب البحث لأن لا سجل مشاهدة)
    var query by mutableStateOf("")
    var diag by mutableStateOf<String?>(null)
    var psOpen by mutableStateOf(false)                 // قائمة إعدادات المشغّل الأصلية
    var ps by mutableStateOf<YtPlayerInfo?>(null)
    val recent = mutableStateListOf<String>()
    var wantMode = true                                 // هل يجب تفعيل وضع المشغّل في الصفحة (تضبطه الواجهة)
    var lastMore = 0L
    var playlists by mutableStateOf<List<YtPlaylist>>(emptyList())   // قوائم المستخدم (صفحة المكتبة)
    var plTitle by mutableStateOf("")                   // عنوان صفحة قائمة التشغيل
    var plPanel by mutableStateOf<YtPanel?>(null)       // قائمة التشغيل الجارية في صفحة المشاهدة
    var saveOpen by mutableStateOf(false)               // قائمة «حفظ في قائمة تشغيل»
    var saveOpts by mutableStateOf<List<YtSaveOpt>?>(null)
    var saveFail by mutableStateOf(false)
    var speed by mutableStateOf(1.0)                    // آخر سرعة تشغيل ضُبطت من زر السرعة السريع
    var sleepMin by mutableStateOf(0)                   // مؤقت النوم بالدقائق (0 = متوقف)
    /** آخر قوائم الصفحات التي زارها المستخدم (الرئيسية/البحث/…): تُعرض فوراً عند العودة بدل شاشة تحميل فارغة. */
    val pageCache = object : LinkedHashMap<String, Pair<List<YtVideo>, List<Pair<String, Boolean>>>>() {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Pair<List<YtVideo>, List<Pair<String, Boolean>>>>?) = size > 8
    }
}

/**
 * جسر «يوتيوب كمصدر بيانات»: الصفحة تبقى حيّة خلف الواجهة الأصلية وتوفّر القوائم والبيانات،
 * ومشغّلها الحقيقي يظهر في أعلى شاشة المشاهدة. لا نستخرج روابط البث ولا نعدّل الإعلانات.
 */
object YtApp {
    private val origins = setOf("https://m.youtube.com", "https://www.youtube.com", "https://youtube.com")
    private val listenerOk by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) }

    fun available() = YtHub.ready() && listenerOk

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
            p.startsWith("/feed/history") -> "history"
            p.startsWith("/feed/library") || p.startsWith("/feed/you") -> "library"
            p == "/playlist" -> "playlist"
            p.startsWith("/@") || p.startsWith("/channel/") || p.startsWith("/c/") || p.startsWith("/user/") -> "channel"
            else -> "other"
        }
    }

    /** صفحات تعرضها الواجهة الأصلية؛ غيرها (Shorts وغيرها) يبقى بعرض الموقع. */
    fun nativePage(url: String?) = pageOf(url) in setOf("home", "search", "watch", "subs", "library", "history", "channel", "playlist")

    /** نفس صيغة المفتاح في yt-all.js: المسار + معرّف الفيديو أو كلمة البحث. */
    fun urlKey(url: String?): String {
        val u = runCatching { Uri.parse(url ?: "") }.getOrNull() ?: return ""
        val p = (u.path ?: "").ifEmpty { "/" }
        return p + "?" + (u.getQueryParameter("v") ?: u.getQueryParameter("search_query") ?: "")
    }

    /** يسجّل مستمع الرسائل فقط؛ السكربت نفسه (yt-all.js) يحقنه YtHub مرة واحدة. */
    fun listen(wv: WebView, tab: BrowserTab) {
        if (!Prefs.pwaMode || !Prefs.ytNative || !listenerOk) return
        runCatching {
            WebViewCompat.addWebMessageListener(wv, "NovaYtApp", origins, WebViewCompat.WebMessageListener { view, message, _, isMain, _ ->
                if (!isMain) return@WebMessageListener
                if (YtMini.owns(view)) return@WebMessageListener   // صفحة المشغّل المصغّر لها واجهتها الخاصة ولا تغيّر حالة التبويب
                val data = message.data ?: return@WebMessageListener
                if (data.length > 800_000) return@WebMessageListener
                handle(tab, data)
            })
        }
    }

    /** تحليل اللقطات (قد تصل لمئات الكيلوبايت) خارج الخيط الرئيسي؛ الترتيب محفوظ لأن الخيط واحد. */
    private val pool = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "nova-yt-parse").apply { isDaemon = true } }

    private class Snap(
        val on: Boolean, val key: String, val nudge: Boolean, val items: List<YtVideo>?, val chips: List<Pair<String, Boolean>>,
        val watch: YtWatchData?, val playlists: List<YtPlaylist>, val plTitle: String, val plPanel: YtPanel?, val comments: List<YtComment>?
    )

    private fun video(v: JSONObject) = YtVideo(v.optString("id"), v.optString("t"), v.optString("c"), v.optString("m"), v.optString("d"), v.optString("a"))

    private fun parseSnap(o: JSONObject): Snap {
        val a = o.optJSONArray("items")
        val items = if (a == null) null else {
            val l = ArrayList<YtVideo>(a.length())
            for (i in 0 until a.length()) { val v = a.optJSONObject(i) ?: continue; l += video(v) }
            l.distinctBy { it.id }   // مفاتيح LazyColumn يجب أن تكون فريدة
        }
        val ch = o.optJSONArray("chips")
        val chips = if (ch == null) emptyList() else (0 until ch.length()).mapNotNull { i -> ch.optJSONObject(i)?.let { it.optString("x") to it.optBoolean("s") } }.distinctBy { it.first }
        val w = o.optJSONObject("w")
        val watch = if (w == null) null else YtWatchData(
            w.optString("id"), w.optString("title"), w.optString("chan"), w.optString("subs"), w.optString("info"), w.optString("likes"),
            w.optBoolean("liked"), w.optBoolean("subbed"), w.optString("desc"), w.optString("cl"), w.optString("cp"), w.optString("oa")
        )
        val pls = o.optJSONArray("pls")
        val playlists = if (pls == null) emptyList() else (0 until pls.length()).mapNotNull { i ->
            pls.optJSONObject(i)?.let { YtPlaylist(it.optString("id"), it.optString("t"), it.optString("m"), it.optString("th")) }
        }.distinctBy { it.id }
        val pp = o.optJSONObject("pp")
        val panel = if (pp == null) null else {
            val ia = pp.optJSONArray("items")
            YtPanel(pp.optString("id"), pp.optString("t"), if (ia == null) emptyList() else (0 until ia.length()).mapNotNull { i -> ia.optJSONObject(i)?.let { video(it) } }.distinctBy { it.id })
        }
        val c = o.optJSONArray("c")
        val comments = if (c == null) null else (0 until c.length()).mapNotNull { i ->
            c.optJSONObject(i)?.let { YtComment(it.optString("a"), it.optString("x"), it.optString("t"), it.optString("l")) }
        }
        return Snap(o.optBoolean("on"), o.optString("key"), o.optBoolean("nu"), items, chips, watch, playlists, o.optString("pt"), panel, comments)
    }

    /** يُنفَّذ على الخيط الرئيسي؛ الأصناف data فالقيمة المتطابقة لا تعيد تركيب القوائم (mutableStateOf يقارن بنيوياً). */
    private fun applySnap(tab: BrowserTab, n: Snap) {
        val s = tab.yt
        if (n.on != s.wantMode) call(tab, "mode(${s.wantMode})")   // مزامنة وضع المشغّل (دوران الشاشة/عرض الموقع)
        s.key = n.key
        s.nudge = n.nudge
        if (n.items != null) s.items = n.items
        s.chips = n.chips
        if (!s.key.startsWith("/watch") && s.items.isNotEmpty()) s.pageCache[s.key] = s.items to s.chips
        s.watch = n.watch
        s.playlists = n.playlists
        s.plTitle = n.plTitle
        s.plPanel = n.plPanel
        if (n.comments != null) s.comments = n.comments
    }

    private fun handle(tab: BrowserTab, data: String) {
        pool.execute {
            val o = runCatching { JSONObject(data) }.getOrNull() ?: return@execute
            if (o.optString("t") == "ys") { val n = parseSnap(o); ui.post { applySnap(tab, n) } }
            else ui.post { handleMsg(tab, o) }
        }
    }

    private fun handleMsg(tab: BrowserTab, o: JSONObject) {
        val s = tab.yt
        when (o.optString("t")) {
            "save" -> {
                if (!s.saveOpen) return
                val a = o.optJSONArray("o")
                s.saveFail = o.optBoolean("fail")
                s.saveOpts = if (!o.optBoolean("ok") && !s.saveFail) null else (0 until (a?.length() ?: 0)).mapNotNull { i ->
                    a?.optJSONObject(i)?.let { YtSaveOpt(it.optString("n"), it.optString("p"), it.optBoolean("c")) }
                }
            }
            "diag" -> s.diag = o.optString("x")
            "gear" -> { if (!s.psOpen) { s.ps = null; s.psOpen = true }; call(tab, "ps()") }
            "ps" -> {
                val q = o.optJSONArray("q"); val c = o.optJSONArray("caps"); val tr = o.optJSONArray("trs")
                s.ps = YtPlayerInfo(
                    rate = o.optDouble("rate", 1.0).takeIf { it > 0 } ?: 1.0, loop = o.optBoolean("loop"),
                    qualities = if (q == null) emptyList() else (0 until q.length()).map { q.optString(it) }.filter { it.isNotBlank() },
                    quality = o.optString("cq"),
                    captions = if (c == null) emptyList() else (0 until c.length()).mapNotNull { i ->
                        c.optJSONObject(i)?.let { YtCap(it.optString("c"), it.optString("n").ifBlank { it.optString("c") }, it.optInt("a") == 1) }
                    }.filter { it.key.isNotBlank() },
                    caption = o.optString("cc"),
                    translations = if (tr == null) emptyList() else (0 until tr.length()).mapNotNull { i ->
                        tr.optJSONObject(i)?.let { it.optString("c") to it.optString("n").ifBlank { it.optString("c") } }
                    }.filter { it.first.isNotBlank() },
                    translateTo = o.optString("tl"),
                    ccReady = o.optBoolean("cr"),
                    ccSize = o.optString("cs").ifBlank { "m" },
                    ccBg = o.optString("cb").ifBlank { "glass" },
                    ccPos = o.optString("cp").ifBlank { "b" },
                    ccOff = o.optInt("co", 0).coerceIn(0, 40)
                )
            }
        }
    }

    private fun call(tab: BrowserTab, expr: String) {
        tab.webView?.evaluateJavascript("window.__novaYtApp&&window.__novaYtApp.$expr", null)
    }

    fun mode(tab: BrowserTab, on: Boolean) { tab.yt.wantMode = on; call(tab, "mode($on)") }
    fun open(tab: BrowserTab, v: YtVideo) {
        YtMini.close()   // فيديو جديد: المشغّل المصغّر السابق يُغلق كي لا يتداخل صوتان
        tab.yt.showComments = false
        call(tab, "open(${JSONObject.quote("/watch?v=" + v.id)})")
    }
    /** تشغيل فيديو ضمن قائمة تشغيل (يبقى التشغيل يتابع عناصر القائمة). */
    fun openInList(tab: BrowserTab, id: String, list: String) {
        YtMini.close()
        tab.yt.showComments = false
        call(tab, "open(${JSONObject.quote("/watch?v=$id&list=$list")})")
    }
    fun saveOpen(tab: BrowserTab) { tab.yt.saveOpts = null; tab.yt.saveFail = false; tab.yt.saveOpen = true; call(tab, "saveOpen()") }
    fun saveToggle(tab: BrowserTab, i: Int) = call(tab, "saveToggle($i)")
    fun saveNew(tab: BrowserTab, name: String) { tab.yt.saveOpts = null; call(tab, "saveNew(${JSONObject.quote(name)})") }
    fun saveClose(tab: BrowserTab) { tab.yt.saveOpen = false; call(tab, "saveClose()") }
    fun go(tab: BrowserTab, path: String) { tab.yt.showComments = false; call(tab, "go(${JSONObject.quote(path)})") }
    fun search(tab: BrowserTab, q: String) { tab.yt.showComments = false; call(tab, "search(${JSONObject.quote(q)})") }
    fun more(tab: BrowserTab) {
        val now = System.currentTimeMillis()
        if (now - tab.yt.lastMore < 1200) return
        tab.yt.lastMore = now
        call(tab, "more()")
    }
    /** سحب للتحديث: نعيد تحميل الصفحة نفسها (تُحدَّث القائمة عند وصول لقطة جديدة). */
    fun refresh(tab: BrowserTab) { tab.webView?.reload() }
    fun chip(tab: BrowserTab, i: Int) = call(tab, "chip($i)")
    fun like(tab: BrowserTab) = call(tab, "like()")
    fun dislike(tab: BrowserTab) = call(tab, "dislike()")
    fun subscribe(tab: BrowserTab) = call(tab, "subscribe()")
    fun openComments(tab: BrowserTab) { tab.yt.comments = emptyList(); tab.yt.showComments = true; call(tab, "comments()") }
    fun closeComments(tab: BrowserTab) { tab.yt.showComments = false; call(tab, "closeComments()") }
    fun expand(tab: BrowserTab) = call(tab, "expand()")
    fun diagnose(tab: BrowserTab) = call(tab, "diag()")

    // ───────── إعدادات المشغّل (جودة/سرعة/ترجمة/تكرار) ─────────
    fun openSettings(tab: BrowserTab) { tab.yt.ps = null; tab.yt.psOpen = true; call(tab, "ps()") }
    fun closeSettings(tab: BrowserTab) { tab.yt.psOpen = false }
    fun setRate(tab: BrowserTab, r: Double) = call(tab, "rate($r)")
    fun setQuality(tab: BrowserTab, q: String) = call(tab, "quality(${JSONObject.quote(q)})")
    /** key: مفتاح مسار الترجمة (فارغ = إيقاف). translateTo: لغة الترجمة التلقائية (فارغ = بلا ترجمة). */
    fun setCaption(tab: BrowserTab, key: String, translateTo: String = "") =
        call(tab, "caption(${JSONObject.quote(key)},${JSONObject.quote(translateTo)})")
    /** شكل الترجمة: الحجم (s/m/l/xl) والخلفية (glass/solid/none). */
    fun setCcStyle(tab: BrowserTab, size: String, bg: String, pos: String = "", off: Int = -1, quiet: Boolean = false) =
        call(tab, "ccStyle(${JSONObject.quote(size)},${JSONObject.quote(bg)},${JSONObject.quote(pos)},${if (off < 0) "null" else off.toString()},$quiet)")
    fun setLoop(tab: BrowserTab, on: Boolean) = call(tab, "loop($on)")

    // ───────── ميزات إضافية في صفحة المشاهدة ─────────
    private val speedSteps = doubleArrayOf(0.75, 1.0, 1.25, 1.5, 1.75, 2.0)
    private var sleepRun: Runnable? = null
    private val ui = Handler(Looper.getMainLooper())
    private const val PAUSE_JS = "(function(){if(window.__novaYtCtl){window.__novaYtCtl('pause')}else{var v=document.querySelector('video');if(v)v.pause()}})()"

    /** زر السرعة السريع: يقرأ السرعة الحالية من الفيديو ثم ينتقل للتي بعدها (0.75 ← 1 ← 1.25 … 2 ثم يعود). */
    fun cycleSpeed(tab: BrowserTab) {
        val w = tab.webView ?: return
        w.evaluateJavascript("(function(){var v=document.querySelector('video');return v?v.playbackRate:1})()") { r ->
            val cur = r?.trim('"')?.toDoubleOrNull() ?: tab.yt.speed
            val i = speedSteps.indexOfFirst { kotlin.math.abs(it - cur) < 0.01 }
            val next = speedSteps[(i + 1).mod(speedSteps.size)]
            tab.yt.speed = next
            setRate(tab, next)
        }
    }

    /** مؤقت النوم: يدور بين إيقاف ← 15 ← 30 ← 60 دقيقة، وعند انتهائه يوقف الفيديو (في التبويب أو المشغّل المصغّر). */
    fun cycleSleep(tab: BrowserTab) {
        val steps = intArrayOf(0, 15, 30, 60)
        val s = tab.yt
        val next = steps[(steps.indexOf(s.sleepMin) + 1).mod(steps.size)]
        s.sleepMin = next
        sleepRun?.let { ui.removeCallbacks(it) }; sleepRun = null
        if (next > 0) {
            val r = Runnable {
                s.sleepMin = 0; sleepRun = null
                tab.webView?.evaluateJavascript(PAUSE_JS, null)
                YtMini.wv?.evaluateJavascript(PAUSE_JS, null)
            }
            sleepRun = r
            ui.postDelayed(r, next * 60_000L)
        }
    }

    /** الثانية الحالية من الفيديو (لمشاركة رابط يبدأ من هذه اللحظة). */
    fun currentSec(tab: BrowserTab, done: (Int) -> Unit) {
        val w = tab.webView ?: return done(0)
        w.evaluateJavascript("(function(){var v=document.querySelector('video');return v?Math.floor(v.currentTime):0})()") { r ->
            done(r?.trim('"')?.toIntOrNull() ?: 0)
        }
    }

    // ───────── المشغّل المصغّر ─────────
    /**
     * يصغّر الفيديو الجاري: صفحة المشاهدة (بمشغّلها الحيّ) تنتقل إلى نافذة صغيرة عائمة تواصل التشغيل،
     * ويفتح التبويب صفحة جديدة (الصفحة السابقة في السجل أو الرئيسية) للتنقل بحرية في يوتيوب.
     */
    fun minimize(tab: BrowserTab): Boolean {
        val w = tab.webView ?: return false
        if (pageOf(tab.url) != "watch") return false
        val wasPlaying = tab.ytPlaying
        val title = tab.yt.watch?.title?.ifBlank { null } ?: tab.title
        val back = runCatching {
            val l = w.copyBackForwardList()
            if (l.currentIndex > 0) l.getItemAtIndex(l.currentIndex - 1)?.url else null
        }.getOrNull()
        val next = back?.takeIf { supports(it) && pageOf(it) != "watch" && pageOf(it) != "shorts" } ?: "https://m.youtube.com/"
        YtMini.start(w, title, wasPlaying)
        tab.webView = null; tab.saved = null
        tab.url = next; tab.canBack = false; tab.canForward = false; tab.loading = true
        val s = tab.yt
        // القائمة التي كان المستخدم يتصفحها قبل فتح الفيديو تظهر فوراً (والصفحة الحيّة تحدّثها حين تجهز) بدل شاشة فارغة
        val cached = s.pageCache[urlKey(next)]
        s.items = cached?.first ?: emptyList(); s.chips = cached?.second ?: emptyList(); s.key = if (cached != null) urlKey(next) else ""
        s.watch = null; s.comments = emptyList()
        s.showComments = false; s.psOpen = false; s.ps = null; s.searching = false
        tab.epoch++
        return true
    }

    private const val RESUME_JS = "(function(){var v=document.querySelector('video');if(v&&v.paused&&!v.ended){var p=v.play();if(p&&p.catch)p.catch(function(){})}})()"

    /** ينقل الـ WebView الحيّ من المشغّل المصغّر إلى التبويب مكان صفحة القوائم (التي تُحرَّر). يعيد false إن تعذّر فيُستعمل الطريق القديم. */
    private fun handBack(tab: BrowserTab, w: WebView, url: String): Boolean = runCatching {
        val was = YtMini.playing
        YtMini.handOver() ?: return@runCatching false
        (w.parent as? android.view.ViewGroup)?.removeView(w)
        val old = tab.webView
        tab.webView = w; tab.saved = null
        tab.url = url; tab.canBack = w.canGoBack(); tab.canForward = w.canGoForward(); tab.loading = false
        val s = tab.yt
        s.showComments = false; s.showSite = false; s.psOpen = false; s.ps = null; s.searching = false
        s.watch = null; s.comments = emptyList(); s.items = emptyList(); s.key = ""
        w.evaluateJavascript("window.__novaMini&&window.__novaMini(false);window.__novaYtApp&&window.__novaYtApp.mini(false)", null)
        w.onResume(); w.resumeTimers()
        tab.epoch++
        // إعادة ربط الـ WebView بالواجهة قد توقف الفيديو لحظة: نستأنفه إن كان يعمل
        if (was) for (d in longArrayOf(350L, 1100L)) ui.postDelayed({ if (tab.webView === w) w.evaluateJavascript(RESUME_JS, null) }, d)
        // صفحة القوائم القديمة تُحرَّر بعد أن تنتهي الواجهة من فصلها
        if (old != null && old !== w) ui.postDelayed({ runCatching { old.stopLoading(); (old.parent as? android.view.ViewGroup)?.removeView(old); old.destroy() } }, 500L)
        true
    }.getOrDefault(false)

    /** يوسّع المشغّل المصغّر: يفتح الفيديو في التبويب الحالي من الموضع نفسه ويغلق النافذة العائمة. */
    fun expandMini(tab: BrowserTab) {
        val w = YtMini.wv ?: return
        // التكبير بلا إعادة تحميل: نفس صفحة المشغّل المصغّر (بكل ما حُمّل ووقت التشغيل) تعود إلى التبويب
        val cur = w.url
        if (cur != null && pageOf(cur) == "watch" && handBack(tab, w, cur)) return
        val id = Regex("[?&]v=([\\w-]{11})").find(w.url ?: "")?.groupValues?.get(1)
        w.evaluateJavascript("(function(){var v=document.querySelector('video');return v?Math.floor(v.currentTime):0})()") { r ->
            val sec = r?.trim('"')?.toIntOrNull() ?: 0
            YtMini.close()
            if (id != null) {
                tab.yt.showComments = false; tab.yt.showSite = false
                val path = "/watch?v=$id" + if (sec > 3) "&t=${sec}s" else ""
                val u = "https://m.youtube.com$path"
                if (supports(tab.url) && tab.webView != null) call(tab, "go(${JSONObject.quote(path)})")
                else { tab.url = u; tab.webView?.loadUrl(u, Perf.privacyHeaders) }
            }
        }
    }
}
