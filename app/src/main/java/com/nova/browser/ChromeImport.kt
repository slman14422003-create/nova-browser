package com.nova.browser

import android.content.Context
import android.net.Uri

/** استيراد كلمات المرور من ملف CSV مُصدَّر من Chrome (name,url,username,password). */
object ChromeImport {
    /** يقسم سطر CSV مع دعم علامات الاقتباس والفواصل داخلها. */
    private fun split(line: String): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder(); var q = false; var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && q && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> q = !q
                ch == ',' && !q -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(ch)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    /** يعيد عدد العناصر المستوردة (أو -1 عند فشل قراءة الملف). */
    fun importPasswords(c: Context, uri: Uri): Int = runCatching {
        val lines = c.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readLines() }
        if (lines.isEmpty()) return 0
        val head = split(lines[0]).map { it.trim().lowercase() }
        val iUrl = head.indexOf("url"); val iUser = head.indexOf("username"); val iPass = head.indexOf("password")
        if (iUrl < 0 || iUser < 0 || iPass < 0) return -1
        var n = 0
        for (l in lines.drop(1)) {
            if (l.isBlank()) continue
            val f = split(l)
            val host = Vault.cleanHost(f.getOrElse(iUrl) { "" })
            val user = f.getOrElse(iUser) { "" }; val pass = f.getOrElse(iPass) { "" }
            if (host.isBlank() || pass.isEmpty()) continue
            Vault.upsert("", host, user, pass); n++
        }
        n
    }.getOrDefault(-1)
}
