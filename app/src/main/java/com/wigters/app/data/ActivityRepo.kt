package com.wigters.app.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DailyActivity(val steps: Long, val km: Double, val kcal: Double)

/**
 * Passos, distância e energia de hoje via Health Connect.
 * O Honor Saúde envia esses dados para o Health Connect quando a ligação está ativa.
 */
object ActivityRepo {
    private const val PREFS = "activity"
    const val BACKGROUND_PERMISSION = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        BACKGROUND_PERMISSION,
    )

    fun available(context: Context) =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun cached(context: Context): DailyActivity? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getString("day", null) != LocalDate.now().toString()) return null
        return DailyActivity(p.getLong("steps", 0), p.getFloat("km", 0f).toDouble(), p.getFloat("kcal", 0f).toDouble())
    }

    suspend fun refresh(context: Context) {
        if (!available(context)) return
        runCatching {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (HealthPermission.getReadPermission(StepsRecord::class) !in granted) return
            val zone = ZoneId.systemDefault()
            val start = LocalDate.now().atStartOfDay(zone).toInstant()
            val result = client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        DistanceRecord.DISTANCE_TOTAL,
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                    ),
                    timeRangeFilter = TimeRangeFilter.between(start, Instant.now()),
                )
            )
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("day", LocalDate.now().toString())
                .putLong("steps", result[StepsRecord.COUNT_TOTAL] ?: 0L)
                .putFloat("km", (result[DistanceRecord.DISTANCE_TOTAL]?.inKilometers ?: 0.0).toFloat())
                .putFloat("kcal", (result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0).toFloat())
                .apply()
        }
    }
}
