package com.nova.browser

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** نص حالة التحديث لصف الإعدادات. */
@Composable
fun updateStatus(): String = when (Updater.phase) {
    UpdPhase.CHECKING -> L("جارٍ الفحص…")
    UpdPhase.UP_TO_DATE -> L("أنت على أحدث إصدار")
    UpdPhase.AVAILABLE -> L("إصدار جديد") + " " + (Updater.info?.version ?: "") + " — " + L("اضغط للتنزيل")
    UpdPhase.DOWNLOADING -> L("جارٍ التنزيل…") + " " + (Updater.progress * 100).toInt() + "%"
    UpdPhase.READY -> L("جاهز — اضغط للتثبيت") + (if (Updater.error.isNotBlank()) "\n" + Updater.error else "")
    UpdPhase.INSTALLING -> L("جارٍ التثبيت…")
    UpdPhase.ERROR -> L("فشل") + ": " + Updater.error
    UpdPhase.IDLE -> L("اضغط للفحص الآن")
}

/** نافذة «يوجد تحديث»: تنزيل بشريط تقدّم ثم تثبيت من داخل التطبيق. */
@Composable
fun UpdateDialog() {
    val ctx = LocalContext.current
    val i = Updater.info ?: return
    val busy = Updater.phase == UpdPhase.DOWNLOADING || Updater.phase == UpdPhase.INSTALLING
    AlertDialog(
        onDismissRequest = { if (!busy) Updater.dismissPrompt() },
        title = { Text(L("تحديث جديد") + " " + i.version) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                if (i.size > 0) Text(fmtSize(i.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (i.notes.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(i.notes.take(1200), style = MaterialTheme.typography.bodySmall) }
                if (Updater.phase == UpdPhase.DOWNLOADING) { Spacer(Modifier.height(12.dp)); LinearProgressIndicator(progress = { Updater.progress }, modifier = Modifier.fillMaxWidth()) }
                if (Updater.error.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(Updater.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                when (Updater.phase) { UpdPhase.READY -> Updater.install(ctx); else -> Updater.download(ctx) }
            }) {
                Text(when (Updater.phase) {
                    UpdPhase.READY -> L("تثبيت")
                    UpdPhase.DOWNLOADING -> L("جارٍ التنزيل…")
                    UpdPhase.INSTALLING -> L("جارٍ التثبيت…")
                    else -> L("تنزيل وتثبيت")
                })
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { Updater.dismissPrompt() }) { Text(L("لاحقاً")) } }
    )
}
