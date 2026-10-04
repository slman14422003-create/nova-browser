package com.nova.browser

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

class ImportResult(var bookmarks: Int = 0, var history: Int = 0, var passwords: Int = 0, var skipped: Int = 0, val notes: MutableList<String> = mutableListOf())

/**
 * استيراد بيانات كروم من الملفات التي يصدّرها كروم نفسه (أندرويد لا يسمح لتطبيق بقراءة مجلد كروم الخاص):
 *  • الإشارات المرجعية: Bookmarks.html (تصدير كروم) أو ملف Bookmarks (JSON) من ملف تعريف سطح المكتب
 *  • كلمات المرور: ملف CSV من «تصدير كلمات المرور» في كروم
 *  • السجل: History.json من Google Takeout أو ملف History (SQLite) من ملف تعريف سطح المكتب
 *  • أرشيف Takeout (.zip) كاملاً: يُفرز تلقائياً حسب أسماء الملفات
 */
object ChromeImport {
    private const val MAX_BYTES = 200L * 1024 * 1024

    fun run(c: Context, uris: List<Uri>): ImportResult {
        val r = ImportResult()
        val bms = ArrayList<Bookmark>(); val hs = ArrayList<HistoryItem>(); val pws = ArrayList<Triple<String, String, String>>()
        for (u in uris) {
            runCatching {
                val name = displayName(c, u)
                val bytes = c.contentResolver.openInputStream(u)?.use { readLimited(it) } ?: return@runCatching
                route(c, name, bytes, bms, hs, pws, r)
            }.onFailure { r.notes += "${displayName(c, u)}: ${it.message ?: it.javaClass.simpleName}" }
        }
        if (pws.isNotEmpty()) r.passwords = Vault.addAll(pws)
        r.bookmarks = Library.addBookmarks(bms)
        r.history = Library.addHistory(hs)
        return r
    }

    private fun route(c: Context, name: String, b: ByteArray, bms: MutableList<Bookmark>, hs: MutableList<HistoryItem>,
                      pws: MutableList<Triple<String, String, String>>, r: ImportResult) {
        val low = name.lowercase()
        when {
            low.endsWith(".zip") || (b.size > 4 && b[0] == 0x50.toByte() && b[1] == 0x4B.toByte()) -> {
                ZipInputStream(ByteArrayInputStream(b)).use { z ->
                    while (true) {
                        val e = z.nextEntry ?: break
                        if (e.isDirectory) continue
                        val data = readLimited(z)
                        route(c, e.name.substringAfterLast('/'), data, bms, hs, pws, r)
                    }
                }
            }
            b.size > 16 && String(b, 0, 15, Charsets.ISO_8859_1) == "SQLite format 3" -> importSqlite(c, b, hs, r)
            low.endsWith(".html") || low.endsWith(".htm") -> parseBookmarksHtml(String(b, Charsets.UTF_8), bms)
            low.endsWith(".csv") -> parsePasswordsCsv(String(b, Charsets.UTF_8), pws, r)
            low.endsWith(".json") || low == "bookmarks" -> parseJson(String(b, Charsets.UTF_8), bms, hs, r)
            else -> {
                val t = String(b, 0, minOf(b.size, 4096), Charsets.UTF_8).trimStart()
                when {
                    t.startsWith("<!DOCTYPE NETSCAPE", true) || t.contains("<DT>", true) -> parseBookmarksHtml(String(b, Charsets.UTF_8), bms)
                    t.startsWith("{") -> parseJson(String(b, Charsets.UTF_8), bms, hs, r)
                    t.startsWith("name,url,", true) || t.startsWith("url,", true) -> parsePasswordsCsv(String(b, Charsets.UTF_8), pws, r)
                    else -> r.skipped++
                }
            }
        }
    }

    // ───────────── الإشارات المرجعية (Netscape HTML) ─────────────
    private val htmlTok = Regex("(?is)<h3[^>]*>(.*?)</h3>|<a\\s[^>]*?href=\"([^\"]*)\"[^>]*?>(.*?)</a>|<dl[^>]*>|</dl>")
    private val addDate = Regex("(?i)add_date=\"(\\d+)\"")

    fun parseBookmarksHtml(html: String, out: MutableList<Bookmark>) {
        val stack = ArrayList<String>(); var pending: String? = null
        for (m in htmlTok.findAll(html)) {
            val t = m.value.lowercase()
            when {
                t.startsWith("<h3") -> pending = unescape(m.groupValues[1])
                t.startsWith("<dl") -> { stack.add(pending ?: ""); pending = null }
                t.startsWith("</dl") -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                else -> {
                    val url = unescape(m.groupValues[2])
                    if (!url.startsWith("http")) continue
                    val ts = addDate.find(m.value)?.groupValues?.get(1)?.toLongOrNull()?.let { it * 1000 } ?: 0L
                    out.add(Bookmark(url, unescape(m.groupValues[3]).ifBlank { url }, stack.filter { it.isNotBlank() }.joinToString(" / "), ts))
                }
            }
        }
    }

    private fun unescape(s: String) = s.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'").trim()

    // ───────────── كلمات المرور (CSV) ─────────────
    fun parsePasswordsCsv(text: String, out: MutableList<Triple<String, String, String>>, r: ImportResult) {
        val rows = csv(text.removePrefix("\uFEFF"))
        if (rows.isEmpty()) return
        val head = rows[0].map { it.trim().lowercase() }
        val iUrl = head.indexOf("url").takeIf { it >= 0 } ?: head.indexOf("origin").takeIf { it >= 0 } ?: head.indexOf("login_uri").takeIf { it >= 0 } ?: return
        val iName = head.indexOf("name")
        val iUser = head.indexOf("username").takeIf { it >= 0 } ?: head.indexOf("login_username").takeIf { it >= 0 } ?: return
        val iPass = head.indexOf("password").takeIf { it >= 0 } ?: head.indexOf("login_password").takeIf { it >= 0 } ?: return
        for (row in rows.drop(1)) {
            val pass = row.getOrNull(iPass).orEmpty()
            if (pass.isEmpty()) { r.skipped++; continue }
            val url = row.getOrNull(iUrl).orEmpty().trim()
            if (url.startsWith("android://")) { r.skipped++; continue }       // كلمات مرور تطبيقات أندرويد
            var host = runCatching { Uri.parse(url).host }.getOrNull()
            if (host.isNullOrBlank()) host = row.getOrNull(iName)?.trim()
            if (host.isNullOrBlank()) { r.skipped++; continue }
            out.add(Triple(Vault.cleanHost(host), row.getOrNull(iUser).orEmpty(), pass))
        }
    }

    /** محلل CSV وفق RFC 4180 (يدعم الاقتباس والأسطر داخل الحقول). */
    private fun csv(s: String): List<List<String>> {
        val rows = ArrayList<List<String>>(); var row = ArrayList<String>(); val f = StringBuilder()
        var q = false; var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (q) {
                if (ch == '"') { if (i + 1 < s.length && s[i + 1] == '"') { f.append('"'); i++ } else q = false } else f.append(ch)
            } else when (ch) {
                '"' -> q = true
                ',' -> { row.add(f.toString()); f.setLength(0) }
                '\n' -> { row.add(f.toString()); f.setLength(0); rows.add(row); row = ArrayList() }
                '\r' -> {}
                else -> f.append(ch)
            }
            i++
        }
        if (f.isNotEmpty() || row.isNotEmpty()) { row.add(f.toString()); rows.add(row) }
        return rows.filter { it.any { x -> x.isNotEmpty() } }
    }

    // ───────────── JSON: Takeout History / Bookmarks (ملف تعريف سطح المكتب) ─────────────
    private fun parseJson(text: String, bms: MutableList<Bookmark>, hs: MutableList<HistoryItem>, r: ImportResult) {
        val o = JSONObject(text)
        o.optJSONArray("Browser History")?.let { a ->
            for (i in 0 until a.length()) {
                val e = a.optJSONObject(i) ?: continue
                val url = e.optString("url"); if (!url.startsWith("http")) continue
                hs.add(HistoryItem(url, e.optString("title").ifBlank { url }, e.optLong("time_usec") / 1000))
            }
            return
        }
        o.optJSONObject("roots")?.let { roots ->
            for (k in roots.keys()) roots.optJSONObject(k)?.let { walkBookmarkNode(it, "", bms, true) }
            return
        }
        r.skipped++
    }

    private fun walkBookmarkNode(n: JSONObject, path: String, out: MutableList<Bookmark>, root: Boolean) {
        val name = n.optString("name")
        if (n.optString("type") == "url") {
            val u = n.optString("url")
            if (u.startsWith("http")) out.add(Bookmark(u, name.ifBlank { u }, path, (n.optString("date_added").toLongOrNull() ?: 0L).let { if (it > 11644473600000000L) it / 1000 - 11644473600000L else 0L }))
            return
        }
        val here = if (root) path else if (path.isEmpty()) name else "$path / $name"
        val ch: JSONArray = n.optJSONArray("children") ?: return
        for (i in 0 until ch.length()) ch.optJSONObject(i)?.let { walkBookmarkNode(it, here, out, false) }
    }

    // ───────────── السجل من قاعدة بيانات كروم (SQLite) ─────────────
    private fun importSqlite(c: Context, bytes: ByteArray, hs: MutableList<HistoryItem>, r: ImportResult) {
        val f = File(c.cacheDir, "chrome_import.db")
        try {
            f.writeBytes(bytes)
            val db = android.database.sqlite.SQLiteDatabase.openDatabase(f.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
            db.use {
                val hasUrls = it.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='urls'", null).use { cu -> cu.moveToFirst() }
                if (!hasUrls) { r.notes += L("قاعدة بيانات لا تحتوي سجلاً (كلمات مرور سطح المكتب مشفّرة بمفتاح الجهاز ولا يمكن قراءتها؛ استخدم تصدير CSV)"); return }
                it.rawQuery("SELECT url, title, last_visit_time FROM urls WHERE hidden=0 ORDER BY last_visit_time DESC LIMIT 20000", null).use { cu ->
                    while (cu.moveToNext()) {
                        val u = cu.getString(0) ?: continue
                        if (!u.startsWith("http")) continue
                        hs.add(HistoryItem(u, cu.getString(1).orEmpty().ifBlank { u }, cu.getLong(2) / 1000 - 11644473600000L))
                    }
                }
            }
        } finally { f.delete() }
    }

    private fun readLimited(ins: java.io.InputStream): ByteArray {
        val bo = java.io.ByteArrayOutputStream(); val buf = ByteArray(64 * 1024); var tot = 0L
        while (true) { val n = ins.read(buf); if (n < 0) break; tot += n; if (tot > MAX_BYTES) throw IllegalStateException(L("الملف كبير جداً")); bo.write(buf, 0, n) }
        return bo.toByteArray()
    }

    private fun displayName(c: Context, u: Uri): String =
        runCatching {
            c.contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: (u.lastPathSegment ?: "file")
}
