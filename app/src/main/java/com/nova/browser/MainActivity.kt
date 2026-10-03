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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val start = intent?.data?.toString() ?: ""
        setContent { NovaTheme { BrowserApp(start) } }
    }
}

@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx),
        content = content
    )
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserApp(startUrl: String) {
    var nextId by remember { mutableIntStateOf(1) }
    val tabs = remember { mutableStateListOf(BrowserTab(0, startUrl)) }
    var current by remember { mutableIntStateOf(0) }
    var showTabs by remember { mutableStateOf(false) }
    val tab = tabs[current.coerceIn(0, tabs.lastIndex)]

    fun go(t: BrowserTab, input: String) {
        val u = normalize(input)
        t.url = u
        t.webView?.loadUrl(u)
    }
    fun newTab() { tabs.add(BrowserTab(nextId++)); current = tabs.lastIndex; showTabs = false }
    fun closeTab(i: Int) {
        tabs[i].webView?.destroy()
        tabs.removeAt(i)
        if (tabs.isEmpty()) tabs.add(BrowserTab(nextId++))
        current = current.coerceIn(0, tabs.lastIndex)
    }

    BackHandler(enabled = tab.canBack) { tab.webView?.goBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            BottomBar(
                tab = tab, tabCount = tabs.size,
                onGo = { go(tab, it) },
                onTabs = { showTabs = true }
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (tab.url.isBlank()) {
                StartPage(onSearch = { go(tab, it) })
            } else {
                key(tab.id) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            tab.webView ?: WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.setSupportZoom(true)
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
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
            val p by animateFloatAsState(tab.progress, label = "progress")
            AnimatedVisibility(visible = tab.loading, modifier = Modifier.align(Alignment.TopCenter)) {
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(3.dp))
            }
        }
    }

    if (showTabs) {
        ModalBottomSheet(onDismissRequest = { showTabs = false }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("التبويبات (${tabs.size})", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = { newTab() }) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("جديد")
                    }
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(tabs, key = { _, t -> t.id }) { i, t ->
                        val sel = i == current
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                                .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { current = i; showTabs = false }
                                .padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                Text(t.url.ifBlank { "صفحة البداية" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { closeTab(i) }) { Icon(Icons.Default.Close, "إغلاق") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomBar(tab: BrowserTab, tabCount: Int, onGo: (String) -> Unit, onTabs: () -> Unit) {
    val focus = LocalFocusManager.current
    var text by remember(tab.id, tab.url) { mutableStateOf(tab.url) }
    var focused by remember { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
            TextField(
                value = text, onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("ابحث أو اكتب عنوان موقع") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (text.isNotBlank()) onGo(text); focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { tab.webView?.goBack() }, enabled = tab.canBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                IconButton(onClick = { tab.webView?.goForward() }, enabled = tab.canForward) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "تقدم") }
                IconButton(onClick = { tab.url = ""; tab.canBack = false; tab.canForward = false; tab.webView = null }) { Icon(Icons.Default.Home, "الرئيسية") }
                IconButton(onClick = { if (tab.loading) tab.webView?.stopLoading() else tab.webView?.reload() }) {
                    Icon(if (tab.loading) Icons.Default.Close else Icons.Default.Refresh, "تحديث")
                }
                Box(
                    Modifier.size(30.dp).border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp)).clickable { onTabs() },
                    contentAlignment = Alignment.Center
                ) { Text("$tabCount", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

@Composable
fun StartPage(onSearch: (String) -> Unit) {
    val sites = listOf("Google" to "google.com", "YouTube" to "youtube.com", "Wikipedia" to "wikipedia.org", "GitHub" to "github.com")
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Nova", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        Text("تصفح أسرع وأنعم", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            sites.forEach { (name, url) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onSearch(url) }) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) { Text(name.take(1), style = MaterialTheme.typography.titleLarge) }
                    Spacer(Modifier.height(6.dp))
                    Text(name, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
