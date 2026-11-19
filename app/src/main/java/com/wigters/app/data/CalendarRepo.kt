package com.wigters.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class NextEvent(val title: String, val whenText: String)

object CalendarRepo {

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Próximo evento (ainda não terminado) dos próximos 7 dias. */
    fun next(context: Context): NextEvent? {
        if (!hasPermission(context)) return null
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - 24 * 3600_000L)
            ContentUris.appendId(it, now + 7 * 24 * 3600_000L)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
        )
        val selection = "${CalendarContract.Instances.VISIBLE} = 1 AND " +
            "${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}"
        return runCatching {
            context.contentResolver.query(uri, projection, selection, null, "${CalendarContract.Instances.BEGIN} ASC")
                ?.use { c ->
                    while (c.moveToNext()) {
                        val title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "(sem título)"
                        val begin = c.getLong(1)
                        var end = c.getLong(2)
                        val allDay = c.getInt(3) == 1
                        if (allDay) {
                            // Eventos de dia inteiro vêm em UTC; converter para o fuso local.
                            val off = TimeZone.getDefault().getOffset(begin)
                            end -= off
                            if (end <= now) continue
                            return@use NextEvent(title, dayLabel(begin - off, now, null))
                        }
                        if (end <= now) continue
                        val time = if (begin <= now) "Agora" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(begin))
                        return@use NextEvent(title, dayLabel(begin, now, time))
                    }
                    null
                }
        }.getOrNull()
    }

    private fun dayLabel(at: Long, now: Long, time: String?): String {
        val a = Calendar.getInstance().apply { timeInMillis = at }
        val n = Calendar.getInstance().apply { timeInMillis = now }
        val dayDiff = ((startOfDay(a) - startOfDay(n)) / (24 * 3600_000L)).toInt()
        val day = when {
            dayDiff <= 0 -> if (time == null) "Hoje" else null
            dayDiff == 1 -> "Amanhã"
            else -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(at))
                .replaceFirstChar { it.uppercase() }.trimEnd('.')
        }
        return when {
            time == null -> "$day · Dia todo"
            day == null -> time
            else -> "$day · $time"
        }
    }

    private fun startOfDay(c: Calendar): Long = (c.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
