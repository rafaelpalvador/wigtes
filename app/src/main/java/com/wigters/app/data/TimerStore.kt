package com.wigters.app.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import com.wigters.app.widget.updateAllWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Timer de foco (25 min por padrão), com pausa e reinício. */
data class TimerState(val running: Boolean, val endElapsed: Long, val remainingMs: Long) {
    /** Base para o Chronometer em modo contagem regressiva. */
    val base: Long get() = if (running) endElapsed else SystemClock.elapsedRealtime() + remainingMs
}

object TimerStore {
    const val DEFAULT_MS = 25 * 60 * 1000L
    private const val PREFS = "timer"

    fun get(context: Context): TimerState {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val running = p.getBoolean("running", false)
        val end = p.getLong("end", 0)
        if (running && end <= SystemClock.elapsedRealtime()) {
            reset(context)
            return TimerState(false, 0, DEFAULT_MS)
        }
        return TimerState(running, end, p.getLong("remaining", DEFAULT_MS))
    }

    fun toggle(context: Context) {
        val s = get(context)
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (s.running) {
            p.putBoolean("running", false)
                .putLong("remaining", (s.endElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            cancelAlarm(context)
        } else {
            val end = SystemClock.elapsedRealtime() + s.remainingMs
            p.putBoolean("running", true).putLong("end", end)
            scheduleAlarm(context, end)
        }
        p.apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("running", false).putLong("remaining", DEFAULT_MS).apply()
        cancelAlarm(context)
    }

    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(
        context, 7, Intent(context, TimerDoneReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun scheduleAlarm(context: Context, end: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        runCatching {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, end, alarmIntent(context))
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, end, alarmIntent(context))
            }
        }
    }

    private fun cancelAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
    }
}

/** Fim do timer: vibra, toca o som de notificação e volta o widget para 25:00. */
class TimerDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TimerStore.reset(context)
        runCatching {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
                .vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
        }
        runCatching {
            RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { updateAllWidgets(context) } finally { pending.finish() }
        }
    }
}
