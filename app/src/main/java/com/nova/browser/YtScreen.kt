package com.nova.browser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * الواجهة الأصلية ليوتيوب: الرئيسية/البحث/الاشتراكات/المكتبة كقوائم أصلية، وصفحة مشاهدة يعلوها مشغّل الصفحة الحيّ.
 * البيانات من DOM الصفحة (yt-app.js). غير المدعوم (Shorts وغيرها) يبقى بعرض الموقع.
 */
@Composable
fun YtScreen(tab: BrowserTab, onShowSite: () -> Unit, onDownload: () -> Unit, onPip: () -> Unit) {
    val s = tab.yt
    val page = YtApp.pageOf(tab.url)
    val fresh = s.key == YtApp.urlKey(tab.url)
    if (page == "watch") YtWatchView(tab, fresh, onShowSite, onDownload, onPip)
    else YtListView(tab, page, fresh, onShowSite)
    s.diag?.let { d ->
        val cs = MaterialTheme.colorScheme
        val ctx = LocalContext.current
        AlertDialog(
            onDismissRequest = { s.diag = null }, shape = RoundedCornerShape(24.dp), containerColor = cs.surfaceContainerHigh,
            title = { Text(L("تشخيص الاتصال بالموقع")) },
            text = { Text(d, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp, color = cs.onSurface) },
            confirmButton = { TextButton(onClick = { copyText(ctx, d); s.diag = null }) { Text(L("نسخ"), color = cs.tertiary) } },
            dismissButton = { TextButton(onClick = { s.diag = null }) { Text(L("إغلاق"), color = cs.onSurfaceVariant) } }
        )
    }
}

// ───────────────────────── القوائم (الرئيسية/بحث/اشتراكات/مكتبة…) ─────────────────────────

@Composable
private fun YtListView(tab: BrowserTab, page: String, fresh: Boolean, onShowSite: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val s = tab.yt
    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(Modifier.fillMaxSize()) {
            if (s.searching) YtSearchBar(tab) else YtTopBar(tab, page, onShowSite)
            if (s.searching) {
                YtRecent(tab)
            } else {
                if (page == "home" && fresh && s.chips.isNotEmpty()) YtChips(tab)
                Box(Modifier.weight(1f).fillMaxWidth()) { YtFeed(tab, fresh, onShowSite) }
                YtNav(tab, page)
            }
        }
    }
}

@Composable
private fun YtTopBar(tab: BrowserTab, page: String, onShowSite: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val s = tab.yt
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(cs.background)) {
        Row(
            Modifier.fillMaxWidth().height(UiLayout.BAR_ROW_DP.dp).background(siteWash(cs)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (page == "home") {
                YtLogo(Modifier.size(width = 30.dp, height = 21.dp))
                Text("YouTube", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.onSurface, modifier = Modifier.weight(1f))
            } else {
                GlyphButton(L("رجوع"), { tab.webView?.goBack() }, tonal = false) { c -> BackGlyph(c, Modifier.size(20.dp)) }
                Text(
                    when (page) {
                        "search" -> s.recent.firstOrNull() ?: L("نتائج البحث")
                        "subs" -> L("الاشتراكات")
                        "library" -> L("أنت")
                        "channel" -> L("القناة")
                        "playlist" -> L("قائمة التشغيل")
                        else -> "YouTube"
                    },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.onSurface, maxLines = 1, modifier = Modifier.weight(1f)
                )
            }
            GlyphButton(L("بحث"), { s.query = ""; s.searching = true }) { c -> Icon(Icons.Default.Search, null, Modifier.size(20.dp), tint = c) }
            GlyphButton(L("عرض الموقع"), onShowSite, tonal = false) { c -> YtSiteGlyph(c, Modifier.size(19.dp)) }
            Box {
                GlyphButton(L("المزيد"), { menu = true }, tonal = false) { c -> Icon(Icons.Default.MoreVert, null, Modifier.size(20.dp), tint = c) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(L("إعادة تحميل الموقع")) }, onClick = { menu = false; tab.webView?.reload() })
                    DropdownMenuItem(text = { Text(L("تشخيص الاتصال بالموقع")) }, onClick = { menu = false; YtApp.diagnose(tab) })
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant.copy(alpha = 0.7f)))
    }
}

@Composable
private fun YtSearchBar(tab: BrowserTab) {
    val cs = MaterialTheme.colorScheme
    val s = tab.yt
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun submit() {
        val q = s.query.trim()
        if (q.isEmpty()) return
        s.recent.remove(q); s.recent.add(0, q); while (s.recent.size > 8) s.recent.removeAt(s.recent.lastIndex)
        s.searching = false
        YtApp.search(tab, q)
    }
    Row(
        Modifier.fillMaxWidth().height(UiLayout.BAR_ROW_DP.dp).background(cs.background).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GlyphButton(L("رجوع"), { s.searching = false }, tonal = false) { c -> BackGlyph(c, Modifier.size(20.dp)) }
        Surface(shape = CircleShape, color = cs.surfaceContainerHigh, modifier = Modifier.weight(1f).height(40.dp)) {
            Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = s.query, onValueChange = { s.query = it }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface, textDirection = TextDirection.Content),
                    cursorBrush = SolidColor(cs.tertiary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    decorationBox = { inner ->
                        Box {
                            if (s.query.isEmpty()) Text(L("ابحث في يوتيوب"), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant.copy(alpha = 0.8f))
                            inner()
                        }
                    }
                )
            }
        }
        GlyphButton(L("بحث"), { submit() }) { c -> Icon(Icons.Default.Search, null, Modifier.size(20.dp), tint = c) }
    }
}

@Composable
private fun YtRecent(tab: BrowserTab) {
    val cs = MaterialTheme.colorScheme
    val s = tab.yt
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(s.recent.toList()) { _, q ->
            Row(
                Modifier.fillMaxWidth().clickable { s.query = q; s.searching = false; YtApp.search(tab, q) }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                Text(q, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun YtChips(tab: BrowserTab) {
    val cs = MaterialTheme.colorScheme
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(tab.yt.chips) { i, c ->
            Surface(
                shape = RoundedCornerShape(10.dp), color = if (c.second) cs.onSurface else cs.secondaryContainer,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { YtApp.chip(tab, i) }
            ) {
                Text(
                    c.first, Modifier.padding(horizontal = 14.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    color = if (c.second) cs.surface else cs.onSecondaryContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YtFeed(tab: BrowserTab, fresh: Boolean, onShowSite: () -> Unit) {
    val s = tab.yt
    val list = if (fresh) s.items else emptyList()
    val state = rememberLazyListState()
    var waited by remember(tab.url) { mutableStateOf(false) }
    var retry by remember(tab.url) { mutableIntStateOf(0) }
    LaunchedEffect(tab.url, retry) { waited = false; delay(9000); waited = true }
    val nearEnd by remember { derivedStateOf { val li = state.layoutInfo; li.totalItemsCount > 0 && (li.visibleItemsInfo.lastOrNull()?.index ?: 0) >= li.totalItemsCount - 4 } }
    LaunchedEffect(nearEnd, list.size) { if (nearEnd) { delay(250); YtApp.more(tab) } }

    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) { if (refreshing) { delay(1500); refreshing = false } }

    when {
        list.isEmpty() && !waited -> YtSkeleton()
        list.isEmpty() -> YtEmpty(onShowSite, onRetry = { retry++; YtApp.refresh(tab) })
        else -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; YtApp.refresh(tab) }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp)) {
                itemsIndexed(list, key = { _, v -> v.id }) { _, v -> VideoCard(v, onClick = { YtApp.open(tab, v) }) }
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun YtNav(tab: BrowserTab, page: String) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(cs.surfaceContainer)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant.copy(alpha = 0.6f)))
        Row(Modifier.fillMaxWidth().height(54.dp)) {
            NavItem(Icons.Default.Home, L("الرئيسية"), page == "home") { YtApp.go(tab, "/") }
            NavItem(Icons.Default.PlayArrow, "Shorts", false) { YtApp.go(tab, "/shorts") }
            NavItem(Icons.Default.Menu, L("الاشتراكات"), page == "subs") { YtApp.go(tab, "/feed/subscriptions") }
            NavItem(Icons.Default.Person, L("أنت"), page == "library") { YtApp.go(tab, "/feed/library") }
        }
    }
}

@Composable
private fun RowScope.NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val c = if (selected) cs.tertiary else cs.onSurfaceVariant
    Column(
        Modifier.weight(1f).fillMaxHeight().clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = c)
        Text(label, fontSize = 11.sp, color = c, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
private fun YtSkeleton() {
    val cs = MaterialTheme.colorScheme
    val t = rememberInfiniteTransition(label = "sk")
    val a by t.animateFloat(0.35f, 0.7f, infiniteRepeatable(androidx.compose.animation.core.tween(800), RepeatMode.Reverse), label = "a")
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(3) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHigh.copy(alpha = a)))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(cs.surfaceContainerHigh.copy(alpha = a)))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.fillMaxWidth(0.85f).height(12.dp).clip(CircleShape).background(cs.surfaceContainerHigh.copy(alpha = a)))
                        Box(Modifier.fillMaxWidth(0.5f).height(10.dp).clip(CircleShape).background(cs.surfaceContainerHigh.copy(alpha = a)))
                    }
                }
            }
        }
    }
}

@Composable
private fun YtEmpty(onShowSite: () -> Unit, onRetry: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(22.dp), color = cs.surfaceContainer) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(L("لم تصل أي فيديوهات من الصفحة"), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Text(L("قد تكون الصفحة ما زالت تحمّل أو غيّر يوتيوب تصميمه. افتح الموقع أو جرّب خيار التشخيص."), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRetry, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = cs.tertiary, contentColor = cs.onTertiary)) {
                        Text(L("إعادة المحاولة"), fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(onClick = onShowSite, shape = CircleShape) { Text(L("فتح الموقع"), fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

// ───────────────────────── بطاقة الفيديو ─────────────────────────

@Composable
fun VideoCard(v: YtVideo, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 18.dp)) {
        Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))) {
            NetImage("https://i.ytimg.com/vi/${v.id}/hqdefault.jpg", Modifier.fillMaxSize())
            if (v.dur.isNotEmpty()) Text(
                v.dur, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color(0xCC000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Row(Modifier.padding(start = 12.dp, end = 2.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChannelAvatar(v.channel, 36, v.avatar)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    v.title, style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Content), color = cs.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                val sub = listOf(v.channel, v.meta).filter { it.isNotBlank() }.joinToString(" • ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box {
                Box(Modifier.size(36.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MoreVert, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(L("مشاركة")) }, onClick = { menu = false; shareText(ctx, v.url) })
                    DropdownMenuItem(text = { Text(L("نسخ الرابط")) }, onClick = { menu = false; copyText(ctx, v.url) })
                }
            }
        }
    }
}

@Composable
fun ChannelAvatar(name: String, size: Int, url: String = "") {
    val initial = name.trim().trimStart('@').take(1).uppercase().ifEmpty { "Y" }
    val hue = (name.hashCode().let { if (it < 0) -it else it } % 360).toFloat()
    val bg = Color.hsv(hue, 0.45f, 0.55f)
    Box(Modifier.size(size.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size * 0.42f).sp)
        // الصورة الحقيقية فوق الحرف؛ إن فشل التحميل يبقى الحرف ظاهراً
        if (url.startsWith("https://")) NetImage(url, Modifier.fillMaxSize(), showBg = false)
    }
}

private val thumbs = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

private fun fetchBitmap(url: String): Bitmap? = runCatching {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 8000; c.readTimeout = 8000
    c.inputStream.use { BitmapFactory.decodeStream(it) }
}.getOrNull()?.also { thumbs.put(url, it) }

/** صورة من الشبكة بذاكرة مؤقتة صغيرة (بلا مكتبات). القص المركزي يزيل الشريطين الأسودين من hqdefault فيصبح 16:9 دقيقاً. */
@Composable
fun NetImage(url: String, modifier: Modifier, showBg: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    var bmp by remember(url) { mutableStateOf(thumbs.get(url)) }
    LaunchedEffect(url) { if (bmp == null) bmp = withContext(Dispatchers.IO) { fetchBitmap(url) } }
    Box(if (showBg) modifier.background(cs.surfaceContainerHigh) else modifier) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

// ───────────────────────── صفحة المشاهدة ─────────────────────────

@Composable
private fun YtWatchView(tab: BrowserTab, fresh: Boolean, onShowSite: () -> Unit, onDownload: () -> Unit, onPip: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val s = tab.yt
    val w = if (fresh) s.watch else null
    val list = if (fresh) s.items else emptyList()
    val state = rememberLazyListState()
    var descOpen by remember(tab.url) { mutableStateOf(false) }
    val nearEnd by remember { derivedStateOf { val li = state.layoutInfo; li.totalItemsCount > 0 && (li.visibleItemsInfo.lastOrNull()?.index ?: 0) >= li.totalItemsCount - 4 } }
    LaunchedEffect(nearEnd, list.size) { if (nearEnd) { delay(250); YtApp.more(tab) } }
    val link = "https://youtu.be/" + (w?.id ?: YtApp.urlKey(tab.url).substringAfter('?'))

    Column(Modifier.fillMaxSize()) {
        // ثقب بنسبة 16:9: يظهر منه المشغّل الحيّ للصفحة (خلف الواجهة). لا يعالج اللمس فيصل للمشغّل.
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Surface(Modifier.weight(1f).fillMaxWidth(), color = cs.background) {
            if (s.showComments) YtComments(tab, onShowSite)
            else LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                item {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            w?.title?.ifBlank { null } ?: tab.title, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
                            fontWeight = FontWeight.Bold, color = cs.onSurface, maxLines = 3, overflow = TextOverflow.Ellipsis
                        )
                        if (!w?.info.isNullOrBlank()) Text(w!!.info, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    }
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionPill(Icons.Default.ThumbUp, w?.likes?.ifBlank { null } ?: L("أعجبني"), active = w?.liked == true) { YtApp.like(tab) }
                        ActionPill(Icons.Default.Share, L("مشاركة")) { shareText(ctx, link) }
                        ActionPill(null, L("تنزيل")) { onDownload() }
                        ActionPill(null, L("منبثق")) { onPip() }
                        ActionPill(null, L("عرض الموقع")) { onShowSite() }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ChannelAvatar(w?.channel ?: "", 40, w?.avatar ?: "")
                        Column(Modifier.weight(1f)) {
                            Text(w?.channel?.ifBlank { null } ?: "…", style = MaterialTheme.typography.titleSmall, color = cs.onSurface, maxLines = 1)
                            if (!w?.subs.isNullOrBlank()) Text(w!!.subs, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1)
                        }
                        val sub = w?.subscribed == true
                        Button(
                            onClick = { YtApp.subscribe(tab) }, shape = CircleShape,
                            colors = if (sub) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors(containerColor = cs.onSurface, contentColor = cs.surface)
                        ) { Text(if (sub) L("مشترك") else L("اشتراك"), fontWeight = FontWeight.SemiBold) }
                    }
                }
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp), color = cs.surfaceContainer,
                        modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                            if (w?.desc.isNullOrBlank()) YtApp.expand(tab)
                            descOpen = !descOpen
                        }
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(L("الوصف"), style = MaterialTheme.typography.labelLarge, color = cs.tertiary)
                            Text(
                                w?.desc?.ifBlank { null } ?: L("اضغط لعرض الوصف"),
                                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content), color = cs.onSurface,
                                maxLines = if (descOpen) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp), color = cs.surfaceContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { YtApp.openComments(tab) }
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(w?.commentsLabel?.ifBlank { null } ?: L("التعليقات"), style = MaterialTheme.typography.labelLarge, color = cs.tertiary)
                            if (!w?.commentPreview.isNullOrBlank()) Text(
                                w!!.commentPreview, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                                color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (list.isNotEmpty()) item {
                    Text(L("فيديوهات مقترحة"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = cs.onSurface, modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 10.dp))
                }
                itemsIndexed(list, key = { _, v -> v.id }) { _, v -> VideoCard(v, onClick = { YtApp.open(tab, v) }) }
                if (list.isEmpty()) item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = cs.tertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPill(icon: ImageVector?, label: String, active: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape, color = if (active) cs.tertiary.copy(alpha = 0.2f) else cs.secondaryContainer,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = if (active) cs.tertiary else cs.onSecondaryContainer)
            Text(label, style = MaterialTheme.typography.labelLarge, color = if (active) cs.tertiary else cs.onSecondaryContainer, maxLines = 1)
        }
    }
}

@Composable
private fun YtComments(tab: BrowserTab, onShowSite: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val s = tab.yt
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(6000); waited = true }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphButton(L("رجوع"), { YtApp.closeComments(tab) }, tonal = false) { c -> BackGlyph(c, Modifier.size(20.dp)) }
            Text(s.watch?.commentsLabel?.ifBlank { null } ?: L("التعليقات"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = cs.onSurface)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant.copy(alpha = 0.6f)))
        when {
            s.comments.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                itemsIndexed(s.comments) { _, c ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ChannelAvatar(c.author, 34)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(listOf(c.author, c.time).filter { it.isNotBlank() }.joinToString(" • "), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1)
                            Text(c.text, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content), color = cs.onSurface)
                            if (c.likes.isNotBlank()) Text(c.likes, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
            !waited -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = cs.tertiary) }
            else -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(L("لم تُحمَّل التعليقات من الصفحة"), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Button(onClick = onShowSite, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = cs.tertiary, contentColor = cs.onTertiary)) { Text(L("فتح الموقع")) }
            }
        }
    }
}

// ───────────────────────── رموز مرسومة ─────────────────────────

@Composable
private fun YtLogo(modifier: Modifier) = Canvas(modifier) {
    drawRoundRect(Color(0xFFFF0033), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height * 0.3f))
    val p = Path().apply {
        moveTo(size.width * 0.4f, size.height * 0.28f); lineTo(size.width * 0.4f, size.height * 0.72f); lineTo(size.width * 0.68f, size.height * 0.5f); close()
    }
    drawPath(p, Color.White)
}

@Composable
private fun BackGlyph(color: Color, modifier: Modifier) = Canvas(modifier) {
    val w = size.width; val h = size.height; val sw = w * 0.11f
    val p = Path().apply { moveTo(w * 0.62f, h * 0.2f); lineTo(w * 0.32f, h * 0.5f); lineTo(w * 0.62f, h * 0.8f) }
    drawPath(p, color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun YtSiteGlyph(color: Color, modifier: Modifier) = Canvas(modifier) {
    val w = size.width; val h = size.height; val sw = w * 0.09f
    drawRoundRect(color, Offset(w * 0.12f, h * 0.18f), androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.64f), androidx.compose.ui.geometry.CornerRadius(w * 0.12f), style = Stroke(width = sw))
    drawLine(color, Offset(w * 0.12f, h * 0.38f), Offset(w * 0.88f, h * 0.38f), sw, StrokeCap.Round)
}
