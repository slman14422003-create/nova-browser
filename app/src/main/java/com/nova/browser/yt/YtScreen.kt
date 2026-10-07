package com.nova.browser

import android.graphics.Bitmap
import android.net.Uri
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
 * البيانات من DOM الصفحة (yt-all.js). غير المدعوم (Shorts وغيرها) يبقى بعرض الموقع.
 */
@Composable
fun YtScreen(tab: BrowserTab, onShowSite: () -> Unit, onDownload: () -> Unit, onPip: () -> Unit) {
    val s = tab.yt
    val page = YtApp.pageOf(tab.url)
    val fresh = s.key == YtApp.urlKey(tab.url)
    if (page == "watch") YtWatchView(tab, fresh, onShowSite, onDownload, onPip)
    else YtListView(tab, page, fresh, onShowSite)
    if (s.psOpen && page == "watch") YtPlayerSheet(tab)
    if (s.saveOpen && page == "watch") YtSaveSheet(tab, onShowSite)
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
                if (page == "playlist" && fresh && s.items.isNotEmpty()) YtPlaylistHeader(tab)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (page == "library") YtYou(tab, fresh, onShowSite) else YtFeed(tab, fresh, onShowSite)
                }
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
                        "history" -> L("السجل")
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
    val plId = if (YtApp.pageOf(tab.url) == "playlist") Uri.parse(tab.url).getQueryParameter("list") else null
    val state = rememberLazyListState()
    var waited by remember(tab.url) { mutableStateOf(false) }
    var retry by remember(tab.url) { mutableIntStateOf(0) }
    // لا نعيد التحميل ولا نبدّل للموقع تلقائياً (كان يقطع تحميل الصفحة البطيء): ننتظر ثم تظهر رسالة بزرَّي إعادة المحاولة وفتح الموقع
    LaunchedEffect(tab.url, retry) { waited = false; delay(15000); waited = true }
    val nearEnd by remember { derivedStateOf { val li = state.layoutInfo; li.totalItemsCount > 0 && (li.visibleItemsInfo.lastOrNull()?.index ?: 0) >= li.totalItemsCount - 4 } }
    LaunchedEffect(nearEnd, list.size) { if (nearEnd) { delay(250); YtApp.more(tab) } }

    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) { if (refreshing) { delay(1500); refreshing = false } }
    val pullState = rememberPullToRefreshState()

    val nudge = fresh && s.nudge && s.key == YtApp.urlKey(tab.url)
    when {
        list.isEmpty() && YtApp.pageOf(tab.url) == "home" && (nudge || waited) -> YtStart(tab)
        list.isEmpty() && !waited -> YtSkeleton()
        list.isEmpty() -> YtEmpty(onShowSite, onRetry = { retry++; YtApp.refresh(tab) })
        else -> PullToRefreshBox(
            isRefreshing = refreshing, onRefresh = { refreshing = true; YtApp.refresh(tab) }, modifier = Modifier.fillMaxSize(),
            state = pullState,
            indicator = { NovaPullIndicator(pullState, refreshing, Modifier.align(Alignment.TopCenter)) }
        ) {
            LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp)) {
                itemsIndexed(list, key = { _, v -> v.id }) { _, v -> Box(Modifier.animateItem()) { VideoCard(v, onClick = { if (plId != null) YtApp.openInList(tab, v.id, plId) else YtApp.open(tab, v) }) } }
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        NovaSpinner(size = 22.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            NavItem(Icons.Default.Person, L("أنت"), page == "library" || page == "history") { YtApp.go(tab, "/feed/library") }
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

/** الرئيسية بلا توصيات (غير مسجّل دخول / بلا سجل مشاهدة): شاشة بحث أصلية بدل صفحة يوتيوب الفارغة. */
@Composable
private fun YtStart(tab: BrowserTab) {
    val cs = MaterialTheme.colorScheme
    val s = tab.yt
    val topics = listOf("موسيقى", "ألعاب", "أخبار", "رياضة", "طبخ", "تقنية", "أفلام", "تعليم", "كرتون")
    fun go(q: String) { s.recent.remove(q); s.recent.add(0, q); while (s.recent.size > 8) s.recent.removeAt(s.recent.lastIndex); s.searching = false; YtApp.search(tab, q) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
    ) {
        Text(L("ابحث لتبدأ"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = cs.onSurface)
        Text(
            L("لا يعرض يوتيوب توصيات دون تسجيل دخول أو سجل مشاهدة. ابحث عن أي شيء أو اختر موضوعاً."),
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center
        )
        Surface(onClick = { s.searching = true }, shape = CircleShape, color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Search, null, tint = cs.onSurfaceVariant)
                Text(L("ابحث في يوتيوب"), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            }
        }
        topics.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    Surface(onClick = { go(L(t)) }, shape = CircleShape, color = cs.secondaryContainer) {
                        Text(L(t), Modifier.padding(horizontal = 16.dp, vertical = 9.dp), color = cs.onSecondaryContainer, style = MaterialTheme.typography.labelLarge)
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

// ───────────────────────── صفحة «أنت» ─────────────────────────
// صفحة المكتبة في يوتيوب ليست قائمة فيديوهات واحدة (بل رفوف: سجل وقوائم تشغيل)، فكانت تبقى هيكلاً رمادياً ثم تظهر كخطأ.
// هنا تُعرض بتصميم أصلي: رف «السجل» من الصفحة نفسها + اختصارات إلى صفحات تعمل بثبات.

@Composable
private fun YtYou(tab: BrowserTab, fresh: Boolean, onShowSite: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val recent = if (fresh) tab.yt.items.take(14) else emptyList()
    val pls = if (fresh) tab.yt.playlists else emptyList()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 18.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, Modifier.size(30.dp), tint = cs.tertiary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(L("مكتبتك"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.onSurface)
                    Text(L("السجل وقوائم التشغيل والفيديوهات المحفوظة"), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(L("السجل"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = cs.onSurface, modifier = Modifier.weight(1f))
                TextButton(onClick = { YtApp.go(tab, "/feed/history") }) { Text(L("عرض الكل"), color = cs.tertiary) }
            }
        }
        item {
            if (recent.isEmpty()) Text(
                L("لا توجد فيديوهات حديثة هنا. افتح السجل، أو سجّل الدخول من عرض الموقع."),
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) else LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(recent, key = { _, v -> v.id }) { _, v -> YtShelfCard(v) { YtApp.open(tab, v) } }
            }
        }
        item {
            Text(L("قوائم التشغيل"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = cs.onSurface, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp))
            if (pls.isEmpty()) Text(
                L("لا توجد قوائم تشغيل هنا. سجّل الدخول من عرض الموقع."),
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) else LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(pls, key = { _, p -> p.id }) { _, p -> YtPlaylistCard(p) { YtApp.go(tab, "/playlist?list=" + p.id) } }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                ListRow(groupShape(0, 4), L("السجل"), null, { YtApp.go(tab, "/feed/history") }) { IconCircle { Icon(Icons.Default.Refresh, null) } }
                ListRow(groupShape(1, 4), L("المشاهدة لاحقاً"), null, { YtApp.go(tab, "/playlist?list=WL") }) { IconCircle { Icon(Icons.Default.DateRange, null) } }
                ListRow(groupShape(2, 4), L("الفيديوهات التي أعجبتني"), null, { YtApp.go(tab, "/playlist?list=LL") }) { IconCircle { Icon(Icons.Default.ThumbUp, null) } }
                ListRow(groupShape(3, 4), L("قوائم التشغيل وقناتك"), L("يفتح الموقع الكامل"), onShowSite) { IconCircle { Icon(Icons.Default.List, null) } }
            }
        }
    }
}

@Composable
private fun YtShelfCard(v: YtVideo, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.width(168.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
            NetImage("https://i.ytimg.com/vi/${v.id}/hqdefault.jpg", Modifier.fillMaxSize())
            if (v.dur.isNotEmpty()) Text(
                v.dur, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color(0xCC000000), RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
        Text(
            v.title, style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Content), color = cs.onSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        if (v.channel.isNotBlank()) Text(v.channel, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ───────────────────────── إعدادات المشغّل ─────────────────────────

private fun qualityLabel(q: String): String = when (q) {
    "highres" -> "4320p"; "hd2880" -> "2880p"; "hd2160" -> "2160p 4K"; "hd1440" -> "1440p"; "hd1080" -> "1080p"; "hd720" -> "720p"
    "large" -> "480p"; "medium" -> "360p"; "small" -> "240p"; "tiny" -> "144p"; "auto" -> L("تلقائي"); else -> q
}

private fun rateLabel(r: Double): String = if (r == 1.0) L("عادية") else (if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()) + "x"

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape, color = if (selected) cs.tertiary else cs.surfaceContainerHigh,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick)
    ) {
        Text(
            label, Modifier.padding(horizontal = 16.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1,
            color = if (selected) cs.onTertiary else cs.onSurface, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

private fun sizeLabel(k: String): String = when (k) { "s" -> L("صغير"); "l" -> L("كبير"); "xl" -> L("ضخم"); else -> L("متوسط") }
private fun bgLabel(k: String): String = when (k) { "solid" -> L("غامقة"); "none" -> L("بلا خلفية"); else -> L("زجاجية") }

/** لغات الترجمة التلقائية الأكثر استخداماً تظهر أولاً كشرائح سريعة؛ الباقي في قائمة «كل اللغات». */
private val popularTr = listOf("ar", "en", "fr", "tr", "es", "de", "ru", "fa", "ur", "hi", "id", "pt", "it", "ja", "ko", "zh-Hans")

/** بطاقة قسم في قائمة الإعدادات: عنوان صغير ومحتوى على خلفية مدوّرة. */
@Composable
private fun SheetCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainer).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        content()
    }
}

/** صف اختيار (لغة ترجمة…) مع علامة الصح للمحدد. */
@Composable
private fun PickRow(label: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) cs.tertiary.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                color = if (selected) cs.tertiary else cs.onSurface, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = cs.tertiary)
    }
}

/** معاينة حيّة لشكل الترجمة وموضعها (أعلى/وسط/أسفل + الإزاحة) فوق لقطة داكنة. */
@Composable
private fun CcPreview(size: String, bg: String, pos: String, off: Int) {
    val k = when (size) { "s" -> 0.82f; "l" -> 1.22f; "xl" -> 1.5f; else -> 1f }
    val fill = when (bg) { "solid" -> Color(0xEB000000); "none" -> Color.Transparent; else -> Color(0xAD0E0E12) }
    val h = 120.dp
    val gap = h * (off / 100f)
    Box(
        Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3B4F73), Color(0xFF0F172A)))),
        contentAlignment = when (pos) { "t" -> Alignment.TopCenter; "m" -> Alignment.Center; else -> Alignment.BottomCenter }
    ) {
        Text(
            L("هكذا ستظهر الترجمة"), color = Color.White, fontSize = (15f * k).sp, textAlign = TextAlign.Center,
            style = if (bg == "none") TextStyle(shadow = Shadow(Color.Black, Offset(0f, 1f), 8f)) else TextStyle.Default,
            modifier = Modifier
                .padding(start = 8.dp, end = 8.dp, top = if (pos == "t") 10.dp + gap else 0.dp, bottom = if (pos == "b") 10.dp + gap else if (pos == "m") gap * 2 else 0.dp)
                .background(fill, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 2.dp)
        )
    }
}

private fun posLabel(k: String): String = when (k) { "t" -> L("أعلى"); "m" -> L("وسط"); else -> L("أسفل") }

/**
 * قائمة إعدادات المشغّل الأصلية: سرعة، جودة، ترجمة (لغات حقيقية + ترجمة تلقائية + شكل)، تكرار.
 * تنفَّذ عبر واجهة المشغّل الحيّ في الصفحة. لغات الترجمة تصل بعد لحظة من فتح القائمة (تُحمَّل وحدة الترجمة أولاً).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YtPlayerSheet(tab: BrowserTab) {
    val cs = MaterialTheme.colorScheme
    val info = tab.yt.ps
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var allCaps by remember { mutableStateOf(false) }
    var allTr by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { YtApp.closeSettings(tab) }, sheetState = state, containerColor = cs.background) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(L("إعدادات المشغّل"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.onSurface)
            SheetCard(L("سرعة التشغيل")) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0).forEach { r ->
                        OptionChip(rateLabel(r), info != null && kotlin.math.abs(info.rate - r) < 0.01) { YtApp.setRate(tab, r) }
                    }
                }
            }
            if (info != null && info.qualities.isNotEmpty()) SheetCard(L("جودة الفيديو")) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    info.qualities.forEach { q -> OptionChip(qualityLabel(q), q == info.quality) { YtApp.setQuality(tab, q) } }
                }
            }
            SheetCard(L("الترجمة (CC)")) {
                if (info == null || (!info.ccReady && info.captions.isEmpty())) {
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NovaSpinner(size = 18.dp, color = cs.onSurfaceVariant)
                        Text(L("جارٍ تحميل لغات الترجمة…"), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                } else if (info.captions.isEmpty()) {
                    Text(L("لا توجد ترجمة متاحة لهذا الفيديو"), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
                } else {
                    PickRow(L("إيقاف"), null, info.caption.isBlank()) { YtApp.setCaption(tab, "") }
                    val shown = if (allCaps) info.captions else info.captions.take(6)
                    shown.forEach { c ->
                        PickRow(c.name, if (c.auto) L("مُولَّدة تلقائياً") else null, c.key == info.caption) { YtApp.setCaption(tab, c.key, info.translateTo) }
                    }
                    if (info.captions.size > 6) TextButton(onClick = { allCaps = !allCaps }) {
                        Text(if (allCaps) L("عرض أقل") else L("عرض كل اللغات") + " (${info.captions.size})", color = cs.tertiary)
                    }
                }
            }
            if (info != null && info.captions.isNotEmpty() && info.translations.isNotEmpty()) SheetCard(L("ترجمة تلقائية إلى")) {
                // المسار الأساسي: المحدد حالياً، وإن كانت الترجمة مُوقَفة فأول مسار (تفعيل الترجمة التلقائية يشغّل الترجمة)
                val base = info.caption.ifBlank { info.captions.first().key }
                val quick = popularTr.mapNotNull { code -> info.translations.firstOrNull { it.first == code } }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionChip(L("بدون"), info.translateTo.isBlank()) { if (info.caption.isNotBlank()) YtApp.setCaption(tab, base, "") }
                    quick.forEach { t -> OptionChip(t.second, t.first == info.translateTo) { YtApp.setCaption(tab, base, t.first) } }
                }
                TextButton(onClick = { allTr = !allTr }) { Text(if (allTr) L("إخفاء القائمة") else L("كل اللغات"), color = cs.tertiary) }
                if (allTr) Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    info.translations.forEach { t -> PickRow(t.second, null, t.first == info.translateTo) { YtApp.setCaption(tab, base, t.first) } }
                }
            }
            SheetCard(L("شكل الترجمة")) {
                val size = info?.ccSize ?: "m"
                val bg = info?.ccBg ?: "glass"
                val pos = info?.ccPos ?: "b"
                var off by remember(info?.ccOff) { mutableIntStateOf(info?.ccOff ?: 0) }
                CcPreview(size, bg, pos, off)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("s", "m", "l", "xl").forEach { k -> OptionChip(sizeLabel(k), size == k) { YtApp.setCcStyle(tab, k, bg) } }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("glass", "solid", "none").forEach { k -> OptionChip(bgLabel(k), bg == k) { YtApp.setCcStyle(tab, size, k) } }
                }
                Text(L("موضع الترجمة"), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("t", "m", "b").forEach { k -> OptionChip(posLabel(k), pos == k) { YtApp.setCcStyle(tab, size, bg, k, off) } }
                }
                Text(L("الإزاحة عن الحافة"), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Slider(
                    value = off.toFloat(), valueRange = 0f..40f,
                    onValueChange = { off = it.toInt(); YtApp.setCcStyle(tab, size, bg, pos, off, quiet = true) },
                    onValueChangeFinished = { YtApp.setCcStyle(tab, size, bg, pos, off) }
                )
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainer)
                    .clickable { YtApp.setLoop(tab, info?.loop != true) }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(L("تكرار الفيديو"), style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, modifier = Modifier.weight(1f))
                Switch(checked = info?.loop == true, onCheckedChange = null)
            }
            if (info == null) Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                NovaSpinner(size = 22.dp, color = cs.onSurfaceVariant)
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
        Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))) {
            NetImage("https://i.ytimg.com/vi/${v.id}/hqdefault.jpg", Modifier.fillMaxSize())
            if (v.dur.isNotEmpty()) Text(
                v.dur, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color(0xB3000000), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 2.dp)
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
    c.inputStream.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }) }   // نصف ذاكرة الصور المصغّرة
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
    var titleOpen by remember(tab.url) { mutableStateOf(false) }
    var plOpen by remember(tab.url) { mutableStateOf(false) }
    val panel = if (fresh) s.plPanel else null
    val nearEnd by remember { derivedStateOf { val li = state.layoutInfo; li.totalItemsCount > 0 && (li.visibleItemsInfo.lastOrNull()?.index ?: 0) >= li.totalItemsCount - 4 } }
    LaunchedEffect(nearEnd, list.size) { if (nearEnd) { delay(250); YtApp.more(tab) } }
    val link = "https://youtu.be/" + (w?.id ?: YtApp.urlKey(tab.url).substringAfter('?'))

    Column(Modifier.fillMaxSize()) {
        // ثقب بنسبة 16:9: يظهر منه المشغّل الحيّ للصفحة (خلف الواجهة). لا يعالج اللمس فيصل للمشغّل.
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            // زر التصغير فوق الزاوية اليسرى الفعلية للمشغّل (كتطبيق يوتيوب) بغضّ النظر عن اتجاه اللغة؛
            // أزرار الترجمة والترس الأصلية في الزاوية اليمنى، فلا يتداخل الزران أبداً
            Box(
                Modifier.align(AbsoluteAlignment.TopLeft).padding(4.dp).size(40.dp).clip(CircleShape).background(Color(0x66000000))
                    .clickable { YtApp.minimize(tab) },
                contentAlignment = Alignment.Center
            ) { ChevronDownGlyph(Color.White, Modifier.size(22.dp)) }
        }
        Surface(Modifier.weight(1f).fillMaxWidth(), color = cs.background) {
            if (s.showComments) YtComments(tab, onShowSite)
            else LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                item {
                    Column(
                        Modifier.fillMaxWidth().clickable { titleOpen = !titleOpen }.animateContentSize().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            w?.title?.ifBlank { null } ?: tab.title, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
                            fontWeight = FontWeight.Bold, color = cs.onSurface, maxLines = if (titleOpen) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis
                        )
                        if (!w?.info.isNullOrBlank()) Text(w!!.info, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        if (!titleOpen) Text(L("المزيد"), style = MaterialTheme.typography.labelMedium, color = cs.tertiary)
                    }
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LikeDislikePill(w?.liked == true, w?.likes?.ifBlank { null } ?: L("أعجبني"), { YtApp.like(tab) }, { YtApp.dislike(tab) })
                        ActionPill(Icons.Default.Share, L("مشاركة")) { shareText(ctx, link) }
                        ActionPill(Icons.Default.Settings, L("الإعدادات")) { YtApp.openSettings(tab) }
                        ActionPill(null, (if (s.speed == s.speed.toInt().toDouble()) "${s.speed.toInt()}x" else "${s.speed}x") + " " + L("السرعة"), active = s.speed != 1.0) { YtApp.cycleSpeed(tab) }
                        ActionPill(null, if (s.sleepMin > 0) L("مؤقت النوم") + " ${s.sleepMin}" + L("د") else L("مؤقت النوم"), active = s.sleepMin > 0) { YtApp.cycleSleep(tab) }
                        ActionPill(Icons.Default.Share, L("مشاركة من هنا")) { YtApp.currentSec(tab) { sec -> shareText(ctx, if (sec > 3) "$link?t=$sec" else link) } }
                        ActionPill(Icons.Default.Add, L("حفظ")) { YtApp.saveOpen(tab) }
                        ActionPill(null, L("تنزيل")) { onDownload() }
                        ActionPill(null, L("منبثق")) { onPip() }
                        ActionPill(null, L("عرض الموقع")) { onShowSite() }
                    }
                }
                if (panel != null) item { YtPlaylistPanel(tab, panel, w?.id ?: "", plOpen) { plOpen = !plOpen } }
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
                        NovaSpinner(size = 22.dp, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** زر الإعجاب وعدم الإعجاب ككبسولة واحدة مقسومة (كتطبيق يوتيوب). */
@Composable
private fun LikeDislikePill(liked: Boolean, likes: String, onLike: () -> Unit, onDislike: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val c = if (liked) cs.tertiary else cs.onSecondaryContainer
    Surface(shape = CircleShape, color = if (liked) cs.tertiary.copy(alpha = 0.2f) else cs.secondaryContainer, modifier = Modifier.clip(CircleShape)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clickable(onClick = onLike).padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.ThumbUp, null, Modifier.size(16.dp), tint = c)
                Text(likes, style = MaterialTheme.typography.labelLarge, color = c, maxLines = 1)
            }
            Box(Modifier.width(1.dp).height(20.dp).background(cs.outlineVariant))
            Box(Modifier.clickable(onClick = onDislike).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Icon(Icons.Default.ThumbUp, L("لم يعجبني"), Modifier.size(16.dp).rotate(180f), tint = cs.onSecondaryContainer)
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
            !waited -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { NovaSpinner(size = 24.dp, color = cs.onSurfaceVariant) }
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


// ───────────────────────── قوائم التشغيل ─────────────────────────

private fun playlistName(id: String): String = when (id) {
    "WL" -> L("المشاهدة لاحقاً")
    "LL" -> L("الفيديوهات التي أعجبتني")
    else -> L("قائمة التشغيل")
}

/** بطاقة قائمة تشغيل في المكتبة: صورة أول فيديو + العنوان + عدد الفيديوهات. */
@Composable
private fun YtPlaylistCard(p: YtPlaylist, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.width(168.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerHigh)) {
            if (p.thumb.isNotBlank()) NetImage("https://i.ytimg.com/vi/${p.thumb}/hqdefault.jpg", Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color(0xCC000000), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Icon(Icons.Default.List, null, Modifier.size(14.dp), tint = Color.White)
            }
        }
        Text(
            p.title, style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Content), color = cs.onSurface,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        if (p.meta.isNotBlank()) Text(p.meta, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** رأس صفحة قائمة التشغيل: العنوان وعدد الفيديوهات وزر «تشغيل الكل». */
@Composable
private fun YtPlaylistHeader(tab: BrowserTab) {
    val cs = MaterialTheme.colorScheme
    val s = tab.yt
    val lid = remember(tab.url) { Uri.parse(tab.url).getQueryParameter("list") ?: "" }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                s.plTitle.ifBlank { playlistName(lid) }, style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
                fontWeight = FontWeight.Bold, color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text("${s.items.size} " + L("فيديو"), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
        Button(
            onClick = { s.items.firstOrNull()?.let { YtApp.openInList(tab, it.id, lid) } }, shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = cs.onSurface, contentColor = cs.surface)
        ) {
            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(L("تشغيل الكل"), fontWeight = FontWeight.SemiBold)
        }
    }
}

/** قائمة التشغيل الجارية في صفحة المشاهدة: رأس يبيّن الموضع (3/20) وعند التوسيع تظهر العناصر للانتقال بينها. */
@Composable
private fun YtPlaylistPanel(tab: BrowserTab, pp: YtPanel, curId: String, open: Boolean, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val idx = pp.items.indexOfFirst { it.id == curId }
    val next = if (idx >= 0) pp.items.getOrNull(idx + 1) else null
    Surface(
        shape = RoundedCornerShape(16.dp), color = cs.surfaceContainer,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).animateContentSize()
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { if (pp.items.isEmpty()) YtApp.go(tab, "/playlist?list=" + pp.id) else onToggle() }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.List, null, Modifier.size(20.dp), tint = cs.tertiary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        pp.title.ifBlank { playlistName(pp.id) }, style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Content),
                        color = cs.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    val sub = when {
                        pp.items.isEmpty() -> L("فتح قائمة التشغيل")
                        next != null && !open -> L("التالي") + ": " + next.title
                        else -> ""
                    }
                    if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Content), color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (pp.items.isNotEmpty()) Text(
                    (if (idx >= 0) "${idx + 1}/" else "") + pp.items.size, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant
                )
            }
            if (open) pp.items.take(40).forEach { v ->
                val cur = v.id == curId
                Row(
                    Modifier.fillMaxWidth().background(if (cur) cs.tertiary.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { if (!cur) YtApp.openInList(tab, v.id, pp.id) }.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))) {
                        NetImage("https://i.ytimg.com/vi/${v.id}/mqdefault.jpg", Modifier.fillMaxSize())
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            v.title, style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Content),
                            color = if (cur) cs.tertiary else cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        if (v.channel.isNotBlank()) Text(v.channel, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** «حفظ في قائمة تشغيل»: خيارات القوائم تأتي من قائمة الحفظ الأصلية في الصفحة (تتطلب تسجيل الدخول). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YtSaveSheet(tab: BrowserTab, onShowSite: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val s = tab.yt
    val opts = s.saveOpts
    var newDlg by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { YtApp.saveClose(tab) }, sheetState = state, containerColor = cs.background) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(L("حفظ في قائمة تشغيل"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.onSurface)
            when {
                s.saveFail -> {
                    Text(L("تعذّر فتح قائمة الحفظ — تأكد من تسجيل الدخول من عرض الموقع"), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    TextButton(onClick = { YtApp.saveClose(tab); onShowSite() }) { Text(L("عرض الموقع"), color = cs.tertiary) }
                }
                opts == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    NovaSpinner(size = 22.dp, color = cs.onSurfaceVariant)
                }
                else -> {
                    opts.forEachIndexed { i, o ->
                        if (o.name.isBlank()) return@forEachIndexed
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { YtApp.saveToggle(tab, i) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(checked = o.checked, onCheckedChange = null, modifier = Modifier.padding(start = 6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(o.name, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content), color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (o.sub.isNotBlank()) Text(o.sub, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { newDlg = true }.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(20.dp), tint = cs.tertiary)
                        Text(L("قائمة جديدة"), style = MaterialTheme.typography.bodyLarge, color = cs.tertiary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    if (newDlg) AlertDialog(
        onDismissRequest = { newDlg = false }, shape = RoundedCornerShape(24.dp), containerColor = cs.surfaceContainerHigh,
        title = { Text(L("قائمة جديدة")) },
        text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true, label = { Text(L("اسم القائمة")) }) },
        confirmButton = {
            TextButton(onClick = { val n = newName.trim(); if (n.isNotEmpty()) YtApp.saveNew(tab, n); newName = ""; newDlg = false }) { Text(L("إنشاء"), color = cs.tertiary) }
        },
        dismissButton = { TextButton(onClick = { newDlg = false }) { Text(L("إلغاء"), color = cs.onSurfaceVariant) } }
    )
}
