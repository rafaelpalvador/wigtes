package com.wigters.app.data

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.Settings
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/** Fotografia do estado do aparelho num dado momento. */
data class SystemStats(
    val deviceName: String,
    val androidVersion: String,
    val soc: String?,
    val ramPercent: Int,
    val cpuPercent: Int?,          // aproximação pela frequência atual dos núcleos
    val brightnessPercent: Int?,
    val batteryPercent: Int,
    val charging: Boolean,
    val uptime: String,
    val storageUsedBytes: Long,
    val storageTotalBytes: Long,
    val wifiOn: Boolean,
    val airplaneOn: Boolean,
    val ringerSilent: Boolean,
    val torchOn: Boolean,
) {
    val storageFraction: Float
        get() = if (storageTotalBytes > 0) storageUsedBytes.toFloat() / storageTotalBytes else 0f

    companion object {
        suspend fun read(context: Context): SystemStats {
            val cr = context.contentResolver

            val name = Settings.Global.getString(cr, Settings.Global.DEVICE_NAME)
                ?.takeIf { it.isNotBlank() } ?: Build.MODEL

            // RAM
            val am = context.getSystemService(ActivityManager::class.java)
            val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val ram = if (mem.totalMem > 0) ((mem.totalMem - mem.availMem) * 100 / mem.totalMem).toInt() else 0

            // Brilho (0–255 na maioria dos aparelhos)
            val rawBrightness = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, -1)
            val brightness = if (rawBrightness >= 0) (rawBrightness * 100 / 255).coerceIn(0, 100) else null

            // Bateria
            val bm = context.getSystemService(BatteryManager::class.java)
            val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            // Armazenamento
            var used = 0L
            var total = 0L
            runCatching {
                val ssm = context.getSystemService(StorageStatsManager::class.java)
                total = ssm.getTotalBytes(StorageManager.UUID_DEFAULT)
                used = total - ssm.getFreeBytes(StorageManager.UUID_DEFAULT)
            }

            // Conectividade e som
            val wifi = runCatching {
                context.getSystemService(WifiManager::class.java).isWifiEnabled
            }.getOrDefault(false)
            val airplane = Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
            val audio = context.getSystemService(AudioManager::class.java)
            val silent = audio.ringerMode != AudioManager.RINGER_MODE_NORMAL

            return SystemStats(
                deviceName = name,
                androidVersion = "Android ${Build.VERSION.RELEASE}",
                soc = Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN },
                ramPercent = ram,
                cpuPercent = cpuFrequencyPercent(),
                brightnessPercent = brightness,
                batteryPercent = battery,
                charging = charging,
                uptime = formatUptime(SystemClock.elapsedRealtime()),
                storageUsedBytes = used,
                storageTotalBytes = total,
                wifiOn = wifi,
                airplaneOn = airplane,
                ringerSilent = silent,
                torchOn = readTorchState(context) ?: false,
            )
        }

        /** O Android não deixa apps lerem o uso real da CPU; usamos a frequência atual vs. máxima. */
        private fun cpuFrequencyPercent(): Int? = runCatching {
            val cpus = File("/sys/devices/system/cpu")
                .listFiles { f -> f.name.matches(Regex("cpu\\d+")) } ?: return null
            var cur = 0L
            var max = 0L
            for (cpu in cpus) {
                val c = File(cpu, "cpufreq/scaling_cur_freq").readLongOrNull() ?: continue
                val m = File(cpu, "cpufreq/cpuinfo_max_freq").readLongOrNull() ?: continue
                cur += c
                max += m
            }
            if (max > 0) (cur * 100 / max).toInt() else null
        }.getOrNull()

        private fun File.readLongOrNull(): Long? =
            runCatching { readText().trim().toLong() }.getOrNull()

        private fun formatUptime(ms: Long): String {
            val totalMin = ms / 60_000
            val days = totalMin / (60 * 24)
            val hours = (totalMin / 60) % 24
            val mins = totalMin % 60
            return if (days > 0) "${days}d ${hours}h" else "${hours}h ${mins}m"
        }

        /** Lê o estado da lanterna registrando um callback por um instante. */
        suspend fun readTorchState(context: Context): Boolean? {
            val cm = context.getSystemService(CameraManager::class.java)
            val id = torchCameraId(cm) ?: return null
            return withTimeoutOrNull(500) {
                suspendCancellableCoroutine { cont ->
                    val cb = object : CameraManager.TorchCallback() {
                        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                            if (cameraId == id && cont.isActive) {
                                cm.unregisterTorchCallback(this)
                                cont.resume(enabled)
                            }
                        }
                    }
                    cm.registerTorchCallback(cb, Handler(Looper.getMainLooper()))
                    cont.invokeOnCancellation { cm.unregisterTorchCallback(cb) }
                }
            }
        }

        fun torchCameraId(cm: CameraManager): String? = cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }

        fun hasDndAccess(context: Context): Boolean =
            context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
    }
}
