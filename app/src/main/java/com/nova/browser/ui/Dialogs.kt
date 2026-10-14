package com.nova.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * مربع الحوار الموحّد لكل التطبيق — بأسلوب تنبيهات iOS (UIAlertController): عنوان ورسالة في المنتصف،
 * زوايا 14dp بدل 28dp الدائرية السابقة، وأزرار نصيّة بلون أزرق النظام بدل الزر المملوء دائري الشكل
 * (الخطر/الحذف يصير نص أحمر، تماماً كتصرّف زر "Delete" في تنبيهات Safari/iOS).
 */
@Composable
fun NovaDialog(
    title: String,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = L("إغلاق"),
    onDismissClick: (() -> Unit)? = null,
    danger: Boolean = false,
    extraAction: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(14.dp),
        containerColor = cs.surface,
        titleContentColor = cs.onSurface,
        textContentColor = cs.onSurfaceVariant,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (icon != null) {
                    Box(
                        Modifier.padding(bottom = 10.dp).size(40.dp).clip(CircleShape)
                            .background(if (danger) cs.error.copy(alpha = 0.12f) else cs.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(icon, null, Modifier.size(21.dp), tint = if (danger) cs.error else cs.primary) }
                }
                Text(
                    title, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp), content = content
            )
        },
        confirmButton = {
            if (confirmText != null && onConfirm != null) TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (danger) cs.error else cs.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                extraAction?.invoke()
                if (dismissText != null) TextButton(onClick = onDismissClick ?: onDismiss) { Text(dismissText, color = cs.primary) }
            }
        }
    )
}

/** حوار اختيار واحد (لغة، مظهر، …): صفوف بحواف دائرية، والمختار مظلَّل بعلامة ✓ — يتبع هوية التطبيق بدل أزرار الراديو الافتراضية. */
@Composable
fun NovaChoiceDialog(
    title: String, options: List<String>, selected: Int, icon: ImageVector? = null,
    onSelect: (Int) -> Unit, onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    NovaDialog(title = title, onDismiss = onDismiss, icon = icon, dismissText = L("إغلاق")) {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { i, o ->
                val on = i == selected
                // صفّ بأسلوب قوائم إعدادات iOS: علامة ✓ زرقاء يمين العنصر المختار، بلا تظليل خلفية كامل
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(i); onDismiss() }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        o, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = cs.onSurface
                    )
                    if (on) Icon(Icons.Default.Check, null, Modifier.size(19.dp), tint = cs.primary)
                }
            }
        }
    }
}

/** نص شرح داخل الحوار بلون ثانوي. */
@Composable
fun DialogText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
