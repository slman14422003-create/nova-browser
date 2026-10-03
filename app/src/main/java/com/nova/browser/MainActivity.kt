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
    val hideCustom: () -> Unit
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

fun download(c: Context, url: String, ua: String?, cd: String?, mime: String?) {
    runCatching {
        val name = URLUtil.guessFileName(url, cd, mime)
        val req = DownloadManager.Request(Uri.parse(url))
            .setMimeType(mime).addRequestHeader("User-Agent", ua ?: "")
            .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        (c.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        toast(c, "بدأ التنزيل: $name")
    }.onFailure { toast(c, "تعذّر التنزيل") }
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
    setDownloadListener { url, ua, cd, mime, _ -> download(ctx, url, ua, cd, mime) }
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
                choose(ctx, listOf("تنزيل الصورة" to { download(ctx, ex, settings.userAgentString, null, null) }, "فتح الصورة في تبويب جديد" to { h.openTab(ex) }))
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val start = intent?.data?.toString() ?: ""
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) { BrowserApp(start) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserApp(startUrl: String) {
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
    var pendingPerm by remember { mutableStateOf<PermissionRequest?>(null) }
    var pendingGeo by remember { mutableStateOf<Pair<String, GeolocationPermissions.Callback>?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { m ->
        pendingPerm?.let { if (m.values.all { v -> v }) it.grant(it.resources) else it.deny() }; pendingPerm = null
        pendingGeo?.let { (o, cb) -> cb.invoke(o, m.values.any { v -> v }, false) }; pendingGeo = null
    }

    fun go(t: BrowserTab, input: String) { val u = normalize(input); t.url = u; t.webView?.loadUrl(u) }
    fun newTab() { tabs.add(BrowserTab(nextId++)); current = tabs.lastIndex; showTabs = false; editing = true }
    fun openInNewTab(u: String) { tabs.add(BrowserTab(nextId++, u)); current = tabs.lastIndex }
    fun closeTab(i: Int) {
        tabs[i].webView?.destroy(); tabs.removeAt(i)
        if (tabs.isEmpty()) tabs.add(BrowserTab(nextId++))
        current = current.coerceIn(0, tabs.lastIndex)
    }
    fun home(t: BrowserTab) {
        t.webView?.destroy(); t.webView = null
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
                    val missing = req.resources.mapNotNull {
                        when (it) {
                            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
                            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                            else -> null
                        }
                    }.filter { ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED }
                    if (missing.isEmpty()) req.grant(req.resources)
                    else { pendingPerm = req; permLauncher.launch(missing.toTypedArray()) }
                }
            },
            geo = { origin, cb ->
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                    cb.invoke(origin, true, false)
                else { pendingGeo = origin to cb; permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) }
            },
            openTab = { openInNewTab(it) },
            showCustom = { v, cb -> customView = v; customCb = cb },
            hideCustom = { customView = null; customCb = null }
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
    BackHandler(enabled = customView != null) { customCb?.onCustomViewHidden(); customView = null; customCb = null }

    val primaryInt = cs.primary.toArgb()
    val bgInt = cs.surfaceContainerHigh.toArgb()

    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize(), color = cs.surfaceContainer) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Box(Modifier.weight(1f).fillMaxWidth().background(cs.surfaceContainer)) {
                    if (tab.url.isBlank()) {
                        StartPage(onSearchClick = { editing = true }, onOpen = { go(tab, it) })
                    } else {
                        key(tab.id) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    val wv = tab.webView ?: createWebView(ctx, tab, handlers).also { tab.webView = it; it.loadUrl(tab.url) }
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
                if (tab.finding) key(tab.id) { FindBar(tab) } else BottomPill(
                    tab = tab, tabCount = tabs.size, editing = editing, setEditing = { editing = it },
                    onGo = { go(tab, it) }, onTabs = { showTabs = true }, onNewTab = { newTab() }, onHome = { home(tab) },
                    onFind = { tab.findInfo = ""; tab.finding = true },
                    onDesktop = { tab.desktop = !tab.desktop; tab.webView?.let { applyUa(it, tab.desktop); it.reload() } },
                    onShare = { shareText(activity, tab.url) }, onCopy = { copyText(activity, tab.url) }
                )
            }
        }
        customView?.let { v ->
            AndroidView(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                factory = { ctx -> FrameLayout(ctx).apply { setBackgroundColor(android.graphics.Color.BLACK); (v.parent as? ViewGroup)?.removeView(v); addView(v) } }
            )
        }
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
                            modifier = Modifier.height(116.dp)
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

@Composable
fun BottomPill(
    tab: BrowserTab, tabCount: Int, editing: Boolean, setEditing: (Boolean) -> Unit,
    onGo: (String) -> Unit, onTabs: () -> Unit, onNewTab: () -> Unit, onHome: () -> Unit,
    onFind: () -> Unit, onDesktop: () -> Unit, onShare: () -> Unit, onCopy: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    var menu by remember { mutableStateOf(false) }
    val p by animateFloatAsState(tab.progress, label = "progress")
    val hasPage = tab.url.isNotBlank()

    Surface(Modifier.imePadding().fillMaxWidth(), shape = RectangleShape, color = cs.surfaceContainer) {
        Column(Modifier.navigationBarsPadding().animateContentSize()) {
            if (tab.loading && !editing) {
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(3.dp), trackColor = Color.Transparent)
            } else Spacer(Modifier.height(3.dp))

            Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (editing) {
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
                            DropdownMenuItem(text = { Text("الرئيسية") }, leadingIcon = { Icon(Icons.Default.Home, null) }, onClick = { menu = false; onHome() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StartPage(onSearchClick: () -> Unit, onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sites = listOf(
        Triple("Google", "google.com", Color(0xFF4285F4)), Triple("YouTube", "youtube.com", Color(0xFFFF4D4D)),
        Triple("Wikipedia", "wikipedia.org", Color(0xFF8A8F9E)), Triple("GitHub", "github.com", Color(0xFF8B5CF6))
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(cs.primaryContainer.copy(alpha = 0.6f), cs.surfaceContainer)))) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Nova", fontSize = 58.sp, fontWeight = FontWeight.Bold, color = cs.primary)
            Text("تصفح أنعم وأسرع", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(36.dp))
            Surface(onClick = onSearchClick, shape = CircleShape, color = cs.surfaceContainer, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = cs.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text("ابحث أو اكتب عنوان", color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(36.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                sites.forEach { (name, url, c) ->
                    Column(Modifier.clip(RoundedCornerShape(18.dp)).clickable { onOpen(url) }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(58.dp).clip(RoundedCornerShape(20.dp)).background(c.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Text(name.take(1), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(name, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
