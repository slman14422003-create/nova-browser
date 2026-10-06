package com.nova.browser

import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

class AiMsg(val user: Boolean, val text: String)
class AiFile(val uri: Uri, val name: String, val size: Long, val mime: String)

/** حالة محادثة واجهة الذكاء الاصطناعي لتبويب واحد. المصدر الوحيد للحقيقة هو DOM الموقع (يصل عبر ai.js). */
class AiSession {
    var msgs by mutableStateOf<List<AiMsg>>(emptyList())
    var busy by mutableStateOf(false)
    var ready by mutableStateOf(false)        // وُجد حقل الكتابة في الصفحة
    var seen by mutableStateOf(false)         // وصلت لقطة واحدة على الأقل من الصفحة الحالية
    var showSite by mutableStateOf(false)     // عرض الموقع الأصلي بدل الواجهة الأصلية
    var draft by mutableStateOf("")
    var optimistic by mutableStateOf<String?>(null)   // رسالة أُرسلت ولم تظهر بعد في DOM
    var diag by mutableStateOf<String?>(null)
    var note by mutableStateOf<String?>(null)         // ملاحظة قصيرة (فشل رفع…)
    val files = mutableStateListOf<AiFile>()
    var pendingUris: List<Uri> = emptyList()          // يقرؤها chooser الـ WebView إن فتح الموقع نافذة اختيار
    val queue = ArrayDeque<AiFile>()
    var pendingText: String? = null
    var current: AiFile? = null
}

/**
 * جسر «الموقع كـ API»: يُبقي صفحة الموقع حيّة خلف الواجهة الأصلية، ويرسل إليها النص والملفات، ويقرأ الردود منها.
 * المحدّدات في assets/ai-sites.json؛ المواقع ذات native=false تبقى بعرضها الأصلي.
 */
object Ai {
    private const val MAX_FILE = 12L * 1024 * 1024
    private const val CHUNK = 400_000   // مضاعف لـ 4 كي لا ينكسر base64

    private val js: String? by lazy { runCatching { Perf.asset("ai.js") }.getOrNull() }
    private val cfg: String by lazy { runCatching { Perf.asset("ai-sites.json") }.getOrDefault("{}") }
    private val nativeHosts: Set<String> by lazy {
        runCatching {
            val o = JSONObject(cfg)
            buildSet { o.keys().forEach { k -> if (o.optJSONObject(k)?.optBoolean("native") == true) add(k) } }
        }.getOrDefault(emptySet())
    }
    private val docStart by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) }
    private val listenerOk by lazy { WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) }

    fun available() = js != null && listenerOk

    /** هل يُعرض هذا النطاق بالواجهة الأصلية؟ */
    fun supports(host: String?): Boolean {
        if (!available() || host.isNullOrBlank()) return false
        val h = host.removePrefix("www.")
        return nativeHosts.any { h == it || h.endsWith(".$it") }
    }

    fun install(wv: WebView, tab: BrowserTab) {
        val base = js ?: return
        if (!Prefs.pwaMode || !Prefs.aiNative || !listenerOk) return
        runCatching {
            WebViewCompat.addWebMessageListener(wv, "NovaAi", Pwa.aiOrigins, WebViewCompat.WebMessageListener { _, message, _, isMain, _ ->
                if (!isMain) return@WebMessageListener
                val data = message.data ?: return@WebMessageListener
                if (data.length > 600_000) return@WebMessageListener
                handle(tab, data)
            })
        }
        if (docStart) runCatching { WebViewCompat.addDocumentStartJavaScript(wv, base.replace("__CFG__", cfg), Pwa.aiOrigins) }
    }

    /** احتياطي للأجهزة بلا حقن مبكر. */
    fun onPageStart(wv: WebView, url: String, tab: BrowserTab) {
        tab.ai.seen = false; tab.ai.ready = false
        if (docStart || !Prefs.aiNative || !Prefs.pwaMode) return
        val base = js ?: return
        val o = runCatching { Uri.parse(url) }.getOrNull() ?: return
        if (o.scheme != "https" || "https://${o.host}" !in Pwa.aiOrigins) return
        wv.evaluateJavascript(base.replace("__CFG__", cfg), null)
    }

    private fun handle(tab: BrowserTab, data: String) {
        val o = runCatching { JSONObject(data) }.getOrNull() ?: return
        val s = tab.ai
        when (o.optString("t")) {
            "snap" -> {
                s.seen = true
                s.ready = o.optBoolean("input")
                s.busy = o.optBoolean("busy")
                val a = o.optJSONArray("msgs") ?: return
                val l = ArrayList<AiMsg>(a.length())
                for (i in 0 until a.length()) { val m = a.optJSONObject(i) ?: continue; l += AiMsg(m.optString("r") == "u", m.optString("x")) }
                // لا نُحدّث الحالة (فلا إعادة تركيب لكل الرسائل) إن لم يتغيّر شيء
                val cur = s.msgs
                if (cur.size != l.size || l.indices.any { cur[it].user != l[it].user || cur[it].text != l[it].text }) s.msgs = l
                val opt = s.optimistic
                if (opt != null && (s.busy || l.any { it.user && it.text.contains(opt.take(30)) })) s.optimistic = null
            }
            "diag" -> s.diag = o.optString("x")
            "up" -> {
                if (!o.optBoolean("ok")) {
                    val f = s.current
                    if (f != null && o.optString("x") == "noinput") {
                        // لا حقل ملف في الصفحة: نحاول عبر نافذة الاختيار التي يفتحها زر الإرفاق في الموقع
                        s.pendingUris = listOf(f.uri)
                        call(tab, "attach()")
                        tab.webView?.postDelayed({ s.pendingUris = emptyList(); next(tab) }, 2500)
                        return
                    }
                    s.note = L("تعذّر رفع الملف إلى الموقع — أرفقه من الموقع نفسه")
                }
                next(tab)
            }
        }
    }

    private fun call(tab: BrowserTab, expr: String) {
        tab.webView?.evaluateJavascript("window.__novaAi&&window.__novaAi.$expr", null)
    }

    fun stop(tab: BrowserTab) = call(tab, "stop()")
    fun newChat(tab: BrowserTab) {
        tab.ai.msgs = emptyList(); tab.ai.optimistic = null; tab.ai.files.clear()
        call(tab, "newChat()")
    }
    fun diagnose(tab: BrowserTab) = call(tab, "diag()")

    /** إرسال رسالة (مع ملفات إن وُجدت: تُرفع واحداً واحداً ثم يُرسل النص). */
    fun send(tab: BrowserTab, text: String) {
        val s = tab.ai
        if (tab.webView == null || (text.isBlank() && s.files.isEmpty())) return
        s.note = null
        s.optimistic = text.ifBlank { s.files.joinToString(" • ") { it.name } }
        if (s.files.isEmpty()) { call(tab, "send(${JSONObject.quote(text)})"); return }
        s.queue.clear(); s.queue.addAll(s.files); s.files.clear(); s.pendingText = text
        next(tab)
    }

    private fun next(tab: BrowserTab) {
        val s = tab.ai
        val wv = tab.webView ?: return
        val f = s.queue.removeFirstOrNull()
        s.current = f
        if (f == null) {
            val t = s.pendingText ?: return
            s.pendingText = null
            wv.postDelayed({ call(tab, "send(${JSONObject.quote(t)})") }, 700)   // مهلة كي تُنهي الصفحة معالجة المرفق
            return
        }
        val ctx = wv.context
        Thread {
            val bytes = runCatching {
                if (f.size > MAX_FILE) null else ctx.contentResolver.openInputStream(f.uri)?.use { it.readBytes() }
            }.getOrNull()
            wv.post {
                if (bytes == null || bytes.size > MAX_FILE) { s.note = L("الملف كبير أو غير قابل للقراءة (الحد 12MB)"); next(tab); return@post }
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val id = f.hashCode()
                var i = 0
                while (i < b64.length) {
                    val e = minOf(i + CHUNK, b64.length)
                    wv.evaluateJavascript("window.__novaAi&&window.__novaAi.fc($id,'${b64.substring(i, e)}')", null)
                    i = e
                }
                wv.evaluateJavascript("window.__novaAi&&window.__novaAi.fe($id,${JSONObject.quote(f.name)},${JSONObject.quote(f.mime)})", null)
            }
        }.start()
    }

    fun describe(ctx: android.content.Context, uri: Uri): AiFile {
        var name = "file"; var size = -1L
        runCatching {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) name = c.getString(ni) ?: name
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        return AiFile(uri, name, size, ctx.contentResolver.getType(uri) ?: "application/octet-stream")
    }
}
