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
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import kotlinx.coroutines.flow.debounce
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
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.activity.SystemBarStyle
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.launch
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
    primary = Color(0xFF141413), onPrimary = Color(0xFFFAF9F5),
    primaryContainer = Color(0xFFF6DDD2), onPrimaryContainer = Color(0xFF5A2A1B),
    secondary = Color(0xFF3D3D3A), onSecondary = Color(0xFFFAF9F5),
    secondaryContainer = Color(0xFFE0DDD2), onSecondaryContainer = Color(0xFF141413),
    tertiary = Color(0xFFC6613F), onTertiary = Color.White,
    background = Color(0xFFFAF9F5), onBackground = Color(0xFF141413),
    surface = Color(0xFFFAF9F5), onSurface = Color(0xFF141413), onSurfaceVariant = Color(0xFF6B6A68),
    outline = Color(0xFFB0AEA5), outlineVariant = Color(0xFFE0DDD2),
    surfaceContainer = Color(0xFFF0EEE6), surfaceContainerHigh = Color(0xFFE9E6DC), surfaceContainerHighest = Color(0xFFDEDBD0)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFAF9F5), onPrimary = Color(0xFF141413),
    primaryContainer = Color(0xFF3B2A23), onPrimaryContainer = Color(0xFFF3B49C),
    secondary = Color(0xFFB0AEA5), onSecondary = Color(0xFF141413),
    secondaryContainer = Color(0xFF353330), onSecondaryContainer = Color(0xFFFAF9F5),
    tertiary = Color(0xFFD97757), onTertiary = Color.White,
    background = Color(0xFF141413), onBackground = Color(0xFFFAF9F5),
    surface = Color(0xFF141413), onSurface = Color(0xFFFAF9F5), onSurfaceVariant = Color(0xFFB0AEA5),
    outline = Color(0xFF6B6A68), outlineVariant = Color(0xFF3A3835),
    surfaceContainer = Color(0xFF1F1E1D), surfaceContainerHigh = Color(0xFF2A2927), surfaceContainerHighest = Color(0xFF353330)
)
private val NovaTypography = Typography().let { t ->
    val serif = androidx.compose.ui.text.font.FontFamily.Serif
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = serif), displayMedium = t.displayMedium.copy(fontFamily = serif),
        displaySmall = t.displaySmall.copy(fontFamily = serif), headlineLarge = t.headlineLarge.copy(fontFamily = serif),
        headlineMedium = t.headlineMedium.copy(fontFamily = serif), headlineSmall = t.headlineSmall.copy(fontFamily = serif),
        titleLarge = t.titleLarge.copy(fontFamily = serif)
    )
}

class BrowserTab(val id: Int, startUrl: String = "") {
    var upgradedFrom: String? = null        // رابط http الأصلي إذا رُقّي إلى https
    var upgradedTo: String? = null
    var noUpgradeHost: String? = null
    var lastUpgrade: Pair<String, Long>? = null
    var url by mutableStateOf(startUrl)
    var title by mutableStateOf(L("تبويب جديد"))
    var progress by mutableFloatStateOf(0f)
    var loading by mutableStateOf(false)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var desktop by mutableStateOf(Prefs.desktop)
    var finding by mutableStateOf(false)
    var findInfo by mutableStateOf("")
    var saved: android.os.Bundle? = null      // حالة الصفحة عند تحرير الـ WebView لتوفير الذاكرة
    var lastUsed = 0L
    var ytPlaying by mutableStateOf(false)
    var epoch by mutableIntStateOf(0)   // يزيد عند انهيار عملية العرض لإعادة إنشاء الـ WebView
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
    val onDownload: (String, String?, String?, String?, String?) -> Unit,
    val onLoginForm: (BrowserTab, WebView, String) -> Unit = { _, _, _ -> },
    val onCredential: (String, String, String) -> Unit = { _, _, _ -> },
    val onYtState: (BrowserTab) -> Unit = {}
)

fun normalize(input: String): String {
    val t = input.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") -> t
        t.contains(".") && !t.contains(" ") -> "https://$t"
        else -> Prefs.engines[Prefs.engine].second + URLEncoder.encode(t, "UTF-8")
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
    toast(c, L("تم النسخ"))
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
justify-content:center;height:100vh;margin:0;padding:24px;text-align:center;direction:${if (I18n.isEnglish()) "ltr" else "rtl"}}
h2{margin:8px}p{opacity:.65;word-break:break-all;margin:4px}
button{margin-top:22px;padding:12px 30px;border:0;border-radius:24px;background:#3D5AFE;color:#fff;font-size:16px}
</style></head><body><div style="font-size:56px">📡</div><h2>${L("تعذّر فتح الصفحة")}</h2>
<p>${android.text.TextUtils.htmlEncode(desc)}</p><p>${android.text.TextUtils.htmlEncode(url)}</p>
<button onclick='location.replace(${JSONObject.quote(url)})'>${L("إعادة المحاولة")}</button></body></html>"""

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
fun createWebView(ctx: Context, tab: BrowserTab, h: Handlers): WebView = WebView(ctx).apply {
    with(settings) {
        javaScriptEnabled = Prefs.js; domStorageEnabled = true; databaseEnabled = true
        mediaPlaybackRequiresUserGesture = false
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(false)
        allowFileAccess = false
        setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    }
    applyUa(this, tab.desktop)
    Perf.tune(this)
    Perf.installPrivacy(this)
    PasswordBridge.install(this, tab, h)
    YtBridge.install(this, tab, h)
    importantForAutofill = if (Prefs.pwMode == 1) View.IMPORTANT_FOR_AUTOFILL_YES else View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, !Prefs.blockThirdCookies)
    setDownloadListener { u, ua, cd, mime, _ -> h.onDownload(u, ua, cd, mime, this.url) }
    setFindListener { active, total, _ -> tab.findInfo = if (total == 0) "0" else "${active + 1}/$total" }
    setOnLongClickListener {
        val r = hitTestResult
        val ex = r.extra ?: return@setOnLongClickListener false
        when (r.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                choose(ctx, listOf(L("فتح في تبويب جديد") to { h.openTab(ex) }, L("نسخ الرابط") to { copyText(ctx, ex) }, L("مشاركة الرابط") to { shareText(ctx, ex) }))
                true
            }
            WebView.HitTestResult.IMAGE_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                choose(ctx, listOf(L("تنزيل الصورة") to { h.onDownload(ex, settings.userAgentString, null, null, this.url) }, L("فتح الصورة في تبويب جديد") to { h.openTab(ex) }))
                true
            }
            else -> false
        }
    }
    webViewClient = object : WebViewClient() {
        override fun onPageStarted(v: WebView, u: String, f: Bitmap?) { tab.loading = true; tab.url = u; Perf.onPageStart(v); YtMedia.pageChanged(tab, u) }
        override fun doUpdateVisitedHistory(v: WebView, u: String, isReload: Boolean) {
            // تنقّلات الصفحات أحادية الصفحة (مثل يوتيوب) لا تستدعي onPageStarted
            if (u.startsWith("http")) { tab.url = u; tab.canBack = v.canGoBack(); tab.canForward = v.canGoForward(); YtMedia.pageChanged(tab, u) }
        }
        override fun onPageFinished(v: WebView, u: String) {
            tab.loading = false; tab.url = u
            tab.canBack = v.canGoBack(); tab.canForward = v.canGoForward()
            (v.parent as? SwipeRefreshLayout)?.isRefreshing = false
            Perf.onPageDone(v)
            Perf.flushCookies()   // حفظ جلسات تسجيل الدخول (بحدّ أقصى كل 15 ثانية)
            PasswordBridge.onPageDone(v)
            YtBridge.onPageDone(v)
        }
        override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? =
            Perf.intercept(r.url, r.isForMainFrame)
        override fun onRenderProcessGone(v: WebView, d: RenderProcessGoneDetail): Boolean {
            // منع انهيار التطبيق: نُسقط الـ WebView ونعيد إنشاءه بنفس العنوان
            (v.parent as? ViewGroup)?.removeView(v)
            runCatching { v.destroy() }
            tab.webView = null; tab.loading = false
            tab.epoch++
            return true
        }
        override fun onReceivedSslError(v: WebView, h: SslErrorHandler, e: SslError) {
            h.cancel()   // لا نتجاوز أخطاء الشهادات أبداً
            Security.log(L("شهادة"), (L("رُفض اتصال غير موثوق: ") + (e.url?.let { hostOf(it) })))
            val u = e.url ?: v.url ?: ""
            v.loadDataWithBaseURL(u, errorHtml(u, L("شهادة أمان الموقع غير صالحة — تم حظر الاتصال لحمايتك")), "text/html", "UTF-8", u)
        }
        override fun onSafeBrowsingHit(v: WebView, r: WebResourceRequest, threatType: Int, cb: SafeBrowsingResponse) {
            cb.backToSafety(true)
            Security.log("Safe Browsing", (L("حُظر موقع خطير: ") + (r.url.host)))
            toast(ctx, L("تم حظر موقع خطير وإعادتك إلى صفحة آمنة"))
        }
        override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
            if (r.isForMainFrame && tab.upgradedFrom != null && r.url.toString() == tab.upgradedTo) {
                // الموقع لا يدعم HTTPS: رجوع إلى http مرة واحدة
                val orig = tab.upgradedFrom!!
                tab.noUpgradeHost = Uri.parse(orig).host; tab.upgradedFrom = null; tab.upgradedTo = null
                v.loadUrl(orig, Perf.privacyHeaders); return
            }
            if (r.isForMainFrame) {
                val u = r.url.toString()
                v.loadDataWithBaseURL(u, errorHtml(u, e.description.toString()), "text/html", "UTF-8", u)
            }
        }
        override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
            val u = r.url
            return when (u.scheme) {
                "http", "https" -> {
                    if (!r.isForMainFrame) false
                    else {
                        var t = Security.cleanUrl(u)
                        val host = t.host
                        if (Prefs.httpsFirst && t.scheme == "http" && host != null && host != tab.noUpgradeHost) {
                            val last = tab.lastUpgrade; val now = System.currentTimeMillis()
                            if (last != null && last.first == host && now - last.second < 5000) tab.noUpgradeHost = host   // حلقة تحويل
                            else {
                                tab.upgradedFrom = t.toString(); t = t.buildUpon().scheme("https").build()
                                tab.upgradedTo = t.toString(); tab.lastUpgrade = host to now
                                Security.log("HTTPS", (L("رُقّي ") + host))
                            }
                        }
                        if (t != u) { v.loadUrl(t.toString(), Perf.privacyHeaders); true } else false
                    }
                }
                null, "about", "data", "blob" -> false
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
        override fun onProgressChanged(v: WebView, p: Int) {
            val f = p / 100f   // نحدّث الحالة كل 5% فقط لتقليل إعادة التركيب
            if (p == 0 || p == 100 || kotlin.math.abs(f - tab.progress) >= 0.05f) tab.progress = f
        }
        override fun onReceivedTitle(v: WebView, t: String?) { if (!t.isNullOrBlank()) tab.title = t }
        override fun onShowCustomView(view: View, cb: CustomViewCallback) = h.showCustom(view, cb)
        override fun onHideCustomView() = h.hideCustom()
        override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams) = h.chooser(cb, p)
        override fun onPermissionRequest(req: PermissionRequest) = h.permission(req)
        override fun onGeolocationPermissionsShowPrompt(origin: String, cb: GeolocationPermissions.Callback) = h.geo(origin, cb)
        override fun onJsAlert(v: WebView, url: String, msg: String, r: JsResult): Boolean {
            AlertDialog.Builder(ctx).setMessage(msg).setPositiveButton(L("حسناً")) { _, _ -> r.confirm() }
                .setOnCancelListener { r.cancel() }.show()
            return true
        }
        override fun onJsConfirm(v: WebView, url: String, msg: String, r: JsResult): Boolean {
            AlertDialog.Builder(ctx).setMessage(msg).setPositiveButton(L("موافق")) { _, _ -> r.confirm() }
                .setNegativeButton(L("إلغاء")) { _, _ -> r.cancel() }.setOnCancelListener { r.cancel() }.show()
            return true
        }
    }
}

class MainActivity : ComponentActivity() {
    private var dlTrigger by mutableIntStateOf(0)
    @Volatile private var ready = false

    companion object { private var cleanedThisProcess = false }

    // ---- نافذة منبثقة (Picture-in-Picture) ----
    var inPip by mutableStateOf(false)
    var pipAuto = false
    var fullscreenActive = false
    var wvProvider: () -> WebView? = { null }

    private fun pipParams(): android.app.PictureInPictureParams = android.app.PictureInPictureParams.Builder()
        .setAspectRatio(android.util.Rational(16, 9))
        .apply { if (Build.VERSION.SDK_INT >= 31) setAutoEnterEnabled(pipAuto) }
        .build()

    fun refreshPip() { runCatching { setPictureInPictureParams(pipParams()) } }

    fun enterPip() {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) runCatching { enterPictureInPictureMode(pipParams()) }
    }

    // أثناء أي إيقاف مؤقت للـ Activity (زر الرئيسية/المنبثق) نعلم الصفحة أنها في الخلفية قبل أن يتصرف يوتيوب
    override fun onPause() {
        super.onPause()
        if (Prefs.ytBg && YtMedia.owner != null) {
            YtLog.add("native onPause")
            wvProvider()?.evaluateJavascript("window.__novaBg&&window.__novaBg(true)", null)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!inPip) wvProvider()?.evaluateJavascript("window.__novaBg&&window.__novaBg(false)", null)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipAuto && Build.VERSION.SDK_INT < 31) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        wvProvider()?.let { w ->
            if (isInPictureInPictureMode) { w.resumeTimers(); w.onResume() }   // تأكد أن الصفحة غير مجمّدة داخل النافذة المنبثقة
            YtLog.add("native pip=$isInPictureInPictureMode fullscreen=$fullscreenActive")
            val css = if (fullscreenActive) "" else "window.__novaPip&&window.__novaPip($isInPictureInPictureMode);"
            w.evaluateJavascript(css + "window.__novaBg&&window.__novaBg($isInPictureInPictureMode)", null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Prefs.init(this)
        Thread({ Vault.init(applicationContext) }, "nova-vault").start()   // فك التشفير (Keystore) خارج الخيط الرئيسي
        Security.init(this)
        Perf.init(this)
        Thread({ Security.deviceWarnings(applicationContext).forEach { Security.log(L("الجهاز"), it) } }, "nova-sec").start()
        Downloader.init(this)
        val start = intent?.data?.toString() ?: ""
        if (intent?.getBooleanExtra("dl", false) == true) dlTrigger++
        // نُبقي الـ Splash ظاهرة حتى ينتهي تنظيف المؤقت وتُعرض الواجهة
        splash.setKeepOnScreenCondition { !ready }
        if (Prefs.autoClean && !cleanedThisProcess && CacheCleaner.pending(this)) {
            cleanedThisProcess = true
            // التنظيف يجري قبل إنشاء أي WebView كي لا تكون ملفات الكاش مفتوحة
            Thread({
                runCatching { CacheCleaner.cleanLarge(applicationContext) }
                runOnUiThread { if (!isFinishing && !isDestroyed) showUi(start) else ready = true }
            }, "nova-clean").start()
        } else showUi(start)
    }

    private fun showUi(start: String) {
        setContent {
            val dark = when (Prefs.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
                )
            }
            MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = NovaTypography) {
                // اتجاه الواجهة يتبع لغة التطبيق (العربية RTL، الإنجليزية LTR)
                CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides
                    if (I18n.isEnglish()) androidx.compose.ui.unit.LayoutDirection.Ltr else androidx.compose.ui.unit.LayoutDirection.Rtl) {
                    BrowserApp(start, dlTrigger, inPip)
                }
            }
        }
        ready = true
        // تسخين محرك الـ WebView عند أول فراغ، حتى لا يتقطع أول بحث
        android.os.Looper.myQueue().addIdleHandler { Perf.warmUp(applicationContext); runCatching { WebView(applicationContext).destroy() }; false }
    }
    override fun onStop() {
        super.onStop()
        Perf.flushCookies(true)
        // قياس الكاش في الخلفية؛ التنظيف لا يجري إلا عند تجاوز الحد (يحفظ سرعة المواقع وكاش الشيفرة)
        if (Prefs.autoClean && !isChangingConfigurations)
            Thread({ runCatching { CacheCleaner.markIfLarge(applicationContext) } }, "nova-cache").apply { priority = Thread.MIN_PRIORITY }.start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("dl", false)) dlTrigger++
    }
}

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun BrowserApp(startUrl: String, dlTrigger: Int, inPip: Boolean = false) {
    val activity = LocalContext.current as ComponentActivity
    val cs = MaterialTheme.colorScheme
    val prefs = remember { activity.getSharedPreferences("nova", Context.MODE_PRIVATE) }
    val tabs = remember {
        mutableStateListOf<BrowserTab>().apply {
            (if (Prefs.restore) prefs.getString("tabs", "")!! else "").split("\n").filter { it.isNotBlank() }
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
    var showSettings by remember { mutableStateOf(false) }
    var showPasswords by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<PendingSave?>(null) }
    var fillOffer by remember { mutableStateOf<FillOffer?>(null) }
    var ytUrl by remember { mutableStateOf<String?>(null) }
    var askedNotif by remember { mutableStateOf(false) }
    LaunchedEffect(dlTrigger) { if (dlTrigger > 0) showDownloads = true }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) toast(activity, L("فعّل الإشعارات من الإعدادات لمتابعة التنزيل في الخلفية"))
    }
    var customView by remember { mutableStateOf<View?>(null) }
    var customCb by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    val tab = tabs[current.coerceIn(0, tabs.lastIndex)]

    LaunchedEffect(Unit) {
        snapshotFlow { tabs.joinToString("\n") { it.url.ifBlank { "-" } } to current }
            .debounce(700).collect { (s, c) -> prefs.edit().putString("tabs", s).putInt("cur", c).apply() }
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
            settingsMsg = L("تم رفض الإذن بشكل دائم. فعّله من إعدادات التطبيق ليعمل هذا الموقع.")
        cb?.invoke()
    }
    fun askPerms(perms: List<String>, cb: () -> Unit) {
        if (perms.all { granted(it) }) cb()
        else { permCallback = cb; permLauncher.launch(perms.filter { !granted(it) }.toTypedArray()) }
    }

    fun go(t: BrowserTab, input: String) { val u = normalize(input); t.url = u; t.webView?.loadUrl(u, Perf.privacyHeaders) }
    fun newTab() { tabs.add(BrowserTab(nextId++)); current = tabs.lastIndex; showTabs = false; editing = true }
    fun openInNewTab(u: String) { tabs.add(BrowserTab(nextId++, u)); current = tabs.lastIndex }
    fun dispose(t: BrowserTab) {
        YtMedia.tabClosed(t)
        t.webView?.let { w -> (w.parent as? ViewGroup)?.removeView(w); w.destroy() }
        t.webView = null
    }
    fun discard(t: BrowserTab) {   // تحرير الذاكرة مع حفظ الحالة (السجل والتمرير) لاستعادتها لاحقاً
        t.webView?.let { w -> val b = android.os.Bundle(); runCatching { w.saveState(b) }; t.saved = b }
        dispose(t)
    }
    fun closeTab(i: Int) {
        dispose(tabs[i]); tabs.removeAt(i)
        if (tabs.isEmpty()) tabs.add(BrowserTab(nextId++))
        current = current.coerceIn(0, tabs.lastIndex)
    }
    fun home(t: BrowserTab) {
        dispose(t); t.saved = null
        t.url = ""; t.title = L("تبويب جديد"); t.canBack = false; t.canForward = false; t.loading = false; t.finding = false
    }

    LaunchedEffect(Prefs.secureScreen) {
        val f = android.view.WindowManager.LayoutParams.FLAG_SECURE
        if (Prefs.secureScreen) activity.window.setFlags(f, f) else activity.window.clearFlags(f)
    }
    LaunchedEffect(Prefs.textZoom, Prefs.siteDark) { tabs.forEach { t -> t.webView?.let { Perf.applyDisplay(it) } } }
    fun printPage(t: BrowserTab) {
        val w = t.webView ?: return
        val pm = activity.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
        val name = (t.title.ifBlank { "Nova" } + " - " + hostOf(t.url)).take(60)
        runCatching { pm.print(name, w.createPrintDocumentAdapter(name), android.print.PrintAttributes.Builder().build()) }
    }
    fun openCustomTab(t: BrowserTab) {
        val pkg = androidx.browser.customtabs.CustomTabsClient.getPackageName(activity, null)
        if (pkg == null) { toast(activity, L("لا يوجد متصفح يدعم Custom Tabs")); return }
        val ci = androidx.browser.customtabs.CustomTabsIntent.Builder().build()
        ci.intent.setPackage(pkg)
        runCatching { ci.launchUrl(activity, Uri.parse(t.url)) }
    }
    fun translatePage(t: BrowserTab) {
        if (t.url.isBlank()) return
        val target = Prefs.siteLangCode().ifEmpty { I18n.code() }
        go(t, "https://translate.google.com/translate?sl=auto&tl=" + target + "&u=" + Uri.encode(t.url))
        toast(activity, L("سيُرسل عنوان الصفحة إلى Google Translate لترجمتها."))
    }
    LaunchedEffect(Prefs.js) { tabs.forEach { it.webView?.settings?.javaScriptEnabled = Prefs.js } }
    LaunchedEffect(Prefs.pwMode) {
        tabs.forEach { it.webView?.importantForAutofill = if (Prefs.pwMode == 1) View.IMPORTANT_FOR_AUTOFILL_YES else View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS }
        fillOffer = null
    }
    LaunchedEffect(tab.url) { if (fillOffer?.host != Vault.norm(hostOf(tab.url))) fillOffer = null }
    fun clearData() {
        CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        tabs.forEach { t -> t.webView?.let { it.clearCache(true); it.clearHistory(); it.clearFormData() } }
        decisions.clear()
        toast(activity, L("تم مسح بيانات التصفح"))
    }

    fun clearCacheNow() {
        tabs.forEach { it.webView?.clearCache(true) }
        Thread { runCatching { CacheCleaner.clean(activity.applicationContext) } }.start()
        toast(activity, L("تم مسح الذاكرة المؤقتة"))
    }
    // عند ضغط الذاكرة: نحرر الـ WebView للتبويبات الخلفية (تُعاد عند الرجوع لها)
    DisposableEffect(Unit) {
        val cb = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level != ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                    val cur = tabs.getOrNull(current)
                    tabs.toList().forEach { t -> if (t !== cur && t.webView != null) discard(t) }
                }
            }
            override fun onConfigurationChanged(c: android.content.res.Configuration) {}
            @Deprecated("Deprecated in Java") override fun onLowMemory() {}
        }
        activity.registerComponentCallbacks(cb)
        onDispose { activity.unregisterComponentCallbacks(cb) }
    }

    // حدّ أقصى لعدد الـ WebView الحيّة حسب ذاكرة الجهاز؛ الأقدم استخداماً يُحرَّر ويُستعاد عند الرجوع
    val maxLive = remember {
        val am = activity.getSystemService(android.app.ActivityManager::class.java)
        if (am.isLowRamDevice) 2 else if (am.memoryClass >= 256) 5 else 3
    }
    LaunchedEffect(current, tabs.size) {
        val cur = tabs.getOrNull(current)
        cur?.lastUsed = android.os.SystemClock.elapsedRealtime()
        kotlinx.coroutines.delay(1200)   // بعد إنشاء الـ WebView الجديد
        val live = tabs.filter { it.webView != null }
        if (live.size > maxLive)
            live.filter { it !== cur }.sortedBy { it.lastUsed }.take(live.size - maxLive).forEach { discard(it) }
    }
    // عند الخروج من التطبيق: إيقاف مؤقتات الصفحات لتوفير المعالج والبطارية
    DisposableEffect(Unit) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            val w = tabs.getOrNull(current)?.webView
            val inPipNow = (activity as? MainActivity)?.inPip == true
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                val ytLive = Prefs.ytBg && (YtMedia.owner != null || inPipNow)
                if (ytLive) w?.evaluateJavascript("window.__novaBg&&window.__novaBg(true)", null)   // لا نجمّد الصفحة أثناء تشغيل يوتيوب
                else if (Prefs.pauseBg && customView == null) { w?.onPause(); w?.pauseTimers() }
            } else if (e == androidx.lifecycle.Lifecycle.Event.ON_START) {
                w?.resumeTimers(); w?.onResume()
                if (!inPipNow) w?.evaluateJavascript("window.__novaBg&&window.__novaBg(false)", null)
            }
        }
        activity.lifecycle.addObserver(obs)
        onDispose { activity.lifecycle.removeObserver(obs) }
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
                    val label = av.joinToString(L(" و")) { if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) L("الكاميرا") else L("الميكروفون") }
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
                        null -> sitePrompt = SitePrompt(L("السماح بالوصول؟"), ("" + (hostOf(req.origin.toString())) + L(" يريد استخدام ") + label),
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
                        null -> sitePrompt = SitePrompt(L("السماح بالموقع؟"), ("" + (hostOf(origin)) + L(" يريد معرفة موقعك")),
                            { decisions[key] = true; finish(true) }, { decisions[key] = false; finish(false) })
                    }
                }
            },
            openTab = { openInNewTab(it) },
            showCustom = { v, cb -> customView = v; customCb = cb },
            hideCustom = { customView = null; customCb = null },
            onLoginForm = { t, _, host ->
                if (t === tabs.getOrNull(current)) {
                    val cs = Vault.forHost(host)
                    if (cs.isNotEmpty()) fillOffer = FillOffer(t.id, host, cs)
                }
            },
            onCredential = { host, user, pass ->
                if (pass.isNotEmpty() && !Vault.isNever(host)) {
                    val k = Vault.classify(host, user, pass)
                    if (k != SaveKind.SAME) pendingSave = PendingSave(host, user, pass, k)
                }
            },
            onYtState = {
                if (Build.VERSION.SDK_INT >= 33 && !askedNotif && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
                    askedNotif = true; notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onDownload = { u, ua, cd, mime, ref ->
                if (u.startsWith("blob:") || u.startsWith("data:")) toast(activity, L("هذا النوع من التنزيل غير مدعوم بعد"))
                else {
                    val start = {
                        Downloader.start(activity, u, ua, cd, mime, ref)
                        toast(activity, L("بدأ التنزيل — القائمة ⋮ ثم التنزيلات"))
                        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS))
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        Unit
                    }
                    if (Security.isRiskyFile(u, cd)) {
                        Security.log(L("تنزيل"), (L("تحذير ملف تنفيذي من ") + (hostOf(u))))
                        AlertDialog.Builder(activity).setTitle(L("ملف قد يكون خطيراً"))
                            .setMessage((L("هذا النوع من الملفات (تطبيق/ملف تنفيذي) قد يضر بجهازك. نزّله فقط من مصدر تثق به.\n\n") + (hostOf(u))))
                            .setPositiveButton(L("تنزيل")) { _, _ -> start() }.setNegativeButton(L("إلغاء"), null).show()
                    } else start()
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
    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = showPasswords) { showPasswords = false }
    BackHandler(enabled = customView != null) { customCb?.onCustomViewHidden(); customView = null; customCb = null }

    // ربط الـ Activity: مزوّد الـ WebView الحالي + تفعيل الدخول التلقائي للنافذة المنبثقة أثناء تشغيل فيديو يوتيوب
    val mainAct = activity as? MainActivity
    SideEffect {
        mainAct?.wvProvider = { tabs.getOrNull(current)?.webView }
        mainAct?.fullscreenActive = customView != null
        val want = Prefs.autoPip && ((tab.ytPlaying && isYtVideo(tab.url)) || customView != null)
        if (mainAct != null && mainAct.pipAuto != want) { mainAct.pipAuto = want; mainAct.refreshPip() }
    }

    val primaryInt = cs.primary.toArgb()
    val bgInt = cs.surfaceContainerHigh.toArgb()

    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize(), color = cs.background) {
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = if (inPip) 0.dp else 63.dp).background(cs.background)) {
                    AnimatedContent(
                        targetState = tab.id to tab.url.isBlank(),
                        transitionSpec = {
                            if (targetState.second) fadeIn(tween(160)) togetherWith fadeOut(tween(100))
                            else EnterTransition.None togetherWith ExitTransition.None
                        },
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
                            } else key(id, tb.epoch) {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { ctx ->
                                        val wv = tb.webView ?: createWebView(ctx, tb, handlers).also { w ->
                                            tb.webView = w
                                            val sv = tb.saved; tb.saved = null
                                            val restored = sv != null && w.restoreState(sv) != null
                                            if (!restored) w.loadUrl(tb.url, Perf.privacyHeaders)
                                        }
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
                                // التبويب غير الظاهر يُوقَف مؤقتاً (يوفر المعالج والذاكرة) ويُستأنف عند ظهوره
                                DisposableEffect(tb.id, tb.epoch) {
                                    tb.webView?.onResume()
                                    onDispose { tb.webView?.onPause() }
                                }
                            }
                        }
                    }
                }
                if (!inPip) Box(Modifier.align(Alignment.BottomCenter)) {
                if (tab.finding) key(tab.id) { FindBar(tab) } else BottomPill(
                    tab = tab, tabCount = tabs.size, editing = editing, setEditing = { editing = it },
                    onGo = { go(tab, it) }, onTabs = { showTabs = true }, onNewTab = { newTab() }, onHome = { home(tab) },
                    onFind = { tab.findInfo = ""; tab.finding = true },
                    onDesktop = { tab.desktop = !tab.desktop; tab.webView?.let { applyUa(it, tab.desktop); it.reload() } },
                    onShare = { shareText(activity, tab.url) }, onCopy = { copyText(activity, tab.url) },
                    onDownloads = { showDownloads = true }, onSettings = { showSettings = true }, onTranslate = { translatePage(tab) }, onPrint = { printPage(tab) }, onCustomTab = { openCustomTab(tab) },
                    onSwitch = { d -> current = (current + d).coerceIn(0, tabs.lastIndex) }
                )
                }
            }
        }
        AnimatedVisibility(
            visible = showDownloads,
            enter = slideInVertically(tween(280)) { it / 6 } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(220)) { it / 6 } + fadeOut(tween(160))
        ) { DownloadsScreen(onBack = { showDownloads = false }) }
        AnimatedVisibility(
            visible = showSettings,
            enter = slideInVertically(tween(280)) { it / 6 } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(220)) { it / 6 } + fadeOut(tween(160))
        ) { SettingsScreen(onBack = { showSettings = false }, onClearData = { clearData() }, onClearCache = { clearCacheNow() }, onPasswords = { showPasswords = true }) }
        AnimatedVisibility(
            visible = showPasswords,
            enter = slideInVertically(tween(280)) { it / 6 } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(220)) { it / 6 } + fadeOut(tween(160))
        ) { PasswordsScreen(onBack = { showPasswords = false }) }
        fillOffer?.takeIf { it.tabId == tab.id && !inPip && !editing && !showSettings && !showPasswords }?.let { o ->
            FillBanner(
                o, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp),
                onClose = { fillOffer = null },
                onFill = { c ->
                    Auth.run(activity, L("تأكيد الهوية لتعبئة كلمة المرور")) { tab.webView?.let { PasswordBridge.fill(it, c) }; fillOffer = null }
                }
            )
        }
        if (!inPip && customView == null && !editing && !tab.finding && !showSettings && !showPasswords && !showDownloads && isYtVideo(tab.url)) {
            YtBar(
                onDownload = { ytUrl = tab.url }, onPip = { mainAct?.enterPip() },
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(bottom = 76.dp, end = 12.dp)
            )
        }
        customView?.let { v ->
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx -> FrameLayout(ctx).apply { setBackgroundColor(android.graphics.Color.BLACK); (v.parent as? ViewGroup)?.removeView(v); addView(v) } }
                )
                // زر النافذة المنبثقة فوق الفيديو في وضع ملء الشاشة (الأكثر موثوقية)
                if (!inPip) Surface(
                    onClick = { mainAct?.enterPip() }, shape = CircleShape, color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp), tint = Color.White); Spacer(Modifier.width(6.dp))
                        Text(L("منبثق"), style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                }
            }
        }
    }

    sitePrompt?.let { p ->
        AlertDialog(
            onDismissRequest = { sitePrompt = null; p.onDeny() },
            title = { Text(p.title) }, text = { Text(p.message) },
            confirmButton = { TextButton(onClick = { sitePrompt = null; p.onAllow() }) { Text(L("سماح")) } },
            dismissButton = { TextButton(onClick = { sitePrompt = null; p.onDeny() }) { Text(L("رفض")) } }
        )
    }
    pendingSave?.let { p ->
        SavePasswordDialog(
            p,
            onSave = { Vault.upsert("", p.host, p.user, p.pass); pendingSave = null; toast(activity, L("تم حفظ كلمة المرور")) },
            onNever = { Vault.neverSave(p.host); pendingSave = null },
            onDismiss = { pendingSave = null }
        )
    }
    ytUrl?.let { u ->
        YtDownloadSheet(u, onDismiss = { ytUrl = null }, onStarted = {
            if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        })
    }
    settingsMsg?.let { m ->
        AlertDialog(
            onDismissRequest = { settingsMsg = null },
            title = { Text(L("الإذن مطلوب")) }, text = { Text(m) },
            confirmButton = {
                TextButton(onClick = {
                    settingsMsg = null
                    activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)))
                }) { Text(L("فتح الإعدادات")) }
            },
            dismissButton = { TextButton(onClick = { settingsMsg = null }) { Text(L("لاحقاً")) } }
        )
    }

    if (showTabs) {
        ModalBottomSheet(onDismissRequest = { showTabs = false }, containerColor = cs.surface) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(L("التبويبات"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = { newTab() }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(L("جديد")) }
                }
                Spacer(Modifier.height(14.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(tabs, key = { _, t -> t.id }) { i, t ->
                        val sel = i == current
                        Surface(
                            onClick = { current = i; showTabs = false }, shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerHigh,
                            border = BorderStroke(if (sel) 2.dp else 1.dp, if (sel) cs.primary else cs.outlineVariant),
                            modifier = Modifier.height(116.dp).animateItem()
                        ) {
                            Box(Modifier.fillMaxSize().padding(14.dp)) {
                                Column(Modifier.align(Alignment.BottomStart).padding(end = 4.dp)) {
                                    Text(t.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                    Text(if (t.url.isBlank()) L("صفحة البداية") else hostOf(t.url), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                                IconButton(onClick = { closeTab(i) }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
                                    Icon(Icons.Default.Close, L("إغلاق"), Modifier.size(18.dp))
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
    Surface(Modifier.imePadding().fillMaxWidth(), color = cs.background) {
        Row(Modifier.navigationBarsPadding().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = q, onValueChange = { q = it; tab.webView?.findAllAsync(it) }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface), cursorBrush = SolidColor(cs.primary),
                modifier = Modifier.weight(1f).padding(start = 12.dp).focusRequester(fr),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (q.isEmpty()) Text(L("بحث في الصفحة"), color = cs.onSurfaceVariant)
                        inner()
                    }
                }
            )
            Text(tab.findInfo, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            IconButton(onClick = { tab.webView?.findNext(false) }) { Icon(Icons.Default.KeyboardArrowUp, L("السابق")) }
            IconButton(onClick = { tab.webView?.findNext(true) }) { Icon(Icons.Default.KeyboardArrowDown, L("التالي")) }
            IconButton(onClick = { tab.webView?.clearMatches(); tab.finding = false }) { Icon(Icons.Default.Close, L("إغلاق")) }
        }
    }
}

class SitePrompt(val title: String, val message: String, val onAllow: () -> Unit, val onDeny: () -> Unit)

@Composable
fun BottomPill(
    tab: BrowserTab, tabCount: Int, editing: Boolean, setEditing: (Boolean) -> Unit,
    onGo: (String) -> Unit, onTabs: () -> Unit, onNewTab: () -> Unit, onHome: () -> Unit,
    onFind: () -> Unit, onDesktop: () -> Unit, onShare: () -> Unit, onCopy: () -> Unit,
    onDownloads: () -> Unit, onSwitch: (Int) -> Unit, onSettings: () -> Unit, onTranslate: () -> Unit = {},
    onPrint: () -> Unit = {}, onCustomTab: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    val hasPage = tab.url.isNotBlank()

    Surface(Modifier.imePadding().fillMaxWidth(), shape = RectangleShape, color = cs.background) {
        Column(Modifier.navigationBarsPadding()) {
            if (tab.loading && !editing) {
                LinearProgressIndicator(progress = { tab.progress }, modifier = Modifier.fillMaxWidth().height(3.dp), color = cs.tertiary, trackColor = Color.Transparent)
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
                                    if (field.text.isEmpty()) Text(L("ابحث أو اكتب عنوان"), color = cs.onSurfaceVariant)
                                    inner()
                                }
                            }
                        )
                        IconButton(onClick = { field = TextFieldValue("") }) { Icon(Icons.Default.Close, L("مسح")) }
                    } else {
                        RoundBtn(onClick = { if (tab.canBack) tab.webView?.goBack() else onHome() }) {
                            Icon(if (tab.canBack) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Home, if (tab.canBack) L("رجوع") else L("الرئيسية"))
                        }
                        Row(
                            Modifier.weight(1f).height(48.dp).clip(CircleShape).background(cs.surfaceContainerHigh)
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
                            val insecure = tab.url.startsWith("http://")
                            Icon(
                                if (insecure) Icons.Default.Warning else if (tab.url.startsWith("https")) Icons.Default.Lock else Icons.Default.Search,
                                if (insecure) L("اتصال غير مشفّر") else null, Modifier.size(15.dp),
                                tint = if (insecure) cs.error else cs.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (!hasPage) L("ابحث أو اكتب عنوان") else hostOf(tab.url), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium, color = if (hasPage) cs.onSurface else cs.onSurfaceVariant
                            )
                        }
                        RoundBtn(onClick = onTabs) {
                            Box(Modifier.size(24.dp).border(2.dp, cs.onSurface, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                                Text(if (tabCount > 99) "99+" else "$tabCount", maxLines = 1, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        RoundBtn(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, L("المزيد")) }
                        if (menu) MenuSheet(tab, { menu = false }, onNewTab, onFind, onDesktop, onShare, onCopy, onDownloads, onSettings, onHome, onTranslate, onPrint, onCustomTab)
                    }
                }
            }
        }
    }
}

private class Site(val name: String, val url: String, val glyph: String, val color: Long)

@Composable
fun RoundBtn(onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.padding(horizontal = 2.dp).size(48.dp).clip(CircleShape).background(cs.surfaceContainerHigh)
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { CompositionLocalProvider(LocalContentColor provides cs.onSurface) { content() } }
}

@Composable
fun Reveal(shown: Boolean, delay: Int, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(420, delay)) + slideInVertically(tween(420, delay)) { it / 6 }
    ) { content() }
}

fun groupShape(i: Int, n: Int): RoundedCornerShape {
    val big = 28.dp; val small = 6.dp
    val top = if (i == 0) big else small
    val bot = if (i == n - 1) big else small
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bot, bottomEnd = bot)
}

@Composable
fun IconCircle(content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(40.dp).clip(CircleShape).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun ListRow(
    shape: RoundedCornerShape, title: String, sub: String?, onClick: () -> Unit,
    enabled: Boolean = true, trailing: (@Composable () -> Unit)? = null, leading: @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, enabled = enabled, shape = shape, color = cs.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f)
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            leading()
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
        }
    }
}

@Composable
private fun RowScope.QuickTile(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerHigh,
        modifier = Modifier.weight(1f).alpha(if (enabled) 1f else 0.4f)
    ) {
        Column(Modifier.padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(
    tab: BrowserTab, onDismiss: () -> Unit, onNewTab: () -> Unit, onFind: () -> Unit, onDesktop: () -> Unit,
    onShare: () -> Unit, onCopy: () -> Unit, onDownloads: () -> Unit, onSettings: () -> Unit, onHome: () -> Unit, onTranslate: () -> Unit = {},
    onPrint: () -> Unit = {}, onCustomTab: () -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val hasPage = tab.url.isNotBlank()
    fun act(a: () -> Unit) { scope.launch { state.hide() }.invokeOnCompletion { onDismiss(); a() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = cs.background) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickTile(L("التالي"), Icons.AutoMirrored.Filled.ArrowForward, tab.canForward) { act { tab.webView?.goForward() } }
                QuickTile(if (tab.loading) L("إيقاف") else L("تحديث"), if (tab.loading) Icons.Default.Close else Icons.Default.Refresh, hasPage) {
                    act { if (tab.loading) tab.webView?.stopLoading() else tab.webView?.reload() }
                }
                QuickTile(L("مشاركة"), Icons.Default.Share, hasPage) { act(onShare) }
                QuickTile(L("نسخ"), Icons.Default.Edit, hasPage) { act(onCopy) }
            }
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                ListRow(groupShape(0, 6), L("تبويب جديد"), null, { act(onNewTab) }) { IconCircle { Icon(Icons.Default.Add, null) } }
                ListRow(groupShape(1, 6), L("بحث في الصفحة"), null, { act(onFind) }, enabled = hasPage) { IconCircle { Icon(Icons.Default.Search, null) } }
                ListRow(groupShape(2, 6), L("ترجمة الصفحة"), null, { act(onTranslate) }, enabled = hasPage) { IconCircle { Icon(Icons.Default.Share, null) } }
                ListRow(groupShape(3, 6), L("طباعة / حفظ PDF"), null, { act(onPrint) }, enabled = hasPage) { IconCircle { Icon(Icons.Default.Create, null) } }
                ListRow(groupShape(4, 6), L("فتح في Chrome"), null, { act(onCustomTab) }, enabled = hasPage) { IconCircle { Icon(Icons.Default.ExitToApp, null) } }
                ListRow(groupShape(5, 6), L("نسخة سطح المكتب"), null, { onDesktop() }, enabled = hasPage,
                    trailing = { Switch(checked = tab.desktop, onCheckedChange = null) }) { IconCircle { Icon(Icons.Default.Build, null) } }
            }
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                ListRow(groupShape(0, 3), L("التنزيلات"), null, { act(onDownloads) }) { IconCircle { Icon(Icons.Default.KeyboardArrowDown, null) } }
                ListRow(groupShape(1, 3), L("الإعدادات"), null, { act(onSettings) }) { IconCircle { Icon(Icons.Default.Settings, null) } }
                ListRow(groupShape(2, 3), L("الرئيسية"), null, { act(onHome) }) { IconCircle { Icon(Icons.Default.Home, null) } }
            }
        }
    }
}

@Composable
fun StartPage(
    tabsCount: Int, activeDl: Int, onSearchClick: () -> Unit, onOpen: (String) -> Unit,
    onTabs: () -> Unit, onDownloads: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greet = when { hour < 5 -> L("ليلة هادئة"); hour < 12 -> L("صباح الخير"); hour < 18 -> L("طاب يومك"); else -> L("مساء الخير") }
    val date = remember { java.text.SimpleDateFormat(L("EEEE، d MMMM"), java.util.Locale.forLanguageTag(I18n.code())).format(java.util.Date()) }
    val sites = remember {
        listOf(
            Site("Google", "google.com", "G", 0xFF4285F4), Site("YouTube", "youtube.com", "▶", 0xFFFF4D4D),
            Site("Wikipedia", "wikipedia.org", "W", 0xFF8A8F9E), Site("GitHub", "github.com", "</>", 0xFFA78BFA),
            Site("Gmail", "mail.google.com", "✉", 0xFFEA4335), Site(L("الخرائط"), "maps.google.com", "📍", 0xFF34A853)
        )
    }
    val latest = Downloader.tasks.firstOrNull()
    val dlSub = when {
        activeDl > 0 -> ("" + activeDl + L(" قيد التنزيل"))
        latest != null -> latest.name
        else -> L("لا توجد تنزيلات بعد")
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 24.dp)) {
        Reveal(shown, 0) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = cs.surfaceContainerHigh) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, Modifier.size(18.dp), tint = cs.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text("Nova", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.weight(1f))
                RoundBtn(onClick = onTabs) {
                    Box(Modifier.size(22.dp).border(2.dp, cs.onSurface, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                        Text("$tabsCount", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
        Reveal(shown, 60) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                Text(greet, style = MaterialTheme.typography.displaySmall)
                Text(date, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(26.dp))
        Reveal(shown, 120) {
            Surface(onClick = onSearchClick, shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Text(L("ابحث في الويب أو اكتب رابطاً"), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(28.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = cs.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(42.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = cs.onPrimary)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Reveal(shown, 180) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                ListRow(groupShape(0, 2), L("التنزيلات"), dlSub, onDownloads) { IconCircle { Icon(Icons.Default.KeyboardArrowDown, null) } }
                ListRow(groupShape(1, 2), L("التبويبات"), ("" + tabsCount + L(" مفتوحة")), onTabs) { IconCircle { Icon(Icons.Default.Menu, null) } }
            }
        }
        Spacer(Modifier.height(24.dp))
        Reveal(shown, 240) {
            Column {
                Text(L("وصول سريع"), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    sites.forEachIndexed { i, st ->
                        ListRow(groupShape(i, sites.size), st.name, st.url, { onOpen(st.url) }) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(st.color).copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                                Text(st.glyph, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(st.color))
                            }
                        }
                    }
                }
            }
        }
    }
}
