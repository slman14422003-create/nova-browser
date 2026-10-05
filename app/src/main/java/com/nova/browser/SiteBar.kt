package com.nova.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * الشريط العلوي لوضع التطبيق: هوية الموقع + حالة التحميل + الإجراءات.
 * ألوانه من ثيم التطبيق (primaryContainer / tertiary) فيتطابق مع باقي الواجهة فاتحاً وداكناً.
 * onPip/onDownload يظهران فقط لفيديو يوتيوب.
 */
@Composable
fun SiteBar(
    info: SiteInfo, progress: Float, loading: Boolean,
    onReload: () -> Unit, onShare: () -> Unit,
    onPip: (() -> Unit)? = null, onDownload: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), color = cs.surfaceContainer) {
        Column {
            Row(
                Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(cs.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (info.kind == SiteKind.AI) Icons.Default.Star else Icons.Default.PlayArrow, null, Modifier.size(20.dp), tint = cs.onPrimaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(info.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            L("تطبيق"), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = cs.onTertiary,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(cs.tertiary).padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                        Text(info.host, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (onPip != null) BarAction(L("منبثق"), Icons.Default.PlayArrow, false, onPip)
                if (onDownload != null) BarAction(L("تنزيل"), Icons.Default.KeyboardArrowDown, true, onDownload)
                if (onPip == null && onDownload == null) {
                    BarIcon(Icons.Default.Refresh, onReload)
                    BarIcon(Icons.Default.Share, onShare)
                }
            }
            Box(Modifier.fillMaxWidth().height(2.dp)) {
                if (loading && progress in 0.001f..0.999f)
                    LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth(), color = cs.tertiary, trackColor = cs.surfaceContainer)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant))
        }
    }
}

@Composable
private fun BarAction(label: String, icon: ImageVector, accent: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick, interactionSource = src, shape = RoundedCornerShape(14.dp),
        color = if (accent) cs.tertiary else cs.primaryContainer,
        contentColor = if (accent) cs.onTertiary else cs.onPrimaryContainer,
        modifier = Modifier.pressScale(src)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BarIcon(icon: ImageVector, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick, interactionSource = src, shape = RoundedCornerShape(12.dp), color = cs.surfaceContainerHigh,
        modifier = Modifier.size(36.dp).pressScale(src)
    ) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(19.dp), tint = cs.onSurface) } }
}
