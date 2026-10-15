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

@Immutable
class IosColors(
    val dark: Boolean,
    val blue: Color, val green: Color, val orange: Color, val red: Color, val yellow: Color,
    val teal: Color, val indigo: Color, val purple: Color, val pink: Color,
    val gray: Color, val gray2: Color, val gray3: Color, val gray4: Color, val gray5: Color, val gray6: Color,
    val label: Color, val label2: Color, val label3: Color, val label4: Color,
    val fill: Color, val fill2: Color, val fill3: Color, val fill4: Color,
    val separator: Color, val opaqueSeparator: Color,
    val bg: Color, val bg2: Color, val groupedBg: Color, val cell: Color,
    val chrome: Color, val menu: Color, val tabsBg: Color, val alert: Color
)

val IosLight = IosColors(
    dark = false,
    blue = Color(0xFF007AFF), green = Color(0xFF34C759), orange = Color(0xFFFF9500), red = Color(0xFFFF3B30), yellow = Color(0xFFFFCC00),
    teal = Color(0xFF30B0C7), indigo = Color(0xFF5856D6), purple = Color(0xFFAF52DE), pink = Color(0xFFFF2D55),
    gray = Color(0xFF8E8E93), gray2 = Color(0xFFAEAEB2), gray3 = Color(0xFFC7C7CC), gray4 = Color(0xFFD1D1D6), gray5 = Color(0xFFE5E5EA), gray6 = Color(0xFFF2F2F7),
    label = Color(0xFF000000), label2 = Color(0x993C3C43), label3 = Color(0x4D3C3C43), label4 = Color(0x2E3C3C43),
    fill = Color(0x33787880), fill2 = Color(0x28787880), fill3 = Color(0x1F767680), fill4 = Color(0x14747480),
    separator = Color(0x4A3C3C43), opaqueSeparator = Color(0xFFC6C6C8),
    bg = Color(0xFFFFFFFF), bg2 = Color(0xFFF2F2F7), groupedBg = Color(0xFFF2F2F7), cell = Color(0xFFFFFFFF),
    chrome = Color(0xF0F9F9F9), menu = Color(0xF2F7F7F9), tabsBg = Color(0xFFE8E8ED), alert = Color(0xFFF2F2F2)
)

val IosDark = IosColors(
    dark = true,
    blue = Color(0xFF0A84FF), green = Color(0xFF30D158), orange = Color(0xFFFF9F0A), red = Color(0xFFFF453A), yellow = Color(0xFFFFD60A),
    teal = Color(0xFF40C8E0), indigo = Color(0xFF5E5CE6), purple = Color(0xFFBF5AF2), pink = Color(0xFFFF375F),
    gray = Color(0xFF8E8E93), gray2 = Color(0xFF636366), gray3 = Color(0xFF48484A), gray4 = Color(0xFF3A3A3C), gray5 = Color(0xFF2C2C2E), gray6 = Color(0xFF1C1C1E),
    label = Color(0xFFFFFFFF), label2 = Color(0x99EBEBF5), label3 = Color(0x4DEBEBF5), label4 = Color(0x29EBEBF5),
    fill = Color(0x5C787880), fill2 = Color(0x52787880), fill3 = Color(0x3D767680), fill4 = Color(0x2E767680),
    separator = Color(0xA6545458), opaqueSeparator = Color(0xFF38383A),
    bg = Color(0xFF000000), bg2 = Color(0xFF1C1C1E), groupedBg = Color(0xFF000000), cell = Color(0xFF1C1C1E),
    chrome = Color(0xF01C1C1E), menu = Color(0xF22C2C2E), tabsBg = Color(0xFF2C2C2E), alert = Color(0xFF2C2C2E)
)

val LocalIos = staticCompositionLocalOf { IosLight }

private val LightColors = lightColorScheme(
    primary = IosLight.blue, onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8FF), onPrimaryContainer = Color(0xFF004CB3),
    secondary = Color(0xFF8E8E93), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E5EA), onSecondaryContainer = Color(0xFF000000),
    tertiary = IosLight.blue, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE5E5EA), onTertiaryContainer = Color(0xFF000000),
    error = IosLight.red, onError = Color.White,
    errorContainer = Color(0xFFFFE3E0), onErrorContainer = Color(0xFFC4251A),
    background = IosLight.groupedBg, onBackground = IosLight.label,
    surface = IosLight.cell, onSurface = IosLight.label,
    surfaceVariant = Color(0xFFE5E5EA), onSurfaceVariant = Color(0xFF6C6C70),
    outline = IosLight.opaqueSeparator, outlineVariant = Color(0xFFE5E5EA),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF7F7F9), surfaceContainerHigh = Color(0xFFEEEEF0), surfaceContainerHighest = Color(0xFFE5E5EA)
)

private val DarkColors = darkColorScheme(
    primary = IosDark.blue, onPrimary = Color.White,
    primaryContainer = Color(0xFF0B3A73), onPrimaryContainer = Color(0xFFBFDCFF),
    secondary = Color(0xFF8E8E93), onSecondary = Color.White,
    secondaryContainer = Color(0xFF2C2C2E), onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = IosDark.blue, onTertiary = Color.White,
    tertiaryContainer = Color(0xFF2C2C2E), onTertiaryContainer = Color(0xFFFFFFFF),
    error = IosDark.red, onError = Color.White,
    errorContainer = Color(0xFF4A1612), onErrorContainer = Color(0xFFFFB4AB),
    background = IosDark.groupedBg, onBackground = IosDark.label,
    surface = IosDark.cell, onSurface = IosDark.label,
    surfaceVariant = Color(0xFF2C2C2E), onSurfaceVariant = Color(0xFF98989F),
    outline = IosDark.opaqueSeparator, outlineVariant = Color(0xFF2C2C2E),
    surfaceContainerLowest = Color(0xFF000000), surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF2C2C2E), surfaceContainerHigh = Color(0xFF323235), surfaceContainerHighest = Color(0xFF3A3A3C)
)

private val NovaTypography = Typography().let { t ->
    val sf = androidx.compose.ui.text.font.FontFamily.SansSerif
    fun s(b: androidx.compose.ui.text.TextStyle, size: Int, line: Int, w: FontWeight) =
        b.copy(fontFamily = sf, fontSize = size.sp, lineHeight = line.sp, fontWeight = w, letterSpacing = 0.sp)
    t.copy(
        displayLarge = s(t.displayLarge, 34, 41, FontWeight.Bold),
        displayMedium = s(t.displayMedium, 28, 34, FontWeight.Bold),
        displaySmall = s(t.displaySmall, 22, 28, FontWeight.Bold),
        headlineLarge = s(t.headlineLarge, 28, 34, FontWeight.Bold),
        headlineMedium = s(t.headlineMedium, 28, 34, FontWeight.Bold),
        headlineSmall = s(t.headlineSmall, 22, 28, FontWeight.Bold),
        titleLarge = s(t.titleLarge, 20, 25, FontWeight.SemiBold),
        titleMedium = s(t.titleMedium, 17, 22, FontWeight.SemiBold),
        titleSmall = s(t.titleSmall, 15, 20, FontWeight.SemiBold),
        bodyLarge = s(t.bodyLarge, 17, 22, FontWeight.Normal),
        bodyMedium = s(t.bodyMedium, 15, 20, FontWeight.Normal),
        bodySmall = s(t.bodySmall, 13, 18, FontWeight.Normal),
        labelLarge = s(t.labelLarge, 15, 20, FontWeight.Medium),
        labelMedium = s(t.labelMedium, 12, 16, FontWeight.Medium),
        labelSmall = s(t.labelSmall, 11, 13, FontWeight.Medium)
    )
}

private val NovaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp), extraLarge = RoundedCornerShape(14.dp)
)

object IosShape {
    val pill = RoundedCornerShape(50)
    val card = RoundedCornerShape(10.dp)
    val field = RoundedCornerShape(12.dp)
    val sheet = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
}

@Composable
fun NovaTheme(activity: ComponentActivity, content: @Composable () -> Unit) {
    val dark = when (Prefs.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    SideEffect {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
        )
    }
    val ios = if (dark) IosDark else IosLight
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = NovaTypography, shapes = NovaShapes) {
        CompositionLocalProvider(
            LocalIos provides ios,
            LocalContentColor provides ios.label,
            LocalLayoutDirection provides if (I18n.isEnglish()) LayoutDirection.Ltr else LayoutDirection.Rtl
        ) {
            content()
        }
    }
}
