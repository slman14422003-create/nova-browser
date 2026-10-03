package com.nova.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.*
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.app.ActivityCompat
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.json.JSONObject
import java.net.URLEncoder

private val LightColors = lightColorScheme(
    primary = Color(0xFF3D5AFE), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE2FF), onPrimaryContainer = Color(0xFF001258),
    background = Color(0xFFF1F3FA), onBackground = Color(0xFF191B23),
    surface = Color(0xFFF1F3FA), onSurface = Color(0xFF191B23), onSurfaceVariant = Color(0xFF5A5E72),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFECEEF7), surfaceContainerHighest = Color(0xFFE4E7F2)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB0FF), onPrimary = Color(0xFF0A1A6B),
    primaryContainer = Color(0xFF26346F), onPrimaryContainer = Color(0xFFDDE2FF),
    background = Color(0xFF0E1015), onBackground = Color(0xFFE5E6EE),
    surface = Color(0xFF0E1015), onSurface = Color(0xFFE5E6EE), onSurfaceVariant = Color(0xFF9EA2B5),
    surfaceContainer = Color(0xFF1A1D26), surfaceContainerHigh = Color(0xFF232733), surfaceContainerHighest = Color(0xFF2B3040)
)

class BrowserTab(val id: Int, startUrl: String = "") {
    var url by mutableStateOf(startUrl)
    var title by mutableStateOf("تبويب جديد")
    var progress by mutableFloatStateOf(0f)
    var loading by mutableStateOf(false)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var desktop by mutableStateOf(false)
    var finding by mutableStateOf(false)
    var findInfo by mutableStateOf("")
    var webView: WebView? = null
}

class Handlers(
    val activity: ComponentActivity,
    val chooser: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Boolean,
    val permission: (PermissionRequest) -> Unit,
    val geo: (String, GeolocationPermissions.Callback) -> Unit,
    val openTab: (String) -> Unit,
    val showCustom: (View, WebChromeClient.CustomViewCallback) -> Unit,
    val hideCustom: () -> Unit,
    val onDownload: (String, String?, String?, String?, String?) -> Unit
)

fun normalize(input: String): String {
    val t = input.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") -> t
        t.contains(".") && !t.contains(" ") -> "https://$t"
        else -> "https://www.google.com/search?q=" + URLEncoder.encode(t, "UTF-8")
    }
}

fun hostOf(u: String): String = runCatching { java.net.URI(u).host?.removePrefix("www.") }.getOrNull() ?: u

const val DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

fun applyUa(wv: WebView, desktop: Boolean) {
    val s = wv.settings
    s.userAgentString = if (desktop) DESKTOP_UA
    else WebSettings.getDefaultUserAgent(wv.context).replace("; wv", "").replace(Regex("Version/\\S+ "), "")
    s.useWideViewPort = true
    s.loadWithOverviewMode = desktop
}

fun toast(c: Context, m: String) = Toast.makeText(c, m, Toast.LENGTH_SHORT).show()

fun copyText(c: Context, t: String) {
    (c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("url", t))
    toast(c, "تم النسخ")
}

fun shareText(c: Context, t: String) {
    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, t) }
    c.startActivity(Intent.createChooser(i, null))
}

fun choose(c: Context, items: List<Pair<String, () -> Unit>>) {
    AlertDialog.Builder(c).setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }.show()
}

fun errorHtml(url: String, desc: String): String = """
<html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>
:root{color-scheme:light dark}body{font-family:sans-serif;display:flex;flex-direction:column;align-items:center;
justify-content:center;height:100vh;margin:0;padding:24px;text-align:center;direction:rtl}
h2{margin:8px}p{opacity:.65;word-break:break-all;margin:4px}
button{margin-top:22px;padding:12px 30px;border:0;border-radius:24px;background:#3D5AFE;color:#fff;font-size:16px}
</style></head><body><div style="font-size:56px">📡</div><h2>تعذّر فتح الصفحة</h2>
<p>${android.text.TextUtils.htmlEncode(desc)}</p><p>${android.text.TextUtils.htmlEncode(url)}</p>
<button onclick='location.replace(${JSONObject.quote(url)})'>إعادة المحاولة</button></body></html>"""

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
fun createWebView(ctx: Context, tab: BrowserTab, h: Handlers): WebView = WebView(ctx).apply {
    setLayerType(View.LAYER_TYPE_HARDWARE, null)
    with(settings) {
        javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true
        mediaPlaybackRequiresUserGesture = false
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(false)
        allowFileAccess = false
        setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        setOffscreenPreRaster(true)
    }
    applyUa(this, tab.desktop)
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
    setDownloadListener { u, ua, cd, mime, _ -> h.onDownload(u, ua, cd, mime, this.url) }
    setFindListener { active, total, _ -> tab.findInfo = if (total == 0) "0" else "${active + 1}/$total" }
    setOnLongClickListener {
        val r = hitTestResult
        val ex = r.extra ?: return@setOnLongClickListener false
        when (r.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                choose(ctx, listOf("فتح في تبويب جديد" to { h.openTab(ex) }, "نسخ الرابط" to { copyText(ctx, ex) }, "مشاركة الرابط" to { shareText(ctx, ex) }))
                true
            }
            WebView.HitTestResult.IMAGE_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                choose(ctx, listOf("تنزيل الصورة" to { h.onDownload(ex, settings.userAgentString, null, null, this.url) }, "فتح الصورة في تبويب جديد" to { h.openTab(ex) }))
                true
            }
            else -> false
        }
    }
    webViewClient = object : WebViewClient() {
        override fun onPageStarted(v: WebView, u: String, f: Bitmap?) { tab.loading = true; tab.url = u }
        override fun onPageFinished(v: WebView, u: String) {
            tab.loading = false; tab.url = u
            tab.canBack = v.canGoBack(); tab.canForward = v.canGoForward()
            (v.parent as? SwipeRefreshLayout)?.isRefreshing = false
        }
        override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
            if (r.isForMainFrame) {
                val u = r.url.toString()
                v.loadDataWithBaseURL(u, errorHtml(u, e.description.toString()), "text/html", "UTF-8", u)
            }
        }
        override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
            val u = r.url
            return when (u.scheme) {
                null, "http", "https", "about", "data", "blob" -> false
                "intent" -> {
                    runCatching {
                        Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME)
                            .getStringExtra("browser_fallback_url")?.let { v.loadUrl(it) }
                    }
                    true
                }
                "tel", "mailto", "sms", "geo" -> {
                    runCatching { v.context.startActivity(Intent(Intent.ACTION_VIEW, u)) }
                    true
                }
                else -> true
            }
        }
    }
    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(v: WebView, p: Int) { tab.progress = p / 100f }
        override fun onReceivedTitle(v: WebView, t: String?) { if (!t.isNullOrBlank()) tab.title = t }
        override fun onShowCustomView(view: View, cb: CustomViewCallback) = h.showCustom(view, cb)
        override fun onHideCustomView() = h.hideCustom()
        override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams) = h.chooser(cb, p)
        override fun onPermissionRequest(req: PermissionRequest) = h.permission(req)
        override fun onGeolocationPermissionsShowPrompt(origin: String, cb: GeolocationPermissions.Callback) = h.geo(origin, cb)
        override fun onJsAlert(v: WebView, url: String, msg: String, r: JsResult): Boolean {
            AlertDialog.Builder(ctx).setMessage(msg).setPositiveButton("حسناً") { _, _ -> r.confirm() }
                .setOnCancelListener { r.cancel() }.show()
            return true
        }
        override fun onJsConfirm(v: WebView, url: String, msg: String, r: JsResult): Boolean {
            AlertDialog.Builder(ctx).setMessage(msg).setPositiveButton("موافق") { _, _ -> r.confirm() }
                .setNegativeButton("إلغاء") { _, _ -> r.cancel() }.setOnCancelListener { r.cancel() }.show()
            return true
        }
    }
}

class MainActivity : ComponentActivity() {
    private var dlTrigger by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Downloader.init(this)
        val start = intent?.data?.toString() ?: ""
        if (intent?.getBooleanExtra("dl", false) == true) dlTrigger++
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) { BrowserApp(start, dlTrigger) }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("dl", false)) dlTrigger++
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserApp(startUrl: String, dlTrigger: Int) {
    val activity = LocalContext.current as ComponentActivity
    val cs = MaterialTheme.colorScheme
    val prefs = remember { activity.getSharedPreferences("nova", Context.MODE_PRIVATE) }
    val tabs = remember {
        mutableStateListOf<BrowserTab>().apply {
            prefs.getString("tabs", "")!!.split("\n").filter { it.isNotBlank() }
                .forEachIndexed { i, u -> add(BrowserTab(i, if (u == "-") "" else u)) }
            if (startUrl.isNotBlank()) add(BrowserTab(size, startUrl))
            if (isEmpty()) add(BrowserTab(0, ""))
        }
    }
    var nextId by remember { mutableIntStateOf(tabs.size + 1000) }
    var current by remember { mutableIntStateOf(if (startUrl.isNotBlank()) tabs.lastIndex else prefs.getInt("cur", 0).coerceIn(0, tabs.lastIndex)) }
    var showTabs by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    LaunchedEffect(dlTrigger) { if (dlTrigger > 0) showDownloads = true }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) toast(activity, "فعّل الإشعارات من الإعدادات لمتابعة التنزيل في الخلفية")
    }
    var customView by remember { mutableStateOf<View?>(null) }
    var customCb by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    val tab = tabs[current.coerceIn(0, tabs.lastIndex)]

    LaunchedEffect(Unit) {
        snapshotFlow { tabs.joinToString("\n") { it.url.ifBlank { "-" } } to current }
            .collect { (s, c) -> prefs.edit().putString("tabs", s).putInt("cur", c).apply() }
    }

    var fileCb by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data)); fileCb = null
    }
    var sitePrompt by remember { mutableStateOf<SitePrompt?>(null) }
    var settingsMsg by remember { mutableStateOf<String?>(null) }
    val decisions = remember { mutableStateMapOf<String, Boolean>() }
    var permCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun granted(p: String) = ContextCompat.checkSelfPermission(activity, p) == PackageManager.PERMISSION_GRANTED
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { m ->
        val cb = permCallback; permCallback = null
        val denied = m.filter { !it.value }.keys
        if (denied.any { !ActivityCompat.shouldShowRequestPermissionRationale(activity, it) })
            settingsMsg = "تم رفض الإذن بشكل دائم. فعّله من إعدادات التطبيق ليعمل هذا الموقع."
        cb?.invoke()
    }
    fun askPerms(perms: List<String>, cb: () -> Unit) {
        if (perms.all { granted(it) }) cb()
        else { permCallback = cb; permLauncher.launch(perms.filter { !granted(it) }.toTypedArray()) }
    }

    fun go(t: BrowserTab, input: String) { val u = normalize(input); t.url = u; t.webView?.loadUrl(u) }
    fun newTab() { tabs.add(BrowserTab(nextId++)); current = tabs.lastIndex; showTabs = false; editing = true }
    fun openInNewTab(u: String) { tabs.add(BrowserTab(nextId++, u)); current = tabs.lastIndex }
    fun dispose(t: BrowserTab) {
        t.webView?.let { w -> (w.parent as? ViewGroup)?.removeView(w); w.destroy() }
        t.webView = null
    }
    fun closeTab(i: Int) {
        dispose(tabs[i]); tabs.removeAt(i)
        if (tabs.isEmpty()) tabs.add(BrowserTab(nextId++))
        current = current.coerceIn(0, tabs.lastIndex)
    }
    fun home(t: BrowserTab) {
        dispose(t)
        t.url = ""; t.title = "تبويب جديد"; t.canBack = false; t.canForward = false; t.loading = false; t.finding = false
    }

    val handlers = remember {
        Handlers(
            activity = activity,
            chooser = { cb, p ->
                fileCb?.onReceiveValue(null); fileCb = cb
                runCatching { fileLauncher.launch(p.createIntent()) }.onFailure { cb.onReceiveValue(null); fileCb = null }
                true
            },
            permission = { req ->
                activity.runOnUiThread {
                    val av = req.resources.filter { it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
                    if (av.isEmpty()) { req.deny(); return@runOnUiThread }
                    fun perm(r: String) = if (r == PermissionRequest.RESOURCE_VIDEO_CAPTURE) Manifest.permission.CAMERA else Manifest.permission.RECORD_AUDIO
                    val label = av.joinToString(" و") { if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) "الكاميرا" else "الميكروفون" }
                    fun finish(allow: Boolean) {
                        if (!allow) { req.deny(); return }
                        askPerms(av.map { perm(it) }) {
                            val ok = av.filter { granted(perm(it)) }.toTypedArray()
                            if (ok.isEmpty()) req.deny() else req.grant(ok)
                        }
                    }
                    val key = req.origin.toString() + "|av"
                    when (decisions[key]) {
                        true -> finish(true)
                        false -> req.deny()
                        null -> sitePrompt = SitePrompt("السماح بالوصول؟", "${hostOf(req.origin.toString())} يريد استخدام $label",
                            { decisions[key] = true; finish(true) }, { decisions[key] = false; finish(false) })
                    }
                }
            },
            geo = { origin, cb ->
                activity.runOnUiThread {
                    fun finish(allow: Boolean) {
                        if (!allow) { cb.invoke(origin, false, false); return }
                        val perms = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        askPerms(perms) { cb.invoke(origin, perms.any { granted(it) }, false) }
                    }
                    val key = origin + "|geo"
                    when (decisions[key]) {
                        true -> finish(true)
                        false -> cb.invoke(origin, false, false)
                        null -> sitePrompt = SitePrompt("السماح بالموقع؟", "${hostOf(origin)} يريد معرفة موقعك",
                            { decisions[key] = true; finish(true) }, { decisions[key] = false; finish(false) })
                    }
                }
            },
            openTab = { openInNewTab(it) },
            showCustom = { v, cb -> customView = v; customCb = cb },
            hideCustom = { customView = null; customCb = null },
            onDownload = { u, ua, cd, mime, ref ->
                if (u.startsWith("blob:") || u.startsWith("data:")) toast(activity, "هذا النوع من التنزيل غير مدعوم بعد")
                else {
                    Downloader.start(activity, u, ua, cd, mime, ref)
                    toast(activity, "بدأ التنزيل — القائمة ⋮ ثم التنزيلات")
                    if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS))
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )
    }

    DisposableEffect(customView != null) {
        val c = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (customView != null) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            c.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler(enabled = tab.canBack) { tab.webView?.goBack() }
    BackHandler(enabled = tab.finding) { tab.webView?.clearMatches(); tab.finding = false }
    BackHandler(enabled = editing) { editing = false }
    BackHandler(enabled = showDownloads) { showDownloads = false }
    BackHandler(enabled = customView != null) { customCb?.onCustomViewHidden(); customView = null; customCb = null }

    val primaryInt = cs.primary.toArgb()
    val bgInt = cs.surfaceContainerHigh.toArgb()

    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize(), color = cs.surfaceContainer) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Box(Modifier.weight(1f).fillMaxWidth().background(cs.surfaceContainer)) {
                    AnimatedContent(
                        targetState = tab.id to tab.url.isBlank(),
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                        label = "page"
                    ) { (id, blank) ->
                        val tb = tabs.firstOrNull { it.id == id }
                        if (tb != null) {
                            if (blank) {
                                StartPage(
                                    tabsCount = tabs.size,
                                    activeDl = Downloader.tasks.count { it.status == Downloader.DOWNLOADING || it.status == Downloader.PREPARING },
                                    onSearchClick = { editing = true }, onOpen = { go(tb, it) },
                                    onTabs = { showTabs = true }, onDownloads = { showDownloads = true }
                                )
                            } else key(id) {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { ctx ->
                                        val wv = tb.webView ?: createWebView(ctx, tb, handlers).also { tb.webView = it; it.loadUrl(tb.url) }
                                        (wv.parent as? ViewGroup)?.removeView(wv)
                                        SwipeRefreshLayout(ctx).apply {
                                            addView(wv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                                            setOnRefreshListener { wv.reload() }
                                            setOnChildScrollUpCallback { _, _ -> wv.scrollY > 0 }
                                            setColorSchemeColors(primaryInt)
                                            setProgressBackgroundColorSchemeColor(bgInt)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                if (tab.finding) key(tab.id) { FindBar(tab) } else BottomPill(
                    tab = tab, tabCount = tabs.size, editing = editing, setEditing = { editing = it },
                    onGo = { go(tab, it) }, onTabs = { showTabs = true }, onNewTab = { newTab() }, onHome = { home(tab) },
                    onFind = { tab.findInfo = ""; tab.finding = true },
                    onDesktop = { tab.desktop = !tab.desktop; tab.webView?.let { applyUa(it, tab.desktop); it.reload() } },
                    onShare = { shareText(activity, tab.url) }, onCopy = { copyText(activity, tab.url) },
                    onDownloads = { showDownloads = true },
                    onSwitch = { d -> current = (current + d).coerceIn(0, tabs.lastIndex) }
                )
            }
        }
        AnimatedVisibility(
            visible = showDownloads,
            enter = slideInVertically(tween(280)) { it / 6 } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(220)) { it / 6 } + fadeOut(tween(160))
        ) { DownloadsScreen(onBack = { showDownloads = false }) }
        customView?.let { v ->
            AndroidView(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                factory = { ctx -> FrameLayout(ctx).apply { setBackgroundColor(android.graphics.Color.BLACK); (v.parent as? ViewGroup)?.removeView(v); addView(v) } }
            )
        }
    }

    sitePrompt?.let { p ->
        AlertDialog(
            onDismissRequest = { sitePrompt = null; p.onDeny() },
            title = { Text(p.title) }, text = { Text(p.message) },
            confirmButton = { TextButton(onClick = { sitePrompt = null; p.onAllow() }) { Text("سماح") } },
            dismissButton = { TextButton(onClick = { sitePrompt = null; p.onDeny() }) { Text("رفض") } }
        )
    }
    settingsMsg?.let { m ->
        AlertDialog(
            onDismissRequest = { settingsMsg = null },
            title = { Text("الإذن مطلوب") }, text = { Text(m) },
            confirmButton = {
                TextButton(onClick = {
                    settingsMsg = null
                    activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)))
                }) { Text("فتح الإعدادات") }
            },
            dismissButton = { TextButton(onClick = { settingsMsg = null }) { Text("لاحقاً") } }
        )
    }

    if (showTabs) {
        ModalBottomSheet(onDismissRequest = { showTabs = false }, containerColor = cs.surface) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("التبويبات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = { newTab() }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("جديد") }
                }
                Spacer(Modifier.height(14.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(tabs, key = { _, t -> t.id }) { i, t ->
                        val sel = i == current
                        Surface(
                            onClick = { current = i; showTabs = false }, shape = RoundedCornerShape(22.dp), color = cs.surfaceContainer,
                            border = BorderStroke(if (sel) 2.dp else 1.dp, if (sel) cs.primary else cs.surfaceContainerHighest),
                            modifier = Modifier.height(116.dp).animateItem()
                        ) {
                            Box(Modifier.fillMaxSize().padding(14.dp)) {
                                Column(Modifier.align(Alignment.BottomStart).padding(end = 4.dp)) {
                                    Text(t.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                    Text(if (t.url.isBlank()) "صفحة البداية" else hostOf(t.url), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                                IconButton(onClick = { closeTab(i) }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
                                    Icon(Icons.Default.Close, "إغلاق", Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FindBar(tab: BrowserTab) {
    val cs = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    Surface(Modifier.imePadding().fillMaxWidth(), color = cs.surfaceContainer) {
        Row(Modifier.navigationBarsPadding().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = q, onValueChange = { q = it; tab.webView?.findAllAsync(it) }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface), cursorBrush = SolidColor(cs.primary),
                modifier = Modifier.weight(1f).padding(start = 12.dp).focusRequester(fr),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (q.isEmpty()) Text("بحث في الصفحة", color = cs.onSurfaceVariant)
                        inner()
                    }
                }
            )
            Text(tab.findInfo, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            IconButton(onClick = { tab.webView?.findNext(false) }) { Icon(Icons.Default.KeyboardArrowUp, "السابق") }
            IconButton(onClick = { tab.webView?.findNext(true) }) { Icon(Icons.Default.KeyboardArrowDown, "التالي") }
            IconButton(onClick = { tab.webView?.clearMatches(); tab.finding = false }) { Icon(Icons.Default.Close, "إغلاق") }
        }
    }
}

class SitePrompt(val title: String, val message: String, val onAllow: () -> Unit, val onDeny: () -> Unit)

@Composable
fun BottomPill(
    tab: BrowserTab, tabCount: Int, editing: Boolean, setEditing: (Boolean) -> Unit,
    onGo: (String) -> Unit, onTabs: () -> Unit, onNewTab: () -> Unit, onHome: () -> Unit,
    onFind: () -> Unit, onDesktop: () -> Unit, onShare: () -> Unit, onCopy: () -> Unit,
    onDownloads: () -> Unit, onSwitch: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    val p by animateFloatAsState(tab.progress, label = "progress")
    val hasPage = tab.url.isNotBlank()

    Surface(Modifier.imePadding().fillMaxWidth(), shape = RectangleShape, color = cs.surfaceContainer) {
        Column(Modifier.navigationBarsPadding().animateContentSize()) {
            if (tab.loading && !editing) {
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(3.dp), trackColor = Color.Transparent)
            } else Spacer(Modifier.height(3.dp))

            AnimatedContent(
                targetState = editing,
                transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(90)) },
                label = "bar"
            ) { isEditing ->
                Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isEditing) {
                        var field by remember { mutableStateOf(TextFieldValue(tab.url, TextRange(0, tab.url.length))) }
                        val fr = remember { FocusRequester() }
                        var got by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { fr.requestFocus() }
                        Icon(Icons.Default.Search, null, Modifier.padding(start = 10.dp), tint = cs.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        BasicTextField(
                            value = field, onValueChange = { field = it }, singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface), cursorBrush = SolidColor(cs.primary),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                if (field.text.isNotBlank()) onGo(field.text)
                                setEditing(false); focus.clearFocus()
                            }),
                            modifier = Modifier.weight(1f).focusRequester(fr).onFocusChanged { if (it.isFocused) got = true else if (got) setEditing(false) },
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (field.text.isEmpty()) Text("ابحث أو اكتب عنوان", color = cs.onSurfaceVariant)
                                    inner()
                                }
                            }
                        )
                        IconButton(onClick = { field = TextFieldValue("") }) { Icon(Icons.Default.Close, "مسح") }
                    } else {
                        IconButton(onClick = { if (tab.canBack) tab.webView?.goBack() else onHome() }) {
                            Icon(if (tab.canBack) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Home, null)
                        }
                        Row(
                            Modifier.weight(1f).height(44.dp).clip(CircleShape).background(cs.surfaceContainerHighest)
                                .pointerInput(Unit) {
                                    var dx = 0f; var dy = 0f
                                    detectDragGestures(
                                        onDragStart = { dx = 0f; dy = 0f },
                                        onDrag = { _, a -> dx += a.x; dy += a.y },
                                        onDragEnd = {
                                            if (dy < -60f && kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress); onTabs()
                                            } else if (kotlin.math.abs(dx) > 120f) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSwitch(if (dx < 0) 1 else -1)
                                            }
                                        }
                                    )
                                }
                                .clickable { setEditing(true) }.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(if (tab.url.startsWith("https")) Icons.Default.Lock else Icons.Default.Search, null, Modifier.size(15.dp), tint = cs.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (!hasPage) "ابحث أو اكتب عنوان" else hostOf(tab.url), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium, color = if (hasPage) cs.onSurface else cs.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onTabs) {
                            Box(Modifier.size(24.dp).border(2.dp, cs.onSurface, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                                Text("$tabCount", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "المزيد") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
                                DropdownMenuItem(text = { Text("تبويب جديد") }, leadingIcon = { Icon(Icons.Default.Add, null) }, onClick = { menu = false; onNewTab() })
                                DropdownMenuItem(text = { Text("التالي") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                                    enabled = tab.canForward, onClick = { menu = false; tab.webView?.goForward() })
                                DropdownMenuItem(text = { Text(if (tab.loading) "إيقاف" else "تحديث") },
                                    leadingIcon = { Icon(if (tab.loading) Icons.Default.Close else Icons.Default.Refresh, null) }, enabled = hasPage,
                                    onClick = { menu = false; if (tab.loading) tab.webView?.stopLoading() else tab.webView?.reload() })
                                DropdownMenuItem(text = { Text("بحث في الصفحة") }, leadingIcon = { Icon(Icons.Default.Search, null) }, enabled = hasPage,
                                    onClick = { menu = false; onFind() })
                                DropdownMenuItem(text = { Text("نسخة سطح المكتب") }, leadingIcon = { Icon(Icons.Default.Settings, null) },
                                    trailingIcon = { if (tab.desktop) Icon(Icons.Default.Check, null) }, enabled = hasPage,
                                    onClick = { menu = false; onDesktop() })
                                DropdownMenuItem(text = { Text("مشاركة الرابط") }, leadingIcon = { Icon(Icons.Default.Share, null) }, enabled = hasPage,
                                    onClick = { menu = false; onShare() })
                                DropdownMenuItem(text = { Text("نسخ الرابط") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, enabled = hasPage,
                                    onClick = { menu = false; onCopy() })
                                DropdownMenuItem(text = { Text("التنزيلات") }, leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, null) },
                                    onClick = { menu = false; onDownloads() })
                                DropdownMenuItem(text = { Text("الرئيسية") }, leadingIcon = { Icon(Icons.Default.Home, null) }, onClick = { menu = false; onHome() })
                            }
                        }
                    }
                }
            }
        }
    }
}

private class Site(val name: String, val url: String, val glyph: String, val color: Long)

@Composable
fun Reveal(shown: Boolean, delay: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(420, delay)) + slideInVertically(tween(420, delay)) { it / 6 }
    ) { content() }
}

@Composable
private fun RowScope.Tile(s: Site, onOpen: (String) -> Unit) {
    val c = Color(s.color)
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable { onOpen(s.url) }.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(c.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text(s.glyph, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c)
        }
        Spacer(Modifier.height(6.dp))
        Text(s.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun ActionCard(title: String, sub: String, highlight: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, shape = RoundedCornerShape(24.dp), modifier = modifier,
        color = if (highlight) cs.primaryContainer else cs.surfaceContainerHigh
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
fun StartPage(
    tabsCount: Int, activeDl: Int, onSearchClick: () -> Unit, onOpen: (String) -> Unit,
    onTabs: () -> Unit, onDownloads: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greet = when { hour < 5 -> "ليلة هادئة"; hour < 12 -> "صباح الخير"; hour < 18 -> "طاب يومك"; else -> "مساء الخير" }
    val date = remember { java.text.SimpleDateFormat("EEEE، d MMMM", java.util.Locale.forLanguageTag("ar")).format(java.util.Date()) }
    val sites = remember {
        listOf(
            Site("Google", "google.com", "G", 0xFF4285F4), Site("YouTube", "youtube.com", "▶", 0xFFFF3D3D),
            Site("Wikipedia", "wikipedia.org", "W", 0xFF7A8094), Site("GitHub", "github.com", "</>", 0xFF8B5CF6),
            Site("الخرائط", "maps.google.com", "📍", 0xFF34A853), Site("Gmail", "mail.google.com", "✉", 0xFFEA4335),
            Site("ترجمة", "translate.google.com", "文", 0xFF1A73E8), Site("الأخبار", "news.google.com", "N", 0xFFFB8C00)
        )
    }
    val recent = Downloader.tasks.take(2)

    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.radialGradient(listOf(cs.primary.copy(alpha = 0.20f), Color.Transparent), Offset(size.width * 0.95f, 0f), size.width * 0.9f))
            drawRect(Brush.radialGradient(listOf(Color(0xFF8B5CF6).copy(alpha = 0.14f), Color.Transparent), Offset(0f, size.height * 0.75f), size.width * 0.9f))
        }
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp)) {
            Reveal(shown, 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
                        Text("N", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = cs.onPrimary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(greet, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(date, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            Reveal(shown, 80) {
                Surface(onClick = onSearchClick, shape = CircleShape, color = cs.surfaceContainer, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                    Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = cs.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("ابحث في الويب أو اكتب رابطاً", color = cs.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
            Reveal(shown, 160) {
                Column {
                    Text("وصول سريع", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    sites.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) { row.forEach { Tile(it, onOpen) } }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Reveal(shown, 240) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionCard("التنزيلات", if (activeDl > 0) "$activeDl قيد التنزيل" else "الملفات المحمّلة", activeDl > 0, onDownloads, Modifier.weight(1f))
                    ActionCard("التبويبات", "$tabsCount مفتوحة", false, onTabs, Modifier.weight(1f))
                }
            }
            if (recent.isNotEmpty()) {
                Reveal(shown, 320) {
                    Column(Modifier.padding(top = 24.dp)) {
                        Text("آخر التنزيلات", style = MaterialTheme.typography.titleSmall, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        recent.forEach { t ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                    .clickable { if (t.status == Downloader.DONE) openFile(ctx, t) else onDownloads() }
                                    .padding(horizontal = 4.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(t.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    if (t.status == Downloader.DONE) fmtSize(t.total) else if (t.total > 0) "${t.downloaded * 100 / t.total}%" else "…",
                                    style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
