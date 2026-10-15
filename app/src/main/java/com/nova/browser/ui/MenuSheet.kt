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

@Composable
private fun ShareHeader(tab: BrowserTab, onClose: () -> Unit) {
    val ios = LocalIos.current
    val host = hostOf(tab.url)
    val fvTick = Favicons.version
    val ic = remember(host, fvTick) { Favicons.get(host) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(ios.cell), contentAlignment = Alignment.Center) {
            if (ic != null) Image(ic.asImageBitmap(), null, Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)))
            else Text(host.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = ios.label2)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tab.title.ifBlank { host }, style = MaterialTheme.typography.titleSmall, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(host, style = MaterialTheme.typography.bodySmall, color = ios.label2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(30.dp).clip(CircleShape).background(ios.fill2).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            IosIcon(IosGlyph.Close, ios.label2, 13.dp, weight = 1.2f)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(
    tab: BrowserTab, onDismiss: () -> Unit, onNewTab: () -> Unit, onFind: () -> Unit,
    onShare: () -> Unit, onCopy: () -> Unit, onPrint: () -> Unit = {}
) {
    val ios = LocalIos.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val hasPage = tab.url.isNotBlank()
    fun act(a: () -> Unit) { scope.launch { state.hide() }.invokeOnCompletion { onDismiss(); a() } }
    val marked = hasPage && Library.isBookmarked(tab.url)
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, containerColor = ios.groupedBg, shape = IosShape.sheet,
        dragHandle = { Box(Modifier.padding(top = 6.dp, bottom = 8.dp).size(width = 36.dp, height = 5.dp).clip(CircleShape).background(ios.gray3)) }
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
            ShareHeader(tab) { act {} }
            IosGroup {
                IosCell(L("مشاركة"), { act(onShare) }, enabled = hasPage, trailing = { IosIcon(IosGlyph.Share, ios.label, 22.dp) })
                IosCell(L("نسخ الرابط"), { act(onCopy) }, enabled = hasPage, divider = false, trailing = { IosIcon(IosGlyph.Copy, ios.label, 22.dp) })
            }
            Spacer(Modifier.height(20.dp))
            IosGroup {
                IosCell(L("تبويب جديد"), { act(onNewTab) }, trailing = { IosIcon(IosGlyph.PlusSquare, ios.label, 22.dp) })
                IosCell(
                    if (marked) L("إزالة من المفضلة") else L("إضافة إلى المفضلة"),
                    { act { toast(ctx, if (Library.toggleBookmark(tab.url, tab.title)) L("أُضيفت إلى المفضلة") else L("أُزيلت من المفضلة")) } },
                    enabled = hasPage, trailing = { IosIcon(IosGlyph.Book, ios.label, 22.dp) }
                )
                IosCell(L("بحث في الصفحة"), { act(onFind) }, enabled = hasPage, trailing = { IosIcon(IosGlyph.Magnifier, ios.label, 22.dp) })
                IosCell("Add to home screen", { act { PageExtras.pinShortcut(ctx, tab) } }, enabled = hasPage, trailing = { IosIcon(IosGlyph.PlusSquare, ios.label, 22.dp) })
                IosCell(L("طباعة / حفظ PDF"), { act(onPrint) }, enabled = hasPage, divider = false, trailing = { IosIcon(IosGlyph.Printer, ios.label, 22.dp) })
            }
        }
    }
}

@Composable
fun PageMenu(
    tab: BrowserTab, expanded: Boolean, onDismiss: () -> Unit, onDesktop: () -> Unit,
    onTranslate: () -> Unit, onDownloads: () -> Unit, onSettings: () -> Unit
) {
    val ios = LocalIos.current
    var sub by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) { if (!expanded) sub = false }
    val zoomNames = listOf(L("صغير"), L("عادي"), L("كبير"), L("كبير جداً"))
    fun act(a: () -> Unit) { onDismiss(); a() }
    DropdownMenu(
        expanded = expanded, onDismissRequest = onDismiss, shape = RoundedCornerShape(14.dp),
        containerColor = ios.menu, tonalElevation = 0.dp, shadowElevation = 12.dp
    ) {
        Column(Modifier.width(260.dp)) {
            if (!sub) {
                Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable(enabled = Prefs.textZoom > 0) { Prefs.pickTextZoom(Prefs.textZoom - 1) },
                        contentAlignment = Alignment.Center
                    ) { Text("A", fontSize = 14.sp, color = if (Prefs.textZoom > 0) ios.label else ios.label3) }
                    Text(zoomNames.getOrElse(Prefs.textZoom) { "" }, style = MaterialTheme.typography.bodyMedium, color = ios.label2, maxLines = 1)
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable(enabled = Prefs.textZoom < zoomNames.lastIndex) { Prefs.pickTextZoom(Prefs.textZoom + 1) },
                        contentAlignment = Alignment.Center
                    ) { Text("A", fontSize = 24.sp, color = if (Prefs.textZoom < zoomNames.lastIndex) ios.label else ios.label3) }
                }
                IosDivider()
                IosCell(L("نسخة سطح المكتب"), { act(onDesktop) }, trailing = if (tab.desktop) ({ IosIcon(IosGlyph.Check, ios.blue, 20.dp) }) else null)
                IosCell(L("وضع القراءة"), { act { tab.webView?.let { WebSupport.reader(it) } } })
                IosCell(L("ترجمة الصفحة"), { act(onTranslate) })
                IosCell(L("أدوات الصفحة"), { sub = true }, chevron = true)
                Box(Modifier.fillMaxWidth().height(6.dp).background(ios.fill4))
                IosCell(L("التنزيلات"), { act(onDownloads) })
                IosCell(L("الإعدادات"), { act(onSettings) }, divider = false)
            } else {
                Row(
                    Modifier.fillMaxWidth().height(44.dp).clickable { sub = false }.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IosIcon(IosGlyph.Back, ios.blue, 20.dp, weight = 1.1f)
                    Spacer(Modifier.width(4.dp))
                    Text(L("أدوات الصفحة"), style = MaterialTheme.typography.titleSmall, color = ios.label)
                }
                IosDivider()
                IosCell(L("الأعلى"), { act { tab.webView?.let { WebSupport.scroll(it, true) } } })
                IosCell(L("الأسفل"), { act { tab.webView?.let { WebSupport.scroll(it, false) } } })
                IosCell(L("تحديث كامل"), { act { tab.webView?.let { WebSupport.hardReload(it) } } })
                IosCell(L("إخفاء العائم"), { act { tab.webView?.let { WebSupport.toggleFloating(it) } } })
                IosCell(L("الصور"), { act { tab.webView?.let { WebSupport.toggleImages(it) } } })
                IosCell(L("إبقاء الشاشة"), { act { tab.webView?.let { WebSupport.toggleKeepOn(it) } } })
                IosCell(L("نسخ النص"), { act { tab.webView?.let { WebSupport.copyPageText(it) } } }, divider = false)
            }
        }
    }
}
