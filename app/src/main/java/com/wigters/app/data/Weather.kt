package com.wigters.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import com.wigters.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class WeatherInfo(
    val city: String,
    val tempC: Int,
    val maxC: Int,
    val minC: Int,
    val code: Int,
    val fetchedAt: Long,
) {
    val description: String
        get() = when (code) {
            0 -> "Céu limpo"
            1 -> "Quase limpo"
            2 -> "Parcialmente nublado"
            3 -> "Nublado"
            45, 48 -> "Nevoeiro"
            in 51..57 -> "Chuvisco"
            in 61..67, in 80..82 -> "Chuva"
            in 71..77, 85, 86 -> "Neve"
            in 95..99 -> "Trovoada"
            else -> "—"
        }

    val icon: Int
        get() = when (code) {
            0 -> R.drawable.wx_sun
            1, 2 -> R.drawable.wx_partly
            in 51..67, in 80..82, in 95..99 -> R.drawable.wx_rain
            else -> R.drawable.wx_cloud
        }
}

/**
 * Clima via Open-Meteo (gratuito, sem chave, europeu).
 * A localização é guardada quando o app é aberto; sem permissão, usa Viseu.
 */
object WeatherRepo {
    private const val PREFS = "weather"
    private const val DEFAULT_LAT = 40.6566
    private const val DEFAULT_LON = -7.9125
    private const val DEFAULT_CITY = "Viseu"
    private const val MAX_AGE_MS = 30 * 60 * 1000L

    fun cached(context: Context): WeatherInfo? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("temp")) return null
        return WeatherInfo(
            city = p.getString("city", DEFAULT_CITY) ?: DEFAULT_CITY,
            tempC = p.getInt("temp", 0),
            maxC = p.getInt("max", 0),
            minC = p.getInt("min", 0),
            code = p.getInt("code", -1),
            fetchedAt = p.getLong("at", 0),
        )
    }

    /** Chamado com o app aberto: guarda a última localização conhecida e o nome da cidade. */
    suspend fun saveLocation(context: Context) = withContext(Dispatchers.IO) {
        val granted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return@withContext
        val lm = context.getSystemService(LocationManager::class.java)
        val loc = runCatching {
            listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { lm.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
        }.getOrNull() ?: return@withContext
        @Suppress("DEPRECATION")
        val city = runCatching {
            Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)
                ?.firstOrNull()?.let { it.locality ?: it.subAdminArea }
        }.getOrNull()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("lat", loc.latitude.toFloat())
            .putFloat("lon", loc.longitude.toFloat())
            .apply { if (city != null) putString("loc_city", city) }
            .apply()
    }

    suspend fun refreshIfStale(context: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val age = System.currentTimeMillis() - p.getLong("at", 0)
        if (!force && age < MAX_AGE_MS) return@withContext
        val lat = p.getFloat("lat", DEFAULT_LAT.toFloat()).toDouble()
        val lon = p.getFloat("lon", DEFAULT_LON.toFloat()).toDouble()
        val city = p.getString("loc_city", null) ?: if (p.contains("lat")) "Aqui" else DEFAULT_CITY
        runCatching {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code" +
                    "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val json = JSONObject(body)
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            p.edit()
                .putString("city", city)
                .putInt("temp", Math.round(current.getDouble("temperature_2m")).toInt())
                .putInt("code", current.getInt("weather_code"))
                .putInt("max", Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt())
                .putInt("min", Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt())
                .putLong("at", System.currentTimeMillis())
                .apply()
        }
        Unit
    }
}
