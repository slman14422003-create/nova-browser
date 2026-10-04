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
    var autoClean by mutableStateOf(true); private set      // تنظيف المؤقت عند كل تشغيل
    var blockAds by mutableStateOf(true); private set       // حجب الإعلانات والمتتبعات
    var dataSaver by mutableStateOf(false); private set     // عدم تحميل الصور
    var lazyMedia by mutableStateOf(true); private set      // تحميل كسول للصور
    var httpsFirst by mutableStateOf(true); private set     // ترقية http إلى https
    var cleanUrls by mutableStateOf(true); private set      // إزالة معرّفات التتبع من الروابط
    var blockThirdCookies by mutableStateOf(true); private set
    var secureScreen by mutableStateOf(false); private set  // منع لقطات الشاشة
    var antiFingerprint by mutableStateOf(true); private set // الحماية من البصمة
    var lang by mutableIntStateOf(0); private set           // لغة التطبيق: 0 تلقائي، 1 عربية، 2 English
    var siteLang by mutableIntStateOf(0); private set       // لغة المواقع (فهرس في siteLangs)
    var textZoom by mutableIntStateOf(1); private set       // فهرس في zoomValues
    var siteDark by mutableStateOf(false); private set      // الوضع الداكن للمواقع
    val siteLangs = listOf("" to "تلقائي", "ar" to "العربية", "en" to "English", "fr" to "Français", "es" to "Español",
        "de" to "Deutsch", "tr" to "Türkçe", "ru" to "Русский", "fa" to "فارسی", "ur" to "اردو", "id" to "Indonesia")
    val zoomValues = listOf(85, 100, 115, 130)
    fun siteLangCode() = siteLangs.getOrNull(siteLang)?.first ?: ""
    var pauseBg by mutableStateOf(true); private set        // إيقاف الصفحات عند الخروج من التطبيق

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
        autoClean = p.getBoolean("autoclean", true); blockAds = p.getBoolean("blockads", true)
        dataSaver = p.getBoolean("saver", false); lazyMedia = p.getBoolean("lazy", true)
        httpsFirst = p.getBoolean("https1", true); cleanUrls = p.getBoolean("cleanurl", true)
        blockThirdCookies = p.getBoolean("c3p", true); secureScreen = p.getBoolean("secscr", false)
        antiFingerprint = p.getBoolean("antifp", true); pauseBg = p.getBoolean("pausebg", true)
        lang = p.getInt("lang", 0); siteLang = p.getInt("sitelang", 0).coerceIn(0, siteLangs.lastIndex)
        textZoom = p.getInt("zoom", 1).coerceIn(0, zoomValues.lastIndex); siteDark = p.getBoolean("sitedark", false)
    }
    fun pickEngine(v: Int) { engine = v; sp?.edit()?.putInt("engine", v)?.apply() }
    fun pickTheme(v: Int) { theme = v; sp?.edit()?.putInt("theme", v)?.apply() }
    fun pickDesktop(v: Boolean) { desktop = v; sp?.edit()?.putBoolean("desktop", v)?.apply() }
    fun pickJs(v: Boolean) { js = v; sp?.edit()?.putBoolean("js", v)?.apply() }
    fun pickRestore(v: Boolean) { restore = v; sp?.edit()?.putBoolean("restore", v)?.apply() }
    fun pickMaxConns(v: Int) { maxConns = v; sp?.edit()?.putInt("maxc", v)?.apply() }
    fun pickAutoClean(v: Boolean) { autoClean = v; sp?.edit()?.putBoolean("autoclean", v)?.apply() }
    fun pickBlockAds(v: Boolean) { blockAds = v; sp?.edit()?.putBoolean("blockads", v)?.apply() }
    fun pickDataSaver(v: Boolean) { dataSaver = v; sp?.edit()?.putBoolean("saver", v)?.apply() }
    fun pickHttpsFirst(v: Boolean) { httpsFirst = v; sp?.edit()?.putBoolean("https1", v)?.apply() }
    fun pickCleanUrls(v: Boolean) { cleanUrls = v; sp?.edit()?.putBoolean("cleanurl", v)?.apply() }
    fun pickThirdCookies(v: Boolean) { blockThirdCookies = v; sp?.edit()?.putBoolean("c3p", v)?.apply() }
    fun pickAntiFingerprint(v: Boolean) { antiFingerprint = v; sp?.edit()?.putBoolean("antifp", v)?.apply() }
    fun pickLang(v: Int) { lang = v; sp?.edit()?.putInt("lang", v)?.apply() }
    fun pickSiteLang(v: Int) { siteLang = v; sp?.edit()?.putInt("sitelang", v)?.apply() }
    fun pickTextZoom(v: Int) { textZoom = v; sp?.edit()?.putInt("zoom", v)?.apply() }
    fun pickSiteDark(v: Boolean) { siteDark = v; sp?.edit()?.putBoolean("sitedark", v)?.apply() }
    fun pickPauseBg(v: Boolean) { pauseBg = v; sp?.edit()?.putBoolean("pausebg", v)?.apply() }
    fun pickSecureScreen(v: Boolean) { secureScreen = v; sp?.edit()?.putBoolean("secscr", v)?.apply() }
    fun pickLazyMedia(v: Boolean) { lazyMedia = v; sp?.edit()?.putBoolean("lazy", v)?.apply() }

    /** قراءة مبكرة (قبل Prefs.init) لتقرير التنظيف أثناء الـ Splash. */
    fun autoCleanEnabled(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("autoclean", true)
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
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(L("إغلاق")) } }
    )
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onClearData: () -> Unit, onClearCache: () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    var dialog by remember { mutableStateOf<String?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var cacheSize by remember { mutableStateOf("…") }
    var cacheTick by remember { mutableIntStateOf(0) }
    val warnings = remember { Security.deviceWarnings(ctx) }
    LaunchedEffect(cacheTick) {
        cacheSize = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CacheCleaner.format(CacheCleaner.size(ctx)) }
    }
    val themeNames = listOf(L("تلقائي (حسب النظام)"), L("فاتح"), L("داكن"))
    val langNames = listOf(L("تلقائي"), "العربية", "English")
    val zoomNames = listOf(L("صغير"), L("عادي"), L("كبير"), L("كبير جداً"))
    val connOpts = listOf(0, 4, 8, 16)
    val connNames = listOf(L("تلقائي (حتى 16)"), "4", "8", "16")

    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundBtn(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, L("رجوع")) }
                Spacer(Modifier.width(8.dp))
                Text(L("الإعدادات"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Group(L("عام"), listOf(
                    RowSpec(L("محرك البحث"), Prefs.engines[Prefs.engine].first, Icons.Default.Search, { dialog = "engine" }),
                    RowSpec(L("المظهر"), themeNames[Prefs.theme], Icons.Default.Star, { dialog = "theme" }),
                    RowSpec(L("نسخة سطح المكتب افتراضياً"), L("للتبويبات الجديدة"), Icons.Default.Build, { Prefs.pickDesktop(!Prefs.desktop) },
                        { Switch(checked = Prefs.desktop, onCheckedChange = null) })
                ))
                Group(L("التصفح والخصوصية"), listOf(
                    RowSpec("JavaScript", L("تعطيله قد يكسر بعض المواقع"), Icons.Default.Check, { Prefs.pickJs(!Prefs.js) },
                        { Switch(checked = Prefs.js, onCheckedChange = null) }),
                    RowSpec(L("استعادة التبويبات"), L("عند فتح التطبيق"), Icons.Default.Refresh, { Prefs.pickRestore(!Prefs.restore) },
                        { Switch(checked = Prefs.restore, onCheckedChange = null) }),
                    RowSpec(L("حجب الإعلانات والمتتبعات"), L("يسرّع الصفحات ويوفر البيانات"), Icons.Default.Check, { Prefs.pickBlockAds(!Prefs.blockAds) },
                        { Switch(checked = Prefs.blockAds, onCheckedChange = null) }),
                    RowSpec(L("مسح بيانات التصفح"), L("الكوكيز والذاكرة المؤقتة والسجل"), Icons.Default.Delete, { dialog = "clear" })
                ))
                Group(L("اللغة والعرض"), listOf(
                    RowSpec(L("لغة التطبيق"), langNames[Prefs.lang.coerceIn(0, 2)], Icons.Default.Settings, { dialog = "lang" }),
                    RowSpec(L("لغة المواقع"), Prefs.siteLangs[Prefs.siteLang].let { if (it.first.isEmpty()) L(it.second) else it.second }, Icons.Default.Search, { dialog = "sitelang" }),
                    RowSpec(L("حجم الخط في المواقع"), zoomNames[Prefs.textZoom], Icons.Default.Edit, { dialog = "zoom" }),
                    RowSpec(L("الوضع الداكن للمواقع"), L("يتبع وضع النظام الداكن"), Icons.Default.Star, { Prefs.pickSiteDark(!Prefs.siteDark) },
                        { Switch(checked = Prefs.siteDark, onCheckedChange = null) })
                ))
                Group(L("الأمان"), listOf(
                    RowSpec(L("HTTPS أولاً"), L("ترقية الروابط إلى اتصال مشفّر مع رجوع تلقائي عند عدم الدعم"), Icons.Default.Lock, { Prefs.pickHttpsFirst(!Prefs.httpsFirst) },
                        { Switch(checked = Prefs.httpsFirst, onCheckedChange = null) }),
                    RowSpec(L("الحماية من البصمة"), L("توحيد وتشويش قيم Canvas وWebGL والصوت والجهاز (يُطبَّق على التبويبات الجديدة)"), Icons.Default.Lock, { Prefs.pickAntiFingerprint(!Prefs.antiFingerprint) },
                        { Switch(checked = Prefs.antiFingerprint, onCheckedChange = null) }),
                    RowSpec(L("إزالة معرّفات التتبع"), L("utm و fbclid و gclid وغيرها من الروابط"), Icons.Default.Check, { Prefs.pickCleanUrls(!Prefs.cleanUrls) },
                        { Switch(checked = Prefs.cleanUrls, onCheckedChange = null) }),
                    RowSpec(L("حظر كوكيز الطرف الثالث"), L("قد تتأثر بعض مواقع تسجيل الدخول المشترك"), Icons.Default.Info, { Prefs.pickThirdCookies(!Prefs.blockThirdCookies) },
                        { Switch(checked = Prefs.blockThirdCookies, onCheckedChange = null) }),
                    RowSpec(L("حماية الشاشة"), L("منع لقطات الشاشة وتسجيلها داخل التطبيق"), Icons.Default.Lock, { Prefs.pickSecureScreen(!Prefs.secureScreen) },
                        { Switch(checked = Prefs.secureScreen, onCheckedChange = null) }),
                    RowSpec(L("سجل الأمان"), L("التهديدات والمتتبعات المحجوبة"), Icons.Default.Info, { dialog = "seclog" }),
                    RowSpec(L("فحص سلامة الجهاز"), if (warnings.isEmpty()) L("لا مؤشرات مقلقة") else warnings.joinToString(" • "), Icons.Default.Warning, {})
                ))
                Group(L("الأداء والذاكرة المؤقتة"), listOf(
                    RowSpec(L("تنظيف المؤقت عند كل تشغيل"), L("يُحذف أثناء شاشة البداية (لا يمس الكوكيز والتنزيلات)"), Icons.Default.Refresh, { Prefs.pickAutoClean(!Prefs.autoClean) },
                        { Switch(checked = Prefs.autoClean, onCheckedChange = null) }),
                    RowSpec(L("مسح الذاكرة المؤقتة الآن"), (L("الحجم الحالي: ") + cacheSize), Icons.Default.Delete, { dialog = "cache" }),
                    RowSpec(L("إيقاف الصفحات في الخلفية"), L("يوفر المعالج والبطارية عند الخروج من التطبيق (يوقف الصوت أيضاً)"), Icons.Default.Refresh, { Prefs.pickPauseBg(!Prefs.pauseBg) },
                        { Switch(checked = Prefs.pauseBg, onCheckedChange = null) }),
                    RowSpec(L("تحميل كسول للصور"), L("تحميل الصور عند الاقتراب منها فقط"), Icons.Default.KeyboardArrowDown, { Prefs.pickLazyMedia(!Prefs.lazyMedia) },
                        { Switch(checked = Prefs.lazyMedia, onCheckedChange = null) }),
                    RowSpec(L("توفير البيانات"), L("عدم تحميل الصور (يُطبَّق على التبويبات الجديدة)"), Icons.Default.Info, { Prefs.pickDataSaver(!Prefs.dataSaver) },
                        { Switch(checked = Prefs.dataSaver, onCheckedChange = null) })
                ))
                Group(L("التنزيلات"), listOf(
                    RowSpec(L("الحد الأقصى للاتصالات"), connNames[connOpts.indexOf(Prefs.maxConns).coerceAtLeast(0)], Icons.Default.KeyboardArrowDown, { dialog = "conns" }),
                    RowSpec(L("مكان الحفظ"), "Download/Nova", Icons.Default.Info, {})
                ))
                Group(L("حول"), listOf(RowSpec("Nova Browser", L("الإصدار 1.5"), Icons.Default.Star, {})))
            }
        }
    }

    when (dialog) {
        "lang" -> ChoiceDialog(L("لغة التطبيق"), langNames, Prefs.lang.coerceIn(0, 2), { Prefs.pickLang(it) }) { dialog = null }
        "sitelang" -> ChoiceDialog(L("لغة المواقع"), Prefs.siteLangs.map { if (it.first.isEmpty()) L(it.second) else it.second }, Prefs.siteLang, { Prefs.pickSiteLang(it) }) { dialog = null }
        "zoom" -> ChoiceDialog(L("حجم الخط في المواقع"), zoomNames, Prefs.textZoom, { Prefs.pickTextZoom(it) }) { dialog = null }
        "engine" -> ChoiceDialog(L("محرك البحث"), Prefs.engines.map { it.first }, Prefs.engine, { Prefs.pickEngine(it) }) { dialog = null }
        "theme" -> ChoiceDialog(L("المظهر"), themeNames, Prefs.theme, { Prefs.pickTheme(it) }) { dialog = null }
        "conns" -> ChoiceDialog(L("الحد الأقصى للاتصالات"), connNames, connOpts.indexOf(Prefs.maxConns).coerceAtLeast(0), { Prefs.pickMaxConns(connOpts[it]) }) { dialog = null }
        "seclog" -> AlertDialog(
            onDismissRequest = { dialog = null }, title = { Text(L("سجل الأمان")) },
            text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) { Text(Security.summary(), style = MaterialTheme.typography.bodySmall) } },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(L("إغلاق")) } },
            dismissButton = { TextButton(onClick = { Security.clearLog(); dialog = null }) { Text(L("مسح السجل")) } }
        )
        "cache" -> AlertDialog(
            onDismissRequest = { dialog = null }, title = { Text(L("مسح الذاكرة المؤقتة؟")) },
            text = { Text(L("سيتم حذف ملفات الكاش المؤقتة فقط. قد يبطؤ تحميل الصفحات المرة القادمة قليلاً.")) },
            confirmButton = { TextButton(onClick = { dialog = null; onClearCache(); cacheTick++ }) { Text(L("مسح")) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(L("إلغاء")) } }
        )
        "clear" -> AlertDialog(
            onDismissRequest = { dialog = null }, title = { Text(L("مسح بيانات التصفح؟")) },
            text = { Text(L("سيتم حذف الكوكيز والذاكرة المؤقتة وسجل التبويبات. لن تُحذف التنزيلات.")) },
            confirmButton = { TextButton(onClick = { dialog = null; onClearData() }) { Text(L("مسح")) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(L("إلغاء")) } }
        )
    }
}
