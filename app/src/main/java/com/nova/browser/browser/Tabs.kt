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
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.grid.rememberLazyGridState

private fun hostColor(host: String): Color = Color.hsv((host.hashCode().toLong().let { if (it < 0) -it else it } % 360).toFloat(), 0.55f, 0.72f)

val TabTagColors = listOf(Color.Transparent, Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFF34C759), Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF2D55))

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabSwitcher(
    tabs: List<BrowserTab>, current: Int,
    onSelect: (Int) -> Unit, onClose: (Int) -> Unit, onNew: () -> Unit, onCloseAll: () -> Unit, onBack: () -> Unit,
    onDuplicate: (Int) -> Unit = {}, onCloseOthers: (Int) -> Unit = {}, onCloseTag: (Int) -> Unit = {},
    canReopen: Boolean = false, onReopen: () -> Unit = {}
) {
    val ios = LocalIos.current
    var picked by remember { mutableIntStateOf(-1) }
    val usedTags = tabs.map { it.tag }.filter { it > 0 }.distinct().sorted()
    val filter = if ((picked > 0 && picked !in usedTags) || (picked == -2 && tabs.none { it.pinned })) -1 else picked
    val shown = tabs.withIndex().filter { (_, t) -> when (filter) { -1 -> true; -2 -> t.pinned; else -> t.tag == filter } }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = (current / 2) * 2)
    var filterMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(ios.tabsBg)) {
        LazyVerticalGrid(
            state = gridState, columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).statusBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)
        ) {
            items(shown, key = { it.value.id }) { (i, t) ->
                val blank = t.url.isBlank()
                val host = if (blank) "nova" else hostOf(t.url)
                val accent = remember(host) { hostColor(host) }
                var menu by remember { mutableStateOf(false) }
                val cardShape = RoundedCornerShape(12.dp)
                Box(Modifier.fillMaxWidth().aspectRatio(0.74f).animateItem()) {
                    Column(
                        Modifier.fillMaxSize().shadow(2.dp, cardShape).clip(cardShape).background(ios.cell)
                            .combinedClickable(onClick = { onSelect(i) }, onLongClick = { menu = true })
                    ) {
                        if (t.tag > 0) Box(Modifier.fillMaxWidth().height(3.dp).background(TabTagColors[t.tag]))
                        Row(Modifier.fillMaxWidth().height(34.dp).padding(start = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            val fvTick = Favicons.version
                            val ic = if (blank) null else remember(host, fvTick) { Favicons.get(host) }
                            Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(if (ic != null) Color.White else accent), contentAlignment = Alignment.Center) {
                                if (ic != null) {
                                    val ib = remember(ic) { ic.asImageBitmap() }
                                    Image(ib, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                } else Text(host.take(1).uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (blank) L("صفحة البداية") else t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ios.label, modifier = Modifier.weight(1f)
                            )
                            Box(Modifier.size(30.dp).clickable { onClose(i) }, contentAlignment = Alignment.Center) {
                                Box(Modifier.size(20.dp).clip(CircleShape).background(ios.fill2), contentAlignment = Alignment.Center) {
                                    IosIcon(IosGlyph.Close, ios.label2, 10.dp, weight = 1.3f)
                                }
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxWidth().background(ios.groupedBg)) {
                            val th = t.thumb
                            if (th != null && !blank) {
                                val img = remember(th) { th.asImageBitmap() }
                                Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    if (blank) Text(L("صفحة البداية"), style = MaterialTheme.typography.bodySmall, color = ios.label2)
                                    else Text(host.take(1).uppercase(), fontSize = 34.sp, color = accent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    DropdownMenu(
                        expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(14.dp),
                        containerColor = ios.menu, tonalElevation = 0.dp, shadowElevation = 12.dp
                    ) {
                        Column(Modifier.width(240.dp)) {
                            IosCell(if (t.pinned) L("إلغاء التثبيت") else L("تثبيت"), { t.pinned = !t.pinned; menu = false })
                            IosCell(L("نسخ التبويب"), { menu = false; onDuplicate(i) })
                            IosCell(L("إغلاق التبويبات الأخرى"), { menu = false; onCloseOthers(i) }, divider = t.tag > 0)
                            if (t.tag > 0) IosCell(L("إغلاق كل تبويبات هذا اللون"), { menu = false; onCloseTag(t.tag) }, divider = false)
                            IosDivider()
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                for (g in 0..6) Box(
                                    Modifier.size(26.dp).clip(CircleShape)
                                        .background(if (g == 0) ios.fill2 else TabTagColors[g])
                                        .border(if (t.tag == g) 2.dp else 0.dp, ios.blue, CircleShape)
                                        .clickable { t.tag = g; menu = false },
                                    contentAlignment = Alignment.Center
                                ) { if (g == 0) IosIcon(IosGlyph.Close, ios.label2, 12.dp) }
                            }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(ios.chrome).iosHairlineTop(ios.separator).navigationBarsPadding().height(48.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(44.dp).clickable(onClick = onNew), contentAlignment = Alignment.Center) { IosIcon(IosGlyph.Plus, ios.blue, 26.dp) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.height(32.dp).clip(CircleShape).background(ios.fill3).clickable { filterMenu = true }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "" + tabs.size + " " + L("التبويبات"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ios.label, maxLines = 1
                    )
                    Spacer(Modifier.width(4.dp))
                    IosIcon(IosGlyph.ChevronDown, ios.label2, 11.dp, weight = 1.3f)
                }
                DropdownMenu(
                    expanded = filterMenu, onDismissRequest = { filterMenu = false }, shape = RoundedCornerShape(14.dp),
                    containerColor = ios.menu, tonalElevation = 0.dp, shadowElevation = 12.dp
                ) {
                    Column(Modifier.width(240.dp)) {
                        IosCell(L("الكل"), { picked = -1; filterMenu = false }, trailing = if (filter == -1) ({ IosIcon(IosGlyph.Check, ios.blue, 20.dp) }) else null)
                        if (tabs.any { it.pinned }) IosCell(L("المثبّتة"), { picked = -2; filterMenu = false }, trailing = if (filter == -2) ({ IosIcon(IosGlyph.Check, ios.blue, 20.dp) }) else null)
                        usedTags.forEach { g ->
                            IosCell(
                                "" + tabs.count { it.tag == g }, { picked = g; filterMenu = false },
                                leading = { Box(Modifier.size(14.dp).clip(CircleShape).background(TabTagColors[g])) },
                                trailing = if (filter == g) ({ IosIcon(IosGlyph.Check, ios.blue, 20.dp) }) else null
                            )
                        }
                        IosDivider()
                        if (canReopen) IosCell(L("إعادة فتح تبويب مغلق"), { filterMenu = false; onReopen() })
                        IosCell(L("إغلاق الكل"), { filterMenu = false; onCloseAll() }, tint = ios.red, divider = false)
                    }
                }
            }
            IosTextButton(L("تم"), onBack, bold = true)
        }
    }
}
