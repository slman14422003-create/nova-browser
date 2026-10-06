package com.nova.browser

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
    private var touched = false   // المستخدم تحكّم يدوياً: لا نستأنف التشغيل تلقائياً بعد ذلك

    val active: Boolean get() = wv != null

    // صفحات المشغّل المصغّر (الحالية أو التي أُغلقت للتو): نداءاتها المتأخرة يجب ألا تلمس حالة التبويب
    private val retired: MutableSet<WebView> = java.util.Collections.newSetFromMap(java.util.WeakHashMap<WebView, Boolean>())
    fun owns(v: WebView?): Boolean = v != null && (v === wv || retired.contains(v))

    private const val PROBE = "(function(){var v=document.querySelector('video');return v?(v.paused?0:1):-1})()"
    private const val RESUME = "(function(){var v=document.querySelector('video');if(v&&v.paused&&!v.ended){var p=v.play();if(p&&p.catch)p.catch(function(){})}})()"

    fun start(w: WebView, t: String, wasPlaying: Boolean) {
        if (wv != null && wv !== w) close()
        wv = w; title = t; playing = wasPlaying; touched = false
        w.evaluateJavascript("window.__novaYtApp&&window.__novaYtApp.mini(true)", null)
        // نقل الـ WebView بين الحاويات قد يوقف الفيديو لحظة؛ نستأنفه إن كان يعمل قبل التصغير
        if (wasPlaying) for (d in longArrayOf(350L, 1100L, 2400L)) w.postDelayed({
            if (wv === w && !touched) w.evaluateJavascript(RESUME, null)
        }, d)
    }

    fun toggle() {
        val w = wv ?: return
        touched = true
        val a = if (playing) "pause" else "play"
        playing = !playing
        w.evaluateJavascript("window.__novaYtCtl&&window.__novaYtCtl('$a')", null)
    }

    fun probe() {
        val w = wv ?: return
        w.evaluateJavascript(PROBE) { r -> if (wv === w && r != null && r != "-1" && r != "null") playing = r.trim('"') == "1" }
    }

    fun close() {
        val w = wv ?: return
        retired.add(w)
        wv = null
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
                            FrameLayout(ctx).apply {
                                (w.parent as? ViewGroup)?.removeView(w)
                                addView(w, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
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
