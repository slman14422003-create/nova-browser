package com.nova.browser

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** معلومات إصدار جديد على GitHub. */
class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val apkName: String, val size: Long, val sha256Url: String?)

enum class UpdPhase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, INSTALLING, ERROR }

/**
 * تحديث التطبيق من GitHub Releases: فحص تلقائي، تنزيل مع شريط تقدّم، تحقق SHA-256 وحزمة التطبيق، ثم تثبيت عبر PackageInstaller.
 * المستودع يُحقن وقت البناء (BuildConfig.UPDATE_REPO) من متغير GITHUB_REPOSITORY في GitHub Actions.
 */
object Updater {
    var phase by mutableStateOf(UpdPhase.IDLE); private set
    var info by mutableStateOf<UpdateInfo?>(null); private set
    var progress by mutableFloatStateOf(0f); private set
    var error by mutableStateOf(""); private set
    /** يُظهر نافذة «يوجد تحديث» مرة واحدة لكل جلسة (يمكن للمستخدم تجاهلها). */
    var promptVisible by mutableStateOf(false)

    private var apkFile: File? = null
    @Volatile private var busy = false

    fun currentVersion(c: Context): String =
        runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "0" }.getOrDefault("0")

    /** مقارنة رقمية للإصدارات: v1.6.10 > v1.6.9، ويُتجاهل اللاحق بعد "-". */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(s: String) = s.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
            .split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** فحص تلقائي عند التشغيل (مرة كل 12 ساعة) إن كان مفعّلاً. */
    fun autoCheck(c: Context) {
        if (!Prefs.autoUpdate) return
        val sp = c.getSharedPreferences("updater", Context.MODE_PRIVATE)
        val last = sp.getLong("last", 0L)
        if (System.currentTimeMillis() - last < 12L * 3600_000) return
        check(c, silent = true)
    }

    fun check(c: Context, silent: Boolean = false) {
        if (busy || phase == UpdPhase.DOWNLOADING || phase == UpdPhase.INSTALLING) return
        val app = c.applicationContext
        busy = true
        if (!silent) { phase = UpdPhase.CHECKING; error = "" }
        Thread({
            try {
                val repo = BuildConfig.UPDATE_REPO
                if (!repo.contains('/') || repo.startsWith("OWNER/")) throw IllegalStateException(L("لم يُضبط مستودع GitHub للتحديث"))
                val json = JSONObject(httpText("https://api.github.com/repos/$repo/releases/latest"))
                val tag = json.getString("tag_name")
                app.getSharedPreferences("updater", Context.MODE_PRIVATE).edit().putLong("last", System.currentTimeMillis()).apply()
                if (!isNewer(tag, currentVersion(app))) {
                    info = null
                    if (!silent) phase = UpdPhase.UP_TO_DATE else if (phase == UpdPhase.CHECKING) phase = UpdPhase.IDLE
                } else {
                    val assets = json.getJSONArray("assets")
                    var apk: JSONObject? = null; var sums: String? = null
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i); val n = a.getString("name")
                        if (n.endsWith(".apk", true) && apk == null) apk = a
                        if (n.equals("SHA256SUMS.txt", true)) sums = a.getString("browser_download_url")
                    }
                    if (apk == null) throw IllegalStateException(L("لا يحتوي الإصدار على ملف APK"))
                    info = UpdateInfo(tag, json.optString("body"), apk.getString("browser_download_url"), apk.getString("name"), apk.optLong("size"), sums)
                    phase = UpdPhase.AVAILABLE
                    promptVisible = true
                }
            } catch (e: Exception) {
                if (!silent) { error = e.message ?: e.javaClass.simpleName; phase = UpdPhase.ERROR }
                else if (phase == UpdPhase.CHECKING) phase = UpdPhase.IDLE
            } finally { busy = false }
        }, "nova-upd-check").start()
    }

    fun download(c: Context) {
        val i = info ?: return
        if (busy) return
        val app = c.applicationContext
        busy = true; phase = UpdPhase.DOWNLOADING; progress = 0f; error = ""
        Thread({
            try {
                val dir = File(app.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
                val out = File(dir, "update.apk")
                val md = MessageDigest.getInstance("SHA-256")
                val cn = open(i.apkUrl)
                val total = cn.contentLengthLong.takeIf { it > 0 } ?: i.size
                cn.inputStream.use { ins -> out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024); var done = 0L
                    while (true) {
                        val n = ins.read(buf); if (n < 0) break
                        os.write(buf, 0, n); md.update(buf, 0, n); done += n
                        if (total > 0) progress = (done.toFloat() / total).coerceIn(0f, 1f)
                    }
                } }
                val actual = md.digest().joinToString("") { "%02x".format(it) }
                // تحقق من المجموع الاختباري المنشور مع الإصدار
                i.sha256Url?.let { u ->
                    val line = httpText(u).lineSequence().firstOrNull { it.contains(i.apkName) }
                    val expected = line?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.lowercase()
                    if (expected != null && expected != actual) { out.delete(); throw IllegalStateException(L("فشل التحقق من سلامة الملف (SHA-256)")) }
                }
                // يجب أن تكون الحزمة لنفس التطبيق وبرمز إصدار أعلى
                val pm = app.packageManager
                val pi = pm.getPackageArchiveInfo(out.absolutePath, 0) ?: throw IllegalStateException(L("ملف APK غير صالح"))
                if (pi.packageName != app.packageName) { out.delete(); throw IllegalStateException(L("الحزمة لا تخص هذا التطبيق")) }
                val cur = pm.getPackageInfo(app.packageName, 0)
                if (pi.longVersionCode <= cur.longVersionCode) { out.delete(); throw IllegalStateException(L("الإصدار المنزَّل ليس أحدث من المثبّت")) }
                apkFile = out; progress = 1f; phase = UpdPhase.READY
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName; phase = UpdPhase.ERROR
            } finally { busy = false }
        }, "nova-upd-dl").start()
    }

    /** يطلب إذن «تثبيت التطبيقات المجهولة» إن لزم ثم يبدأ جلسة التثبيت. */
    fun install(c: Context) {
        val f = apkFile ?: run { error = L("لا يوجد ملف منزَّل"); phase = UpdPhase.ERROR; return }
        if (!c.packageManager.canRequestPackageInstalls()) {
            runCatching {
                c.startActivity(Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            error = L("فعّل «السماح بالتثبيت من هذا المصدر» ثم اضغط تثبيت مرة أخرى"); phase = UpdPhase.READY
            return
        }
        phase = UpdPhase.INSTALLING
        Thread({
            try {
                val pi = c.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                val id = pi.createSession(params)
                pi.openSession(id).use { s ->
                    f.inputStream().use { ins -> s.openWrite("nova.apk", 0, f.length()).use { os -> ins.copyTo(os); s.fsync(os) } }
                    val cb = Intent(c, UpdateReceiver::class.java).setPackage(c.packageName)
                    val pend = PendingIntent.getBroadcast(c, id, cb, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                    s.commit(pend.intentSender)
                }
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName; phase = UpdPhase.ERROR
            }
        }, "nova-upd-inst").start()
    }

    internal fun onInstallResult(status: Int, msg: String?) {
        if (status == PackageInstaller.STATUS_SUCCESS) { phase = UpdPhase.IDLE; return }
        error = (msg ?: L("تعذّر التثبيت")) + " ($status)"; phase = UpdPhase.ERROR
    }

    fun dismissPrompt() { promptVisible = false }

    // ───────────── شبكة ─────────────
    private fun open(url: String): HttpURLConnection {
        var u = url
        repeat(5) {
            val cn = (URL(u).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000; readTimeout = 30000; instanceFollowRedirects = false
                setRequestProperty("User-Agent", "NovaBrowser-Updater")
                setRequestProperty("Accept", "application/vnd.github+json, */*")
            }
            val code = cn.responseCode
            if (code in 300..399) { u = URL(URL(u), cn.getHeaderField("Location")).toString(); cn.disconnect(); return@repeat }
            if (code == 403 || code == 429) throw IllegalStateException(L("حدّ طلبات GitHub — حاول لاحقاً"))
            if (code == 404) throw IllegalStateException(L("لا توجد إصدارات منشورة بعد"))
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return cn
        }
        throw IllegalStateException(L("تحويلات كثيرة"))
    }

    private fun httpText(url: String): String { val cn = open(url); return cn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }.also { cn.disconnect() } }
}
