package com.wigters.app.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap

/** Lugares do widget onde você pode escolher qual app abre. */
enum class Slot(val key: String, val title: String, val hint: String) {
    CLOCK("clock", "Relógio", "Toque no relógio"),
    CALENDAR("calendar", "Agenda", "Toque no próximo evento"),
    WEATHER("weather", "Clima", "Toque no clima"),
    HEALTH("health", "Atividade", "Toque em passos/energia"),
    MUSIC("music", "Música", "Quando nada está tocando"),
    APP1("app1", "Atalho 1", "Fileira de apps"),
    APP2("app2", "Atalho 2", "Fileira de apps"),
    APP3("app3", "Atalho 3", "Fileira de apps"),
    APP4("app4", "Atalho 4", "Fileira de apps"),
    APP5("app5", "Atalho 5", "Fileira de apps"),
}

data class InstalledApp(val packageName: String, val label: String)

object AppPrefs {
    private const val PREFS = "apps"

    fun get(context: Context, slot: Slot): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(slot.key, null)
            ?.takeIf { context.packageManager.getLaunchIntentForPackage(it) != null }

    fun set(context: Context, slot: Slot, packageName: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (packageName == null) remove(slot.key) else putString(slot.key, packageName)
        }.apply()
    }

    fun launchIntent(context: Context, slot: Slot): Intent? =
        get(context, slot)?.let { context.packageManager.getLaunchIntentForPackage(it) }

    fun label(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    fun icon(context: Context, packageName: String, sizePx: Int = 112): Bitmap? = runCatching {
        context.packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx)
    }.getOrNull()

    /** Apps que aparecem na gaveta de apps, em ordem alfabética. */
    fun installed(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
