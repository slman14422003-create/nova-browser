package com.nova.browser

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** إعدادات التطبيق (محفوظة، وقابلة للقراءة من أي مكان كحالة Compose) */
object Prefs {
    private var sp: android.content.SharedPreferences? = null
    var engine by mutableIntStateOf(0); private set
    var theme by mutableIntStateOf(0); private set          // 0 نظام، 1 فاتح، 2 داكن
    var desktop by mutableStateOf(false); private set
    var js by mutableStateOf(true); private set
    var restore by mutableStateOf(true); private set
    var maxConns by mutableIntStateOf(0); private set       // 0 = تلقائي

    val engines = listOf(
        "Google" to "https://www.google.com/search?q=",
        "DuckDuckGo" to "https://duckduckgo.com/?q=",
        "Bing" to "https://www.bing.com/search?q=",
        "Brave" to "https://search.brave.com/search?q=",
        "Ecosia" to "https://www.ecosia.org/search?q="
    )

    fun init(c: Context) {
        val p = c.getSharedPreferences("settings", Context.MODE_PRIVATE); sp = p
        engine = p.getInt("engine", 0).coerceIn(0, engines.lastIndex)
        theme = p.getInt("theme", 0); desktop = p.getBoolean("desktop", false)
        js = p.getBoolean("js", true); restore = p.getBoolean("restore", true); maxConns = p.getInt("maxc", 0)
    }
    fun setEngine(v: Int) { engine = v; sp?.edit()?.putInt("engine", v)?.apply() }
    fun setTheme(v: Int) { theme = v; sp?.edit()?.putInt("theme", v)?.apply() }
    fun setDesktop(v: Boolean) { desktop = v; sp?.edit()?.putBoolean("desktop", v)?.apply() }
    fun setJs(v: Boolean) { js = v; sp?.edit()?.putBoolean("js", v)?.apply() }
    fun setRestore(v: Boolean) { restore = v; sp?.edit()?.putBoolean("restore", v)?.apply() }
    fun setMaxConns(v: Int) { maxConns = v; sp?.edit()?.putInt("maxc", v)?.apply() }
}

private class RowSpec(
    val title: String, val sub: String?, val icon: ImageVector, val onClick: () -> Unit,
    val trailing: (@Composable () -> Unit)? = null
)

@Composable
private fun Group(title: String, rows: List<RowSpec>) {
    val cs = MaterialTheme.colorScheme
    Text(title, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 22.dp, bottom = 8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rows.forEachIndexed { i, r ->
            ListRow(groupShape(i, rows.size), r.title, r.sub, r.onClick, trailing = r.trailing) { IconCircle { Icon(r.icon, null) } }
        }
    }
}

@Composable
private fun ChoiceDialog(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, o ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onSelect(i); onDismiss() }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) { RadioButton(selected = i == selected, onClick = null); Spacer(Modifier.width(12.dp)); Text(o) }
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onClearData: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var dialog by remember { mutableStateOf<String?>(null) }
    val themeNames = listOf("تلقائي (حسب النظام)", "فاتح", "داكن")
    val connOpts = listOf(0, 4, 8, 16)
    val connNames = listOf("تلقائي (حتى 16)", "4", "8", "16")

    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundBtn(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                Spacer(Modifier.width(8.dp))
                Text("الإعدادات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Group("عام", listOf(
                    RowSpec("محرك البحث", Prefs.engines[Prefs.engine].first, Icons.Default.Search, { dialog = "engine" }),
                    RowSpec("المظهر", themeNames[Prefs.theme], Icons.Default.Star, { dialog = "theme" }),
                    RowSpec("نسخة سطح المكتب افتراضياً", "للتبويبات الجديدة", Icons.Default.Build, { Prefs.setDesktop(!Prefs.desktop) },
                        { Switch(checked = Prefs.desktop, onCheckedChange = null) })
                ))
                Group("التصفح والخصوصية", listOf(
                    RowSpec("JavaScript", "تعطيله قد يكسر بعض المواقع", Icons.Default.Check, { Prefs.setJs(!Prefs.js) },
                        { Switch(checked = Prefs.js, onCheckedChange = null) }),
                    RowSpec("استعادة التبويبات", "عند فتح التطبيق", Icons.Default.Refresh, { Prefs.setRestore(!Prefs.restore) },
                        { Switch(checked = Prefs.restore, onCheckedChange = null) }),
                    RowSpec("مسح بيانات التصفح", "الكوكيز والذاكرة المؤقتة والسجل", Icons.Default.Delete, { dialog = "clear" })
                ))
                Group("التنزيلات", listOf(
                    RowSpec("الحد الأقصى للاتصالات", connNames[connOpts.indexOf(Prefs.maxConns).coerceAtLeast(0)], Icons.Default.KeyboardArrowDown, { dialog = "conns" }),
                    RowSpec("مكان الحفظ", "Download/Nova", Icons.Default.Info, {})
                ))
                Group("حول", listOf(RowSpec("Nova Browser", "الإصدار 1.0", Icons.Default.Star, {})))
            }
        }
    }

    when (dialog) {
        "engine" -> ChoiceDialog("محرك البحث", Prefs.engines.map { it.first }, Prefs.engine, { Prefs.setEngine(it) }) { dialog = null }
        "theme" -> ChoiceDialog("المظهر", themeNames, Prefs.theme, { Prefs.setTheme(it) }) { dialog = null }
        "conns" -> ChoiceDialog("الحد الأقصى للاتصالات", connNames, connOpts.indexOf(Prefs.maxConns).coerceAtLeast(0), { Prefs.setMaxConns(connOpts[it]) }) { dialog = null }
        "clear" -> AlertDialog(
            onDismissRequest = { dialog = null }, title = { Text("مسح بيانات التصفح؟") },
            text = { Text("سيتم حذف الكوكيز والذاكرة المؤقتة وسجل التبويبات. لن تُحذف التنزيلات.") },
            confirmButton = { TextButton(onClick = { dialog = null; onClearData() }) { Text("مسح") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("إلغاء") } }
        )
    }
}
