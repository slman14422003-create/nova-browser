package com.nova.browser

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * مصدر واحد لكل حركات التطبيق: منحنيات Material "Emphasized" (دخول سريع وهبوط ناعم)،
 * وكلها تمر عبر Adaptive.ms فتقصر أو تتوقف تلقائياً عند السخونة أو إيقاف الحركة.
 * الخصائص getters عمداً لأن Adaptive.level يتغير أثناء التشغيل.
 */
object NovaMotion {
    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphasizedAccel = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** لوحات ملء الشاشة (التنزيلات، الإعدادات، كلمات المرور، المكتبة). */
    val panelEnter: EnterTransition
        get() = slideInVertically(tween(Adaptive.ms(280), easing = emphasized)) { it / 12 } +
            fadeIn(tween(Adaptive.ms(200), easing = LinearOutSlowInEasing))
    val panelExit: ExitTransition
        get() = slideOutVertically(tween(Adaptive.ms(200), easing = emphasizedAccel)) { it / 14 } +
            fadeOut(tween(Adaptive.ms(150)))

    /** الشريط العلوي: ينزل من الأعلى. */
    val barEnter: EnterTransition
        get() = slideInVertically(tween(Adaptive.ms(240), easing = emphasized)) { -it } +
            fadeIn(tween(Adaptive.ms(180)))
    val barExit: ExitTransition
        get() = slideOutVertically(tween(Adaptive.ms(160), easing = emphasizedAccel)) { -it } +
            fadeOut(tween(Adaptive.ms(120)))
}

/**
 * تأثير ضغط ناعم (ينكمش قليلاً ثم يرتد بنابض). يعمل على طبقة الرسم فقط فلا يعيد التخطيط.
 * عند إيقاف الحركة لا يتحرك شيء.
 */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = 0.94f): Modifier {
    val down by source.collectIsPressedAsState()
    val s: State<Float> = animateFloatAsState(
        if (down && Adaptive.ms(100) > 0) pressed else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press"
    )
    return this.graphicsLayer { scaleX = s.value; scaleY = s.value }
}

/**
 * ارتفاع منطقة الصفحة (WebView) في طبقة التخطيط (بلا إعادة تركيب أثناء حركة الكيبورد):
 * - كتابة داخل الصفحة: تنتهي الصفحة عند أعلى الكيبورد تماماً فيبقى حقل الإرسال ظاهراً فوقه.
 * - الوضع العادي: تترك مكان شريط العنوان السفلي وشريط التنقل.
 */
fun Modifier.pageInsets(ime: WindowInsets, nav: WindowInsets, typing: Boolean, inPip: Boolean, pill: Dp = 63.dp): Modifier =
    this.layout { m, c ->
        val pad = when {
            inPip -> 0
            typing -> ime.getBottom(this)
            else -> nav.getBottom(this) + pill.roundToPx()
        }
        val h = (c.maxHeight - pad).coerceAtLeast(0)
        val p = m.measure(c.copy(minHeight = h, maxHeight = h))
        layout(c.maxWidth, c.maxHeight) { p.placeRelative(0, 0) }
    }
