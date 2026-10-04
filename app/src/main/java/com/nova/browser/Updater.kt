package com.nova.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** فحص التحديثات من GitHub Releases (بلا مكتبات). المستودع يأتي من BuildConfig.UPDATE_REPO. */
object Updater {
    class Info(val version: String, val page: String, val apk: String?)

    var available by mutableStateOf<Info?>(null); private set
    var checking by mutableStateOf(false); private set

    /** يقارن أرقام الإصدارات رقماً رقماً (1.10.0 أكبر من 1.9.0). يعيد true إن كان remote أحدث. */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-').split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** يُستدعى في الخلفية؛ force=true يتجاهل الفاصل الزمني (12 ساعة). */
    fun check(c: Context, force: Boolean = false, done: (Boolean?) -> Unit = {}) {
        val repo = BuildConfig.UPDATE_REPO
        if (repo.isBlank() || (!force && !Prefs.autoUpdate)) { done(null); return }
        val sp = c.applicationContext.getSharedPreferences("updater", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - sp.getLong("last", 0L) < 12L * 3600_000L) { done(null); return }
        checking = true
        Thread({
            var result: Boolean? = null
            runCatching {
                val cn = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
                cn.connectTimeout = 8000; cn.readTimeout = 8000
                cn.setRequestProperty("Accept", "application/vnd.github+json")
                if (cn.responseCode == 200) {
                    val j = JSONObject(cn.inputStream.bufferedReader().use { it.readText() })
                    val tag = j.optString("tag_name")
                    var apk: String? = null
                    val assets = j.optJSONArray("assets")
                    if (assets != null) for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk", true)) { apk = a.optString("browser_download_url"); break }
                    }
                    val newer = tag.isNotBlank() && isNewer(tag, BuildConfig.VERSION_NAME)
                    available = if (newer) Info(tag.removePrefix("v"), j.optString("html_url"), apk) else null
                    result = newer
                }
                cn.disconnect()
            }
            sp.edit().putLong("last", System.currentTimeMillis()).apply()
            checking = false
            done(result)
        }, "nova-update").apply { priority = Thread.MIN_PRIORITY }.start()
    }

    /** يفتح رابط الـ APK (أو صفحة الإصدار) في المتصفح الخارجي للتنزيل. */
    fun open(c: Context) {
        val i = available ?: return
        runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(i.apk ?: i.page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
