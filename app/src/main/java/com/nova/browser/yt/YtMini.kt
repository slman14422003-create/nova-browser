package com.nova.browser

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/**
 * المشغّل المصغّر ليوتيوب: صفحة المشاهدة (بمشغّلها الحيّ) تُنقل إلى نافذة صغيرة عائمة تواصل التشغيل،
 * بينما يتنقّل التبويب بحرية في قوائم يوتيوب. نافذة واحدة فقط في كل وقت، وهي مستقلة عن التبويبات.
 */
object YtMini {
    var wv by mutableStateOf<WebView?>(null); private set
    var title by mutableStateOf("")
    var playing by mutableStateOf(true)
    private var want = true       // نيّة المستخدم: هل يريد الفيديو شغّالاً؟ (نعيد التشغيل تلقائياً إن أوقفه النظام أو الصفحة رغماً عنه)
    private var stalls = 0        // عدد الفحوص المتتالية التي وجدنا فيها الفيديو متوقفاً رغم رغبة المستخدم
    private var kicks = 0

    val active: Boolean get() = wv != null

    // صفحات المشغّل المصغّر (الحالية أو التي أُغلقت للتو): نداءاتها المتأخرة يجب ألا تلمس حالة التبويب
    private val retired: MutableSet<WebView> = java.util.Collections.newSetFromMap(java.util.WeakHashMap<WebView, Boolean>())
    fun owns(v: WebView?): Boolean = v != null && (v === wv || retired.contains(v))

    // 1 = يعمل، 0 = متوقف، 2 = انتهى، -1 = لا يوجد فيديو
    private const val PROBE = "(function(){var v=document.querySelector('video');return v?(v.ended?2:(v.paused?0:1)):-1})()"
    private const val KICK = "(function(){var v=document.querySelector('video');if(!v||v.ended)return;" +
        "try{var mp=document.getElementById('movie_player');if(mp&&mp.playVideo)mp.playVideo()}catch(e){}" +
        "var p=v.play();if(p&&p.catch)p.catch(function(){var b=document.querySelector('.ytp-play-button,button.player-control-play-pause-icon,[aria-label=\"Play\"]');if(b)b.click()})})()"

    fun start(w: WebView, t: String, wasPlaying: Boolean) {
        if (wv != null && wv !== w) close()
        wv = w; title = t; playing = wasPlaying; want = wasPlaying; stalls = 0; kicks = 0
        // الحماية: الصفحة تتجاهل تغيّر الرؤية الناتج عن نقل الـ WebView بين الحاويات ولا توقف الفيديو بنفسها
        w.evaluateJavascript("window.__novaMini&&window.__novaMini(true);window.__novaYtApp&&window.__novaYtApp.mini(true)", null)
        w.post { w.onResume(); w.resumeTimers() }
        // نقل الـ WebView بين الحاويات قد يوقف الفيديو لحظة؛ نستأنفه إن كان يعمل قبل التصغير
        if (wasPlaying) for (d in longArrayOf(350L, 1100L, 2400L, 4000L)) w.postDelayed({
            if (wv === w && want) kick(w)
        }, d)
    }

    private fun kick(w: WebView) {
        kicks++
        YtLog.add("mini kick #$kicks")
        w.onResume(); w.resumeTimers()
        w.evaluateJavascript("window.__novaYtCtl&&window.__novaYtCtl('play')", null)
        w.evaluateJavascript(KICK, null)
    }

    fun toggle() {
        val w = wv ?: return
        want = !want
        playing = want
        stalls = 0; kicks = 0
        w.onResume(); w.resumeTimers()
        if (want) kick(w) else w.evaluateJavascript("window.__novaYtCtl&&window.__novaYtCtl('pause')", null)
        w.postDelayed({ if (wv === w) probe() }, 900)
    }

    /** يُستدعى كل ثانية: يقرأ حالة الفيديو الحقيقية، ويعيد تشغيله إن توقف والمستخدم لم يطلب الإيقاف. */
    fun probe() {
        val w = wv ?: return
        w.evaluateJavascript(PROBE) { r ->
            if (wv !== w || r == null) return@evaluateJavascript
            when (r.trim('"')) {
                "1" -> { playing = true; stalls = 0; kicks = 0 }
                "2" -> { playing = false; want = false; stalls = 0 }
                "0" -> {
                    if (want) {
                        stalls++
                        // نعرض زر الإيقاف أثناء المحاولة كي لا يومض الزر؛ بعد 8 محاولات فاشلة نستسلم ونعرض الحقيقة
                        playing = kicks < 8
                        if (stalls >= 2 && kicks < 8) { stalls = 0; kick(w) }
                    } else playing = false
                }
            }
        }
    }

    fun close() {
        val w = wv ?: return
        retired.add(w)
        wv = null; want = false
        runCatching { w.stopLoading() }
        (w.parent as? ViewGroup)?.removeView(w)
        runCatching { w.onPause(); w.destroy() }
        YtMedia.stop()
    }

    /** أثناء الخروج من التطبيق/العودة: نفس معاملة صفحة التبويب الحالي (متابعة التشغيل في الخلفية). */
    fun bg(on: Boolean) { wv?.evaluateJavascript("window.__novaBg&&window.__novaBg($on)", null) }
    fun wake() { wv?.let { it.resumeTimers(); it.onResume() } }
}

/** الشريط العائم: معاينة حيّة للفيديو + العنوان + تشغيل/إيقاف + إغلاق. لمسة على المعاينة أو العنوان توسّعه. */
@Composable
fun YtMiniPlayer(modifier: Modifier, onExpand: () -> Unit) {
    val w = YtMini.wv ?: return
    val cs = MaterialTheme.colorScheme
    LaunchedEffect(w) { while (true) { YtMini.probe(); delay(1000) } }
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp),
        shape = RoundedCornerShape(18.dp), color = cs.surfaceContainerHigh, shadowElevation = 8.dp, tonalElevation = 2.dp
    ) {
        Row(Modifier.fillMaxWidth().height(68.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(120.dp).fillMaxSize().background(Color.Black)) {
                key(w) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            // الصفحة تُرسم بحجم مشغّل حقيقي (360×203dp) ثم تُصغَّر بصرياً لتملأ الإطار الصغير،
                            // فلا يعاد تخطيط مشغّل يوتيوب على عرض ~120px (سبب التقطيع وتوقف الفيديو سابقاً)
                            MiniHost(ctx).apply {
                                (w.parent as? ViewGroup)?.removeView(w)
                                addView(w, FrameLayout.LayoutParams(MiniHost.virtW(ctx), MiniHost.virtH(ctx)))
                            }
                        }
                    )
                }
                // طبقة شفافة تمتص اللمس (فلا يصل لمشغّل الصفحة الصغير) وتوسّع المشغّل
                Box(Modifier.fillMaxSize().clickable(onClick = onExpand))
            }
            Text(
                YtMini.title.ifBlank { "YouTube" }, modifier = Modifier.weight(1f).clickable(onClick = onExpand).padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Content), fontWeight = FontWeight.SemiBold,
                color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = { YtMini.toggle() }) {
                if (YtMini.playing) MiniPauseGlyph(cs.onSurface, Modifier.size(22.dp))
                else Icon(Icons.Default.PlayArrow, L("تشغيل"), Modifier.size(26.dp), tint = cs.onSurface)
            }
            IconButton(onClick = { YtMini.close() }) { Icon(Icons.Default.Close, L("إغلاق"), Modifier.size(22.dp), tint = cs.onSurfaceVariant) }
        }
    }
}

@Composable
private fun MiniPauseGlyph(color: Color, modifier: Modifier) = Canvas(modifier) {
    val bw = size.width * 0.26f
    val r = CornerRadius(bw * 0.3f)
    drawRoundRect(color, Offset(size.width * 0.16f, size.height * 0.12f), Size(bw, size.height * 0.76f), r)
    drawRoundRect(color, Offset(size.width * 0.58f, size.height * 0.12f), Size(bw, size.height * 0.76f), r)
}

/** سهم لأسفل (تصغير) فوق المشغّل. */
@Composable
fun ChevronDownGlyph(color: Color, modifier: Modifier) = Canvas(modifier) {
    val w = size.width; val h = size.height
    val p = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.2f, h * 0.36f); lineTo(w * 0.5f, h * 0.66f); lineTo(w * 0.8f, h * 0.36f) }
    drawPath(
        p, color, style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = w * 0.11f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
    )
}

/** حاوية تُبقي الـ WebView بحجمه الافتراضي الكامل وتصغّره بالمقياس (scale) ليناسب مساحتها، مع قص ما يتجاوزها. */
private class MiniHost(ctx: Context) : FrameLayout(ctx) {
    private val vw = virtW(ctx)
    private val vh = virtH(ctx)

    init { clipChildren = true; clipToPadding = true }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        getChildAt(0)?.measure(MeasureSpec.makeMeasureSpec(vw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(vh, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val c = getChildAt(0) ?: return
        c.layout(0, 0, vw, vh)
        val sc = (r - l).toFloat() / vw
        c.pivotX = 0f; c.pivotY = 0f; c.scaleX = sc; c.scaleY = sc
    }

    companion object {
        fun virtW(ctx: Context): Int = (360f * ctx.resources.displayMetrics.density).toInt()
        fun virtH(ctx: Context): Int = (virtW(ctx) * 9f / 16f).toInt()
    }
}
