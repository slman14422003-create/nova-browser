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

private class Site(val name: String, val host: String, val url: String, val glyph: String, val color: Long, val icon: ImageVector?)

const val AI_MODE_URL = "https://www.google.com/search?udm=50"

@Composable
private fun FavoriteItem(st: Site, modifier: Modifier, onClick: () -> Unit) {
    val ios = LocalIos.current
    val src = remember { MutableInteractionSource() }
    Column(
        modifier.pressScale(src, 0.92f).clickable(interactionSource = src, indication = null, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(13.5.dp)).background(Color(st.color)), contentAlignment = Alignment.Center) {
            if (st.icon != null) Icon(st.icon, null, Modifier.size(28.dp), tint = Color.White)
            else Text(st.glyph, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(6.dp))
        Text(st.name, style = MaterialTheme.typography.labelMedium, color = ios.label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StartHeader(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, color = LocalIos.current.label, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp))
}

@Composable
fun StartPage(
    tabsCount: Int, activeDl: Int, onSearchClick: () -> Unit, onAi: () -> Unit, onOpen: (String) -> Unit,
    onTabs: () -> Unit, onDownloads: () -> Unit, onSettings: () -> Unit = {}, onLibrary: () -> Unit = {}
) {
    val ios = LocalIos.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(if (shown) 1f else 0f, tween(Adaptive.ms(280)), label = "appear")
    val sites = remember {
        listOf(
            Site(L("الخرائط"), "maps.google.com", "https://maps.google.com", "", 0xFF34C759, Icons.Default.Place),
            Site(L("ترجمة جوجل"), "translate.google.com", "https://translate.google.com", "文A", 0xFF007AFF, null),
            Site("YouTube", "youtube.com", "https://m.youtube.com", "", 0xFFFF3B30, Icons.Default.PlayArrow),
            Site("Gmail", "mail.google.com", "https://mail.google.com", "", 0xFFEA4335, Icons.Default.Email),
            Site("Wikipedia", "wikipedia.org", "https://www.wikipedia.org", "W", 0xFF8E8E93, null),
            Site("GitHub", "github.com", "https://github.com", "</>", 0xFF3A3A3C, null)
        )
    }
    val latest = Downloader.tasks.firstOrNull()
    val dlSub = when {
        activeDl > 0 -> ("" + activeDl + L(" قيد التنزيل"))
        latest != null -> latest.name
        else -> L("لا توجد تنزيلات بعد")
    }

    Column(
        Modifier.fillMaxSize().background(ios.groupedBg).graphicsLayer { alpha = appear; translationY = (1f - appear) * 36f }
            .verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
    ) {
        StartHeader(L("المفضلة"))
        Column(Modifier.padding(horizontal = 12.dp)) {
            sites.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { st -> FavoriteItem(st, Modifier.weight(1f)) { onOpen(st.url) } }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        IosGroup {
            IosCell(
                L("وضع الذكاء الاصطناعي"), onAi, sub = L("اسأل Google أي شيء"), chevron = true, divider = false,
                leading = { IosTile(ios.indigo) { Text("✦", fontSize = 15.sp, color = Color.White) } }
            )
        }
        StartHeader(L("تقرير الخصوصية"))
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ios.cell).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IosIcon(IosGlyph.ShieldFill, ios.blue, 40.dp, accent = ios.cell)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(Shield.total.toString(), style = MaterialTheme.typography.headlineMedium, color = ios.label)
                Text(L("متتبعات تم منعها من تتبعك"), style = MaterialTheme.typography.bodySmall, color = ios.label2)
            }
        }
        Spacer(Modifier.height(24.dp))
        Column {
            ListRow(groupShape(0, 4), L("التنزيلات"), dlSub, onDownloads, modifier = Modifier.padding(horizontal = 16.dp)) { IosTile(ios.blue) { Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(20.dp)) } }
            ListRow(groupShape(1, 4), L("التبويبات"), ("" + tabsCount + L(" مفتوحة")), onTabs, modifier = Modifier.padding(horizontal = 16.dp)) { IosTile(ios.gray) { Icon(Icons.Default.Menu, null, Modifier.size(18.dp)) } }
            ListRow(groupShape(2, 4), L("المكتبة"), null, onLibrary, modifier = Modifier.padding(horizontal = 16.dp)) { IosTile(ios.orange) { Icon(Icons.Default.Star, null, Modifier.size(18.dp)) } }
            ListRow(groupShape(3, 4), L("الإعدادات"), null, onSettings, modifier = Modifier.padding(horizontal = 16.dp)) { IosTile(ios.gray) { Icon(Icons.Default.Settings, null, Modifier.size(18.dp)) } }
        }
    }
}
