package com.nova.browser

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * تكيّف ذكي مع حرارة الجهاز والبطارية.
 * level: 0 = أداء كامل، 1 = مخفَّف (حرارة متوسطة أو توفير الطاقة)، 2 = أدنى استهلاك (حرارة عالية).
 * يُستخدم لتقصير الأنيميشن، تخفيض معدل التحديث إلى 60Hz، وتحرير التبويبات الخلفية.
 */
object Adaptive {
    var level by mutableIntStateOf(0); private set
    private var thermal = PowerManager.THERMAL_STATUS_NONE
    private var saver = false
    private var started = false

    private fun recompute() {
        level = if (!Prefs.adaptive) 0 else when {
            thermal >= PowerManager.THERMAL_STATUS_SEVERE -> 2
            thermal >= PowerManager.THERMAL_STATUS_MODERATE || saver -> 1
            else -> 0
        }
    }

    fun refresh() = recompute()

    fun init(c: Context) {
        if (started) return
        started = true
        val app = c.applicationContext
        val pm = app.getSystemService(PowerManager::class.java) ?: return
        saver = pm.isPowerSaveMode
        thermal = pm.currentThermalStatus
        runCatching { pm.addThermalStatusListener(ContextCompat.getMainExecutor(app)) { s -> thermal = s; recompute() } }
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, i: Intent?) { saver = pm.isPowerSaveMode; recompute() }
        }, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        recompute()
    }

    /** مدة أنيميشن مناسبة للحالة الحالية (صفر عند الحرارة العالية أو إيقاف الحركة). */
    fun ms(base: Int): Int = when {
        !Prefs.smoothAnim -> 0
        level == 0 -> base
        level == 1 -> base * 6 / 10
        else -> 0
    }

    /** عدد التبويبات الحيّة المسموح به: يقلّ كلما سخن الجهاز. */
    fun liveCap(base: Int): Int = when (level) { 0 -> base; 1 -> (base - 1).coerceAtLeast(2); else -> 2 }
}
