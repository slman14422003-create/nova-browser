package com.nova.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder

private val LightColors = lightColorScheme(
    primary = Color(0xFF3D5AFE), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE2FF), onPrimaryContainer = Color(0xFF001258),
    background = Color(0xFFF1F3FA), onBackground = Color(0xFF191B23),
    surface = Color(0xFFF1F3FA), onSurface = Color(0xFF191B23),
    onSurfaceVariant = Color(0xFF5A5E72),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFECEEF7),
    surfaceContainerHighest = Color(0xFFE4E7F2)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB0FF), onPrimary = Color(0xFF0A1A6B),
    primaryContainer = Color(0xFF26346F), onPrimaryContainer = Color(0xFFDDE2FF),
    background = Color(0xFF0E1015), onBackground = Color(0xFFE5E6EE),
    surface = Color(0xFF0E1015), onSurface = Color(0xFFE5E6EE),
    onSurfaceVariant = Color(0xFF9EA2B5),
    surfaceContainer = Color(0xFF1A1D26), surfaceContainerHigh = Color(0xFF232733),
    surfaceContainerHighest = Color(0xFF2B3040)
)

class BrowserTab(val id: Int, startUrl: String = "") {
    var url by mutableStateOf(startUrl)
    var title by mutableStateOf("تبويب جديد")
    var progress by mutableFloatStateOf(0f)
    var loading by mutableStateOf(false)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var webView: WebView? = null
}

fun normalize(input: String): String {
    val t = input.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") -> t
        t.contains(".") && !t.contains(" ") -> "https://$t"
        else -> "https://www.google.com/search?q=" + URLEncoder.encode(t, "UTF-8")
    }
}

fun hostOf(u: String): String =
    runCatching { java.net.URI(u).host?.removePrefix("www.") }.getOrNull() ?: u

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val start = intent?.data?.toString() ?: ""
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
                BrowserApp(start)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserApp(startUrl: String) {
    var nextId by remember { mutableIntStateOf(1) }
    val tabs = remember { mutableStateListOf(BrowserTab(0, startUrl)) }
    var current by remember { mutableIntStateOf(0) }
    var showTabs by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val tab = tabs[current.coerceIn(0, tabs.lastIndex)]
    val cs = MaterialTheme.colorScheme

    fun go(t: BrowserTab, input: String) {
        val u = normalize(input)
        t.url = u
        t.webView?.loadUrl(u)
    }
    fun newTab() { tabs.add(BrowserTab(nextId++)); current = tabs.lastIndex; showTabs = false; editing = true }
    fun closeTab(i: Int) {
        tabs[i].webView?.destroy()
        tabs.removeAt(i)
        if (tabs.isEmpty()) tabs.add(BrowserTab(nextId++))
        current = current.coerceIn(0, tabs.lastIndex)
    }
    fun home(t: BrowserTab) {
        t.webView?.destroy(); t.webView = null
        t.url = ""; t.title = "تبويب جديد"; t.canBack = false; t.canForward = false; t.loading = false
    }

    BackHandler(enabled = tab.canBack) { tab.webView?.goBack() }
    BackHandler(enabled = editing) { editing = false }

    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp)) {
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer)
            ) {
                if (tab.url.isBlank()) {
                    StartPage(onSearchClick = { editing = true }, onOpen = { go(tab, it) })
                } else {
                    key(tab.id) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                tab.webView ?: WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(v: WebView, u: String, f: Bitmap?) {
                                            tab.loading = true; tab.url = u
                                        }
                                        override fun onPageFinished(v: WebView, u: String) {
                                            tab.loading = false; tab.url = u
                                            tab.canBack = v.canGoBack(); tab.canForward = v.canGoForward()
                                        }
                                        override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) =
                                            r.url.scheme?.startsWith("http") != true
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(v: WebView, p: Int) { tab.progress = p / 100f }
                                        override fun onReceivedTitle(v: WebView, t: String?) { if (!t.isNullOrBlank()) tab.title = t }
                                    }
                                    tab.webView = this
                                    loadUrl(tab.url)
                                }
                            }
                        )
                    }
                }
            }
            BottomPill(
                tab = tab, tabCount = tabs.size, editing = editing, setEditing = { editing = it },
                onGo = { go(tab, it) }, onTabs = { showTabs = true },
                onNewTab = { newTab() }, onHome = { home(tab) }
            )
        }
    }

    if (showTabs) {
        ModalBottomSheet(onDismissRequest = { showTabs = false }, containerColor = cs.surface) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("التبويبات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = { newTab() }) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("جديد")
                    }
                }
                Spacer(Modifier.height(14.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(tabs, key = { _, t -> t.id }) { i, t ->
                        val sel = i == current
                        Surface(
                            onClick = { current = i; showTabs = false },
                            shape = RoundedCornerShape(22.dp),
                            color = cs.surfaceContainer,
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
fun BottomPill(
    tab: BrowserTab, tabCount: Int, editing: Boolean, setEditing: (Boolean) -> Unit,
    onGo: (String) -> Unit, onTabs: () -> Unit, onNewTab: () -> Unit, onHome: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    var menu by remember { mutableStateOf(false) }
    val p by animateFloatAsState(tab.progress, label = "progress")

    Surface(
        modifier = Modifier.navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
        shape = RoundedCornerShape(32.dp), color = cs.surfaceContainer,
        shadowElevation = 10.dp, tonalElevation = 2.dp
    ) {
        Column(Modifier.animateContentSize()) {
            if (tab.loading && !editing) {
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(3.dp), trackColor = Color.Transparent)
            } else Spacer(Modifier.height(3.dp))

            Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (editing) {
                    var field by remember { mutableStateOf(TextFieldValue(tab.url, TextRange(0, tab.url.length))) }
                    val fr = remember { FocusRequester() }
                    var got by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { fr.requestFocus() }
                    Icon(Icons.Default.Search, null, Modifier.padding(start = 10.dp), tint = cs.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = field, onValueChange = { field = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
                        cursorBrush = SolidColor(cs.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            if (field.text.isNotBlank()) onGo(field.text)
                            setEditing(false); focus.clearFocus()
                        }),
                        modifier = Modifier.weight(1f).focusRequester(fr).onFocusChanged {
                            if (it.isFocused) got = true else if (got) setEditing(false)
                        },
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
                        val secure = tab.url.startsWith("https")
                        Icon(if (secure) Icons.Default.Lock else Icons.Default.Search, null, Modifier.size(15.dp), tint = cs.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (tab.url.isBlank()) "ابحث أو اكتب عنوان" else hostOf(tab.url),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (tab.url.isBlank()) cs.onSurfaceVariant else cs.onSurface
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
                            DropdownMenuItem(text = { Text("تبويب جديد") }, leadingIcon = { Icon(Icons.Default.Add, null) },
                                onClick = { menu = false; onNewTab() })
                            DropdownMenuItem(text = { Text("التالي") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                                enabled = tab.canForward, onClick = { menu = false; tab.webView?.goForward() })
                            DropdownMenuItem(text = { Text(if (tab.loading) "إيقاف" else "تحديث") },
                                leadingIcon = { Icon(if (tab.loading) Icons.Default.Close else Icons.Default.Refresh, null) },
                                enabled = tab.url.isNotBlank(),
                                onClick = { menu = false; if (tab.loading) tab.webView?.stopLoading() else tab.webView?.reload() })
                            DropdownMenuItem(text = { Text("الرئيسية") }, leadingIcon = { Icon(Icons.Default.Home, null) },
                                onClick = { menu = false; onHome() })
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
        Triple("Google", "google.com", Color(0xFF4285F4)),
        Triple("YouTube", "youtube.com", Color(0xFFFF4D4D)),
        Triple("Wikipedia", "wikipedia.org", Color(0xFF8A8F9E)),
        Triple("GitHub", "github.com", Color(0xFF8B5CF6))
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(cs.primaryContainer.copy(alpha = 0.6f), cs.surfaceContainer)))) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Nova", fontSize = 58.sp, fontWeight = FontWeight.Bold, color = cs.primary)
            Text("تصفح أنعم وأسرع", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(36.dp))
            Surface(
                onClick = onSearchClick, shape = CircleShape, color = cs.surfaceContainer, shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = cs.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text("ابحث أو اكتب عنوان", color = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(36.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                sites.forEach { (name, url, c) ->
                    Column(
                        Modifier.clip(RoundedCornerShape(18.dp)).clickable { onOpen(url) }.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
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
