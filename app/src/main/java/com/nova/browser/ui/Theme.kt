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

// لوحة ألوان مستوحاة من iOS Safari الحديث (iOS 18/26): أزرق النظام كلون أساسي للروابط
// ومؤشّر التحميل، خلفيات بيضاء/رمادية فاتحة شبه شفافة بدل البني الدافئ السابق.
private val IosBlueLight = Color(0xFF007AFF)
private val IosBlueDark = Color(0xFF0A84FF)

private val LightColors = lightColorScheme(
    primary = IosBlueLight, onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8FF), onPrimaryContainer = Color(0xFF00357F),
    secondary = Color(0xFF3C3C43), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E9EB), onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = Color(0xFFFF9500), onTertiary = Color.White,
    background = Color(0xFFF2F2F7), onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1C1C1E), onSurfaceVariant = Color(0xFF6C6C70),
    outline = Color(0xFFC6C6C8), outlineVariant = Color(0xFFE5E5EA),
    surfaceContainer = Color(0xFFF2F2F7), surfaceContainerHigh = Color(0xFFE9E9EC), surfaceContainerHighest = Color(0xFFDEDEE3)
)
private val DarkColors = darkColorScheme(
    primary = IosBlueDark, onPrimary = Color.White,
    primaryContainer = Color(0xFF123A66), onPrimaryContainer = Color(0xFFBFDCFF),
    secondary = Color(0xFFEBEBF0), onSecondary = Color(0xFF1C1C1E),
    secondaryContainer = Color(0xFF2C2C2E), onSecondaryContainer = Color(0xFFF2F2F7),
    tertiary = Color(0xFFFF9F0A), onTertiary = Color.Black,
    background = Color(0xFF000000), onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF1C1C1E), onSurface = Color(0xFFF2F2F7), onSurfaceVariant = Color(0xFF9B9BA1),
    outline = Color(0xFF48484A), outlineVariant = Color(0xFF2C2C2E),
    surfaceContainer = Color(0xFF151517), surfaceContainerHigh = Color(0xFF2C2C2E), surfaceContainerHighest = Color(0xFF3A3A3C)
)
// خط افتراضي يحاكي SF Pro: sans-serif عريض/هندسي بدل Serif السابق، وهو ما يعطي الطابع الأقرب لواجهات آبل.
private val NovaTypography = Typography().let { t ->
    val sf = androidx.compose.ui.text.font.FontFamily.SansSerif
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = sf, fontWeight = FontWeight.Bold),
        displayMedium = t.displayMedium.copy(fontFamily = sf, fontWeight = FontWeight.Bold),
        displaySmall = t.displaySmall.copy(fontFamily = sf, fontWeight = FontWeight.Bold),
        headlineLarge = t.headlineLarge.copy(fontFamily = sf, fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontFamily = sf, fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontFamily = sf, fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontFamily = sf, fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontFamily = sf, fontWeight = FontWeight.Medium),
        bodyLarge = t.bodyLarge.copy(fontFamily = sf),
        bodyMedium = t.bodyMedium.copy(fontFamily = sf),
        labelLarge = t.labelLarge.copy(fontFamily = sf, fontWeight = FontWeight.SemiBold)
    )
}

/** نصف قطر الزوايا القياسي لكبسولات وبطاقات أسلوب iOS (أشرطة، أزرار، أوراق). */
object IosShape {
    val pill = RoundedCornerShape(50)
    val card = RoundedCornerShape(16.dp)
    val sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
}

/** سمة التطبيق المشتركة بين نافذة المتصفح ونافذة اللوحات (ألوان، خط، اتجاه RTL/LTR، ألوان أشرطة النظام). */
@Composable
fun NovaTheme(activity: ComponentActivity, content: @Composable () -> Unit) {
    val dark = when (Prefs.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    SideEffect {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
        )
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = NovaTypography) {
        // اتجاه الواجهة يتبع لغة التطبيق (العربية RTL، الإنجليزية LTR)
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides
            if (I18n.isEnglish()) androidx.compose.ui.unit.LayoutDirection.Ltr else androidx.compose.ui.unit.LayoutDirection.Rtl) {
            content()
        }
    }
}
