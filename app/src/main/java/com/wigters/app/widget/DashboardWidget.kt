package com.wigters.app.widget

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.wigters.app.R
import com.wigters.app.data.ActivityRepo
import com.wigters.app.data.AppPrefs
import com.wigters.app.data.Slot
import com.wigters.app.data.CalendarRepo
import com.wigters.app.data.DailyActivity
import com.wigters.app.data.Media
import com.wigters.app.data.NextEvent
import com.wigters.app.data.NowPlaying
import com.wigters.app.data.TimerState
import com.wigters.app.data.TimerStore
import com.wigters.app.data.WeatherInfo
import com.wigters.app.data.WeatherRepo
import com.wigters.app.work.RefreshWorker
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DashboardData(
    val nextAlarm: String?,
    val month: String,
    val day: String,
    val event: NextEvent?,
    val calendarAllowed: Boolean,
    val weather: WeatherInfo?,
    val activity: DailyActivity?,
    val timer: TimerState,
    val media: Media?,
    val mediaAllowed: Boolean,
    val shortcuts: List<Shortcut>,
) {
    companion object {
        fun read(context: Context): DashboardData {
            val now = Date()
            val alarm = context.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime
            return DashboardData(
                nextAlarm = alarm?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it)) },
                month = SimpleDateFormat("MMM", Locale.getDefault()).format(now).trimEnd('.').uppercase(),
                day = SimpleDateFormat("d", Locale.getDefault()).format(now),
                event = CalendarRepo.next(context),
                calendarAllowed = CalendarRepo.hasPermission(context),
                weather = WeatherRepo.cached(context),
                activity = ActivityRepo.cached(context),
                timer = TimerStore.get(context),
                media = NowPlaying.read(context),
                mediaAllowed = NowPlaying.hasAccess(context),
                shortcuts = Shortcut.load(context),
            )
        }
    }
}

/** Um botão da fileira de apps: app escolhido (com o ícone real) ou o padrão. */
data class Shortcut(
    val key: String,
    val label: String,
    val intent: Intent,
    val appIcon: android.graphics.Bitmap?,
    val defaultIcon: Int,
    val bg: Color,
    val tint: Color,
) {
    companion object {
        private val defaults = listOf(
            Triple(Slot.APP1, R.drawable.ic_music, Triple("Música", Glass.pink, Color.White)),
            Triple(Slot.APP2, R.drawable.ic_message, Triple("Mensagens", Glass.green, Color.White)),
            Triple(Slot.APP3, R.drawable.ic_camera, Triple("Câmera", Color(0xFF3A3A3C), Color.White)),
            Triple(Slot.APP4, R.drawable.ic_tune, Triple("Ajustes", Glass.gray, Color.White)),
            Triple(Slot.APP5, R.drawable.ic_calendar, Triple("Agenda", Color.White, Glass.red)),
        )

        private fun defaultIntent(slot: Slot): Intent = when (slot) {
            Slot.APP1 -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
            Slot.APP2 -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
            Slot.APP3 -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            Slot.APP4 -> Intent(Settings.ACTION_SETTINGS)
            else -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
        }

        fun load(context: Context): List<Shortcut> = defaults.map { (slot, icon, style) ->
            val pkg = AppPrefs.get(context, slot)
            val intent = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
            if (pkg != null && intent != null) {
                Shortcut("${slot.key}-$pkg", AppPrefs.label(context, pkg), intent,
                    AppPrefs.icon(context, pkg), icon, style.second, style.third)
            } else {
                Shortcut(slot.key, style.first, defaultIntent(slot), null, icon, style.second, style.third)
            }
        }
    }
}

class DashboardWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = DashboardData.read(context)
        provideContent { DashboardContent(data) }
    }
}

class DashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DashboardWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedule(context)
        RefreshWorker.runNow(context)
    }
}

@Composable
internal fun DashboardContent(d: DashboardData, endInset: androidx.compose.ui.unit.Dp = 0.dp.k()) =
    Scaled(DASHBOARD_NATURAL_HEIGHT) { DashboardBody(d, endInset) }

val DASHBOARD_NATURAL_HEIGHT = 535.dp

@Composable
private fun DashboardBody(d: DashboardData, endInset: androidx.compose.ui.unit.Dp) {
    GlassPanel {
        ClockHeader(d)
        VGap()
        EventRow(d)
        VGap()
        WeatherRow(d.weather)
        VGap()
        ActivityRow(d.activity)
        VGap()
        TimerRow(d.timer)
        VGap()
        AppsRow(d.shortcuts)
        VGap()
        MusicRow(d.media, d.mediaAllowed)
    }
}

@Composable
private fun ClockHeader(d: DashboardData) {
    val ctx = LocalContext.current
    GlassTile(
        modifier = GlanceModifier.fillMaxWidth(),
        onClick = launch(ctx, "clock", AppPrefs.launchIntent(ctx, Slot.CLOCK) ?: Intent(AlarmClock.ACTION_SHOW_ALARMS)),
        vPad = 12.dp.k(), hPad = 16.dp.k()
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AndroidRemoteViews(
                remoteViews = RemoteViews(ctx.packageName, R.layout.widget_clock),
                modifier = GlanceModifier.defaultWeight()
            )
            d.nextAlarm?.let {
                Box(
                    modifier = GlanceModifier.cornerRadius(14.dp.k()).background(cp(Glass.off))
                        .padding(horizontal = 10.dp.k(), vertical = 6.dp.k())
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_alarm),
                            contentDescription = "Próximo alarme",
                            colorFilter = ColorFilter.tint(Glass.white),
                            modifier = GlanceModifier.size(15.dp.k())
                        )
                        HGap(5.dp.k())
                        Label(it, color = Glass.white, size = 13.sp.k())
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(d: DashboardData) {
    val ctx = LocalContext.current
    val open = launch(ctx, "calendar-${AppPrefs.get(ctx, Slot.CALENDAR)}",
        AppPrefs.launchIntent(ctx, Slot.CALENDAR)
            ?: Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()),
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
    GlassTile(modifier = GlanceModifier.fillMaxWidth(), onClick = open, vPad = 9.dp.k()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = GlanceModifier.size(42.dp.k()).background(ImageProvider(R.drawable.date_badge)).cornerRadius(12.dp.k()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(d.month, style = TextStyle(color = cp(Glass.red), fontSize = 9.sp.k(), fontWeight = FontWeight.Bold))
                Text(d.day, style = TextStyle(color = cp(Color(0xFF1C1C1E)), fontSize = 17.sp.k(), fontWeight = FontWeight.Bold))
            }
            HGap(12.dp.k())
            Box(GlanceModifier.size(3.dp.k(), 30.dp.k()).cornerRadius(2.dp.k()).background(cp(Glass.accent)), content = {})
            HGap(9.dp.k())
            Column(GlanceModifier.defaultWeight()) {
                when {
                    !d.calendarAllowed -> {
                        Value("Agenda", size = 14.sp.k(), color = cp(Glass.accent))
                        Label("Abra o Wigters para liberar", color = Glass.text2)
                    }
                    d.event == null -> {
                        Value("Sem eventos", size = 14.sp.k(), color = cp(Glass.accent))
                        Label("Nos próximos 7 dias", color = Glass.text2)
                    }
                    else -> {
                        Value(d.event.title, size = 14.sp.k(), color = cp(Glass.accent))
                        Label(d.event.whenText, color = Glass.white, size = 13.sp.k())
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherRow(w: WeatherInfo?) {
    val ctx = LocalContext.current
    val app = AppPrefs.launchIntent(ctx, Slot.WEATHER)
    val onClick = if (app != null) launch(ctx, "weather-${AppPrefs.get(ctx, Slot.WEATHER)}", app)
        else actionRunCallback<RefreshWeatherAction>()
    GlassTile(modifier = GlanceModifier.fillMaxWidth(), onClick = onClick, vPad = 10.dp.k()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Label(w?.let { "${it.city} · ${it.description}" } ?: "Clima", color = Glass.text2, size = 13.sp.k())
                Value(w?.let { "${it.tempC}°" } ?: "—", size = 30.sp.k())
            }
            Column(horizontalAlignment = Alignment.End) {
                Image(
                    provider = ImageProvider(w?.icon ?: R.drawable.wx_cloud),
                    contentDescription = w?.description,
                    modifier = GlanceModifier.size(30.dp.k())
                )
                VGap(4.dp.k())
                Label(w?.let { "${it.maxC}°  ${it.minC}°" } ?: "Toque p/ atualizar", color = Glass.text2, size = 13.sp.k())
            }
        }
    }
}

@Composable
private fun ActivityRow(a: DailyActivity?) {
    val ctx = LocalContext.current
    val onClick = launch(ctx, "health-${AppPrefs.get(ctx, Slot.HEALTH)}",
        AppPrefs.launchIntent(ctx, Slot.HEALTH) ?: Intent("android.health.connect.action.HEALTH_HOME_SETTINGS"),
        Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"))
    GlassTile(modifier = GlanceModifier.fillMaxWidth(), vPad = 10.dp.k(), onClick = onClick) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Stat("Passos", a?.let { String.format(Locale.getDefault(), "%,d", it.steps) } ?: "—", GlanceModifier.defaultWeight())
            Stat("Distância", a?.let { String.format(Locale.getDefault(), "%.1f km", it.km) } ?: "—", GlanceModifier.defaultWeight())
            Stat("Energia", a?.let { "${it.kcal.toInt()} kcal" } ?: "—", GlanceModifier.defaultWeight())
            RoundButton(R.drawable.ic_walk, "Atividade", Glass.green.copy(alpha = 0.85f), null, size = 36.dp.k(), iconSize = 20.dp.k())
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: GlanceModifier) {
    Column(modifier) {
        Label(label, color = cp(Glass.coral), size = 11.sp.k())
        Value(value, size = 15.sp.k())
    }
}

@Composable
private fun TimerRow(t: TimerState) {
    val ctx = LocalContext.current
    val rv = RemoteViews(ctx.packageName, R.layout.widget_timer).apply {
        setChronometer(R.id.timer_chrono, t.base, null, t.running)
        setChronometerCountDown(R.id.timer_chrono, true)
    }
    GlassTile(modifier = GlanceModifier.fillMaxWidth(), vPad = 9.dp.k()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(if (t.running) R.drawable.ic_pause else R.drawable.ic_play,
                if (t.running) "Pausar" else "Iniciar",
                Glass.orange.copy(alpha = 0.22f), actionRunCallback<TimerToggleAction>(),
                size = 38.dp.k(), iconSize = 20.dp.k(), tint = Glass.orange)
            HGap(8.dp.k())
            RoundButton(R.drawable.ic_close, "Reiniciar", Glass.off, actionRunCallback<TimerResetAction>(),
                size = 38.dp.k(), iconSize = 18.dp.k())
            HGap(10.dp.k())
            Label(if (t.running) "Foco" else "Timer", color = Glass.text3, size = 12.sp.k())
            Spacer(GlanceModifier.defaultWeight())
            AndroidRemoteViews(remoteViews = rv)
        }
    }
}

@Composable
private fun AppsRow(shortcuts: List<Shortcut>) {
    val ctx = LocalContext.current
    GlassTile(modifier = GlanceModifier.fillMaxWidth(), vPad = 10.dp.k(), hPad = 8.dp.k()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            shortcuts.forEach { sc ->
                Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                    val action = launch(ctx, sc.key, sc.intent)
                    if (sc.appIcon != null) {
                        Image(
                            provider = ImageProvider(sc.appIcon),
                            contentDescription = sc.label,
                            modifier = GlanceModifier.size(46.dp.k()).clickable(action)
                        )
                    } else {
                        RoundButton(sc.defaultIcon, sc.label, sc.bg, action,
                            size = 46.dp.k(), iconSize = 24.dp.k(), tint = sc.tint, radius = 13.dp.k())
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicRow(m: Media?, allowed: Boolean) {
    val ctx = LocalContext.current
    val pkg = m?.packageName ?: AppPrefs.get(ctx, Slot.MUSIC)
    val openApp = pkg?.let { p ->
        ctx.packageManager.getLaunchIntentForPackage(p)?.let { launch(ctx, "media-$p", it) }
    } ?: launch(ctx, "media-default", Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC))
    GlassTile(modifier = GlanceModifier.fillMaxWidth(), onClick = openApp, vPad = 9.dp.k(), hPad = 10.dp.k()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (m?.art != null) {
                Image(
                    provider = ImageProvider(m.art),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.size(44.dp.k()).cornerRadius(10.dp.k())
                )
            } else {
                RoundButton(R.drawable.ic_music, "Música", Glass.pink.copy(alpha = 0.8f), null,
                    size = 44.dp.k(), iconSize = 22.dp.k(), radius = 10.dp.k())
            }
            HGap(10.dp.k())
            Column(GlanceModifier.defaultWeight()) {
                when {
                    !allowed -> {
                        Value("Música", size = 14.sp.k())
                        Label("Abra o Wigters para liberar", color = Glass.text2)
                    }
                    m == null -> {
                        Value("Nada tocando", size = 14.sp.k())
                        Label("Dê play em qualquer app", color = Glass.text2)
                    }
                    else -> {
                        Value(m.title, size = 14.sp.k())
                        Label(m.artist, color = Glass.text2, size = 13.sp.k())
                    }
                }
            }
            if (m != null) {
                RoundButton(if (m.playing) R.drawable.ic_pause else R.drawable.ic_play,
                    if (m.playing) "Pausar" else "Tocar", Glass.off, actionRunCallback<MediaPlayPauseAction>(),
                    size = 36.dp.k(), iconSize = 18.dp.k())
                HGap(6.dp.k())
                RoundButton(R.drawable.ic_next, "Próxima", Glass.off, actionRunCallback<MediaNextAction>(),
                    size = 36.dp.k(), iconSize = 18.dp.k())
            }
        }
    }
}

// ---------- Ações ----------

class TimerToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        TimerStore.toggle(context)
        updateAllWidgets(context)
    }
}

class TimerResetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        TimerStore.reset(context)
        updateAllWidgets(context)
    }
}

class RefreshWeatherAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WeatherRepo.refreshIfStale(context, force = true)
        ActivityRepo.refresh(context)
        updateAllWidgets(context)
    }
}

class MediaPlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val c = NowPlaying.pick(NowPlaying.controllers(context)) ?: return
        val playing = c.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
        if (playing) c.transportControls.pause() else c.transportControls.play()
        delay(400)
        NowPlaying.save(context, c)
        updateAllWidgets(context)
    }
}

class MediaNextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val c = NowPlaying.pick(NowPlaying.controllers(context)) ?: return
        c.transportControls.skipToNext()
        delay(700)
        NowPlaying.save(context, c)
        updateAllWidgets(context)
    }
}
