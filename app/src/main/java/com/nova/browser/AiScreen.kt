package com.nova.browser

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * الواجهة الأصلية لوضع الذكاء الاصطناعي: شريط علوي، قائمة رسائل بتنسيق Markdown، وشريط إرسال مع رفع ملفات.
 * صفحة الموقع تبقى حيّة خلفها وتعمل كـ API (انظر Ai.kt / ai.js).
 */
@Composable
fun AiScreen(tab: BrowserTab, info: SiteInfo, onShowSite: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val s = tab.ai
    // Surface يمتص اللمس فلا يصل إلى صفحة الموقع الحيّة خلف الواجهة
    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(Modifier.fillMaxSize()) {
            AiTopBar(tab, info, onShowSite)
            Box(Modifier.weight(1f).fillMaxWidth()) { AiMessages(tab, info, onShowSite) }
            AiComposer(tab)
        }
    }
    s.diag?.let { d ->
        AlertDialog(
            onDismissRequest = { s.diag = null }, shape = RoundedCornerShape(24.dp), containerColor = cs.surfaceContainerHigh,
            title = { Text(L("تشخيص الاتصال بالموقع")) },
            text = { Text(d, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = cs.onSurface) },
            confirmButton = { TextButton(onClick = { copyText(ctx, d); s.diag = null }) { Text(L("نسخ"), color = cs.tertiary) } },
            dismissButton = { TextButton(onClick = { s.diag = null }) { Text(L("إغلاق"), color = cs.onSurfaceVariant) } }
        )
    }
}

@Composable
private fun AiTopBar(tab: BrowserTab, info: SiteInfo, onShowSite: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val s = tab.ai
    var menu by remember { mutableStateOf(false) }
    val status = when {
        s.busy -> L("يكتب…")
        !s.seen -> L("جارٍ التحميل…")
        s.ready -> L("جاهز")
        else -> L("سجّل الدخول من الموقع")
    }
    Column(Modifier.fillMaxWidth().background(cs.background)) {
        Row(
            Modifier.fillMaxWidth().height(UiLayout.BAR_ROW_DP.dp).background(siteWash(cs)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(11.dp))
                    .background(Brush.linearGradient(listOf(cs.tertiary, lerp(cs.tertiary, cs.primaryContainer, 0.45f)))),
                contentAlignment = Alignment.Center
            ) { SparkGlyph(cs.onTertiary, Modifier.size(18.dp)) }
            Column(Modifier.weight(1f)) {
                Text(info.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, color = cs.onSurface)
                Text(status, style = MaterialTheme.typography.labelSmall, color = if (s.busy) cs.tertiary else cs.onSurfaceVariant, maxLines = 1)
            }
            GlyphButton(L("محادثة جديدة"), { Ai.newChat(tab) }) { c -> PlusGlyph(c, Modifier.size(18.dp)) }
            GlyphButton(L("عرض الموقع"), onShowSite) { c -> SiteGlyph(c, Modifier.size(19.dp)) }
            Box {
                GlyphButton(L("المزيد"), { menu = true }, tonal = false) { c -> Icon(Icons.Default.MoreVert, null, Modifier.size(20.dp), tint = c) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(L("تشخيص الاتصال بالموقع")) }, onClick = { menu = false; Ai.diagnose(tab) })
                    DropdownMenuItem(text = { Text(L("نسخ المحادثة")) }, onClick = {
                        menu = false
                        copyText(ctx, s.msgs.joinToString("\n\n") { (if (it.user) "• " else "") + it.text })
                    })
                    DropdownMenuItem(text = { Text(L("إعادة تحميل الموقع")) }, onClick = { menu = false; tab.webView?.reload() })
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant.copy(alpha = 0.7f)))
    }
}

@Composable
private fun AiMessages(tab: BrowserTab, info: SiteInfo, onShowSite: () -> Unit) {
    val s = tab.ai
    val ctx = LocalContext.current
    val list = s.msgs
    val opt = s.optimistic
    val typing = s.busy && (list.lastOrNull()?.user != false)   // ينتظر أول حرف من الرد
    val total = list.size + (if (opt != null) 1 else 0) + (if (typing) 1 else 0)
    val state = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(state.isScrollInProgress) { if (!state.isScrollInProgress) follow = !state.canScrollForward }
    LaunchedEffect(opt) { if (opt != null) follow = true }
    val tailLen = list.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(total, tailLen) { if (follow && total > 0) state.scrollBy(1_000_000f) }

    when {
        total == 0 && s.seen && !s.ready -> NoInput(onShowSite)
        total == 0 -> AiEmpty(info)
        else -> LazyColumn(
            state = state, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            itemsIndexed(list) { _, m -> AiBubble(m) }
            if (opt != null) item { AiBubble(AiMsg(true, opt)) }
            if (typing) item { TypingDots() }
        }
    }
}

@Composable
private fun AiBubble(m: AiMsg) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    if (m.user) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Surface(shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp), color = cs.secondaryContainer, modifier = Modifier.widthIn(max = 320.dp)) {
                Text(
                    m.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content), color = cs.onSecondaryContainer
                )
            }
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Markdown(m.text)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ActionText(L("نسخ")) { copyText(ctx, m.text) }
                ActionText(L("مشاركة")) { shareText(ctx, m.text) }
            }
        }
    }
}

@Composable
private fun ActionText(label: String, onClick: () -> Unit) {
    Text(
        label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

@Composable
private fun TypingDots() {
    val cs = MaterialTheme.colorScheme
    val t = rememberInfiniteTransition(label = "dots")
    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 160), RepeatMode.Reverse), label = "d$i")
            Box(Modifier.size(7.dp).clip(CircleShape).background(cs.tertiary.copy(alpha = if (Adaptive.ms(100) > 0) a else 0.7f)))
        }
    }
}

@Composable
private fun AiEmpty(info: SiteInfo) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(cs.tertiary, lerp(cs.tertiary, cs.primaryContainer, 0.45f)))),
            contentAlignment = Alignment.Center
        ) { SparkGlyph(cs.onTertiary, Modifier.size(34.dp)) }
        Spacer(Modifier.height(18.dp))
        Text(L("كيف أساعدك اليوم؟"), style = MaterialTheme.typography.headlineSmall, color = cs.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(info.name, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun NoInput(onShowSite: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(22.dp), color = cs.surfaceContainer) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(L("لا أجد حقل الكتابة في الصفحة"), style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Text(
                    L("قد تحتاج إلى تسجيل الدخول، أو أن الموقع غيّر تصميمه. افتح الموقع وسجّل الدخول ثم ارجع."),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                )
                Button(onClick = onShowSite, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = cs.tertiary, contentColor = cs.onTertiary)) {
                    Text(L("فتح الموقع"), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ───────────────────────── شريط الإرسال ─────────────────────────

@Composable
private fun AiComposer(tab: BrowserTab) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val s = tab.ai
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.take((4 - s.files.size).coerceAtLeast(0)).forEach { s.files.add(Ai.describe(ctx, it)) }
    }
    val canSend = s.ready && !s.busy && (s.draft.isNotBlank() || s.files.isNotEmpty())
    fun submit() { if (!canSend) return; val t = s.draft.trim(); s.draft = ""; Ai.send(tab, t) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
        s.note?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = cs.error, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) }
        Surface(shape = RoundedCornerShape(26.dp), color = cs.surfaceContainerHigh, border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f))) {
            Column(Modifier.padding(6.dp)) {
                if (s.files.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.files.toList().forEach { f -> FileChip(f) { s.files.remove(f) } }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    AiRoundBtn(enabled = s.files.size < 4 && !s.busy, bg = Color.Transparent, onClick = { picker.launch(arrayOf("*/*")) }) { PlusGlyph(cs.onSurfaceVariant, Modifier.size(20.dp)) }
                    BasicTextField(
                        value = s.draft, onValueChange = { s.draft = it }, maxLines = 6,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface, textDirection = TextDirection.Content),
                        cursorBrush = SolidColor(cs.tertiary),
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 11.dp),
                        decorationBox = { inner ->
                            Box {
                                if (s.draft.isEmpty()) Text(L("اكتب رسالتك…"), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant.copy(alpha = 0.8f))
                                inner()
                            }
                        }
                    )
                    if (s.busy) AiRoundBtn(enabled = true, bg = cs.onSurface, onClick = { Ai.stop(tab) }) { StopGlyph(cs.surface, Modifier.size(18.dp)) }
                    else AiRoundBtn(enabled = canSend, bg = if (canSend) cs.tertiary else cs.surfaceContainerHighest, onClick = { submit() }) {
                        SendGlyph(if (canSend) cs.onTertiary else cs.onSurfaceVariant, Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AiRoundBtn(enabled: Boolean, bg: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(bg).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun FileChip(f: AiFile, onRemove: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = cs.secondaryContainer) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (f.name.length > 22) f.name.take(20) + "…" else f.name, style = MaterialTheme.typography.labelMedium, color = cs.onSecondaryContainer)
            Box(Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
                Text("×", color = cs.onSecondaryContainer, fontSize = 16.sp)
            }
        }
    }
}

// ───────────────────────── Markdown خفيف ─────────────────────────

private sealed class Blk
private class BCode(val lang: String, val code: String) : Blk()
private class BText(val s: String, val kind: Int, val indent: Int, val bullet: String) : Blk()   // kind: 0 فقرة، 1-3 عنوان، 4 اقتباس

private val reHead = Regex("^(#{1,3})\\s+(.*)")
private val reItem = Regex("^(\\s*)([-*+]|\\d+\\.)\\s+(.*)")
private val reInline = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`|\\*([^*\\s][^*]*?)\\*|\\[([^\\]]+)]\\(([^)]+)\\)")

private fun parseMd(src: String): List<Blk> {
    val out = ArrayList<Blk>()
    val par = StringBuilder()
    fun flush() { if (par.isNotBlank()) out += BText(par.toString().trim(), 0, 0, ""); par.setLength(0) }
    var inCode = false; var lang = ""; val code = StringBuilder()
    for (raw in src.lines()) {
        if (raw.trimStart().startsWith("```")) {
            if (inCode) { out += BCode(lang, code.toString().trimEnd('\n')); code.setLength(0); inCode = false }
            else { flush(); inCode = true; lang = raw.trim().removePrefix("```").trim() }
            continue
        }
        if (inCode) { code.append(raw).append('\n'); continue }
        val l = raw.trimEnd()
        val h = reHead.find(l)
        val li = reItem.find(l)
        when {
            l.isBlank() -> flush()
            h != null -> { flush(); out += BText(h.groupValues[2], h.groupValues[1].length, 0, "") }
            li != null -> {
                flush()
                val num = li.groupValues[2]
                out += BText(li.groupValues[3], 0, li.groupValues[1].length / 2, if (num[0].isDigit()) num else "•")
            }
            l.startsWith("> ") -> { flush(); out += BText(l.removePrefix("> "), 4, 0, "") }
            l.trim() == "---" -> flush()
            else -> { if (par.isNotEmpty()) par.append('\n'); par.append(l) }
        }
    }
    if (inCode) out += BCode(lang, code.toString().trimEnd('\n'))
    flush()
    return out
}

@Composable
private fun inlineText(s: String): androidx.compose.ui.text.AnnotatedString {
    val cs = MaterialTheme.colorScheme
    return remember(s, cs) {
        buildAnnotatedString {
            var i = 0
            for (m in reInline.findAll(s)) {
                append(s.substring(i, m.range.first))
                val g = m.groupValues
                when {
                    g[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[1]) }
                    g[2].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = cs.surfaceContainerHighest, fontSize = 0.9.em)) { append(g[2]) }
                    g[3].isNotEmpty() -> withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { append(g[3]) }
                    else -> withStyle(SpanStyle(color = cs.tertiary, textDecoration = TextDecoration.Underline)) { append(g[4]) }
                }
                i = m.range.last + 1
            }
            append(s.substring(i))
        }
    }
}

@Composable
private fun Markdown(text: String) {
    val cs = MaterialTheme.colorScheme
    val ty = MaterialTheme.typography
    val blocks = remember(text) { parseMd(text) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { b ->
            when (b) {
                is BCode -> CodeBlock(b.lang, b.code)
                is BText -> {
                    val base = when (b.kind) { 1 -> ty.titleLarge; 2 -> ty.titleMedium; 3 -> ty.titleSmall; else -> ty.bodyLarge }
                    val style = base.copy(textDirection = TextDirection.Content, fontWeight = if (b.kind in 1..3) FontWeight.Bold else base.fontWeight)
                    Row(Modifier.padding(start = (b.indent * 14).dp)) {
                        if (b.bullet.isNotEmpty()) Text(b.bullet, Modifier.width(24.dp), style = style, color = cs.tertiary)
                        Text(inlineText(b.s), style = style, color = if (b.kind == 4) cs.onSurfaceVariant else cs.onSurface, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(lang: String, code: String) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(shape = RoundedCornerShape(14.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(lang.ifBlank { "code" }, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(L("نسخ"), style = MaterialTheme.typography.labelMedium, color = cs.tertiary, modifier = Modifier.clickable { copyText(ctx, code) })
                }
                Text(code, Modifier.horizontalScroll(rememberScrollState()).padding(12.dp), fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = cs.onSurface)
            }
        }
    }
}

// ───────────────────────── رموز مرسومة ─────────────────────────

@Composable
private fun SendGlyph(color: Color, modifier: Modifier) = Canvas(modifier) {
    val w = size.width; val h = size.height; val sw = w * 0.12f
    drawLine(color, Offset(w * 0.5f, h * 0.82f), Offset(w * 0.5f, h * 0.2f), sw, StrokeCap.Round)
    val p = Path().apply { moveTo(w * 0.24f, h * 0.46f); lineTo(w * 0.5f, h * 0.2f); lineTo(w * 0.76f, h * 0.46f) }
    drawPath(p, color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun StopGlyph(color: Color, modifier: Modifier) = Canvas(modifier) {
    drawRoundRect(color, Offset(size.width * 0.2f, size.height * 0.2f), Size(size.width * 0.6f, size.height * 0.6f), CornerRadius(size.width * 0.14f))
}

@Composable
private fun PlusGlyph(color: Color, modifier: Modifier) = Canvas(modifier) {
    val w = size.width; val h = size.height; val sw = w * 0.11f
    drawLine(color, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.5f, h * 0.82f), sw, StrokeCap.Round)
    drawLine(color, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.82f, h * 0.5f), sw, StrokeCap.Round)
}

@Composable
private fun SiteGlyph(color: Color, modifier: Modifier) = Canvas(modifier) {
    val w = size.width; val h = size.height; val sw = w * 0.09f
    drawRoundRect(color, Offset(w * 0.12f, h * 0.18f), Size(w * 0.76f, h * 0.64f), CornerRadius(w * 0.12f), style = Stroke(width = sw))
    drawLine(color, Offset(w * 0.12f, h * 0.38f), Offset(w * 0.88f, h * 0.38f), sw, StrokeCap.Round)
}
