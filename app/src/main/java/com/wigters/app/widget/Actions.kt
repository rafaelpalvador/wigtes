package com.wigters.app.widget

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.Settings
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.wigters.app.data.SystemStats

/** Toque no cabeçalho: atualiza os números na hora. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAllWidgets(context)
    }
}

/** Liga/desliga a lanterna (não precisa de permissão). */
class ToggleTorchAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val cm = context.getSystemService(CameraManager::class.java)
        val id = SystemStats.torchCameraId(cm) ?: return
        val on = SystemStats.readTorchState(context) ?: false
        runCatching { cm.setTorchMode(id, !on) }
        updateAllWidgets(context)
    }
}

/**
 * Alterna entre som normal e silencioso.
 * Sem o acesso ao "Não perturbe" vai para vibrar; com o acesso, vai para silêncio total.
 */
class ToggleRingerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val audio = context.getSystemService(AudioManager::class.java)
        val target = if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            if (SystemStats.hasDndAccess(context)) AudioManager.RINGER_MODE_SILENT
            else AudioManager.RINGER_MODE_VIBRATE
        } else {
            AudioManager.RINGER_MODE_NORMAL
        }
        val ok = runCatching { audio.ringerMode = target }.isSuccess
        if (!ok) {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        updateAllWidgets(context)
    }
}

val BRIGHTNESS_STEP = ActionParameters.Key<Int>("brightness_step")

/** Brilho −/+ em passos de 10% (escala perceptual, como a barra do sistema). */
class BrightnessAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val step = parameters[BRIGHTNESS_STEP] ?: return
        if (!Settings.System.canWrite(context)) return
        val cr = context.contentResolver
        runCatching {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            val raw = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
            val pct = SystemStats.rawToPercent(raw)
            val next = SystemStats.percentToRaw((pct + step).coerceIn(1, 100))
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, next)
        }
        updateAllWidgets(context)
    }
}
