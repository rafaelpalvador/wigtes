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
            )
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
internal fun DashboardContent(d: DashboardData, endInset: androidx.compose.ui.unit.Dp = 0.dp) {
    GlassPanel(endInset) {
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
        AppsRow()
        VGap()
        MusicRow(d.media, d.mediaAllowed)
    }
}

@Composable
private fun ClockHeader(d: DashboardData) {
    val ctx = LocalContext.current
    GlassTile(
        modifier = GlanceModifier.fillMaxWidth(),
        onClick = launch(ctx, "clock", Intent(AlarmClock.ACTION_SHOW_ALARMS)),
        vPad = 12.dp, hPad = 16.dp
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AndroidRemoteViews(
                remoteViews = RemoteViews(ctx.packageName, R.layout.widget_clock),
                modifier = GlanceModifier.defaultWeight()
            )
            d.nextAlarm?.let {
                Box(
                    modifier = GlanceModifier.cornerRadius(14.dp).background(cp(Glass.off))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_alarm),
                            contentDescription = "Próximo alarme",
                            colorFilter = ColorFilter.tint(Glass.white),
                            modifier = GlanceModifier.size(15.dp)
                        )
                        HGap(5.dp)
                        Label(it, color = Glass.white, size = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(d: DashboardData) {
    val ctx = LocalContext.current
    val open = launch(ctx, "calendar",
        Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()))
    GlassTile(modifier = GlanceModifier.fillMaxWidth(), onClick = open, vPad = 9.dp) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = GlanceModifier.size(42.dp).background(ImageProvider(R.drawable.date_badge)).cornerRadius(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(d.month, style = TextStyle(color = cp(Glass.red), fontSize = 9.sp, fontWeight = FontWeight.Bold))
                Text(d.day, style = TextStyle(color = cp(Color(0xFF1C1C1E)), fontSize = 17.sp, fontWeight = FontWeight.Bold))
            }
            HGap(12.dp)
            Box(GlanceModifier.size(3.dp, 30.dp).cornerRadius(2.dp).background(cp(Glass.accent)), content = {})
            HGap(9.dp)
            Column(GlanceModifier.defaultWeight()) {
                when {
                    !d.calendarAllowed -> {
                        Value("Agenda", size = 14.sp, color = cp(Glass.accent))
                        Label("Abra o Wigters para liberar", color = Glass.text2)
                    }
                    d.event == null -> {
                        Value("Sem eventos", size = 14.sp, color = cp(Glass.accent))
                        Label("Nos próximos 7 dias", color = Glass.text2)
                    }
                    else -> {
                        Value(d.event.title, size = 14.sp, color = cp(Glass.accent))
                        Label(d.event.whenText, color = Glass.white, size = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherRow(w: WeatherInfo?) {
    GlassTile(modifier = GlanceModifier.fillMaxWidth(), onClick = actionRunCallback<RefreshWeatherAction>(), vPad = 10.dp) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Label(w?.let { "${it.city} · ${it.description}" } ?: "Clima", color = Glass.text2, size = 13.sp)
                Value(w?.let { "${it.tempC}°" } ?: "—", size = 30.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Image(
                    provider = ImageProvider(w?.icon ?: R.drawable.wx_cloud),
                    contentDescription = w?.description,
                    modifier = GlanceModifier.size(30.dp)
                )
                VGap(4.dp)
                Label(w?.let { "${it.maxC}°  ${it.minC}°" } ?: "Toque p/ atualizar", color = Glass.text2, size = 13.sp)
            }
        }
    }
}

@Composable
private fun ActivityRow(a: DailyActivity?) {
    GlassTile(modifier = GlanceModifier.fillMaxWidth(), vPad = 10.dp) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Stat("Passos", a?.let { String.format(Locale.getDefault(), "%,d", it.steps) } ?: "—", GlanceModifier.defaultWeight())
            Stat("Distância", a?.let { String.format(Locale.getDefault(), "%.1f km", it.km) } ?: "—", GlanceModifier.defaultWeight())
            Stat("Energia", a?.let { "${it.kcal.toInt()} kcal" } ?: "—", GlanceModifier.defaultWeight())
            RoundButton(R.drawable.ic_walk, "Atividade", Glass.green.copy(alpha = 0.85f), null, size = 36.dp, iconSize = 20.dp)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: GlanceModifier) {
    Column(modifier) {
        Label(label, color = cp(Glass.coral), size = 11.sp)
        Value(value, size = 15.sp)
    }
}

@Composable
private fun TimerRow(t: TimerState) {
    val ctx = LocalContext.current
    val rv = RemoteViews(ctx.packageName, R.layout.widget_timer).apply {
        setChronometer(R.id.timer_chrono, t.base, null, t.running)
        setChronometerCountDown(R.id.timer_chrono, true)
    }
    GlassTile(modifier = GlanceModifier.fillMaxWidth(), vPad = 9.dp) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(if (t.running) R.drawable.ic_pause else R.drawable.ic_play,
                if (t.running) "Pausar" else "Iniciar",
                Glass.orange.copy(alpha = 0.22f), actionRunCallback<TimerToggleAction>(),
                size = 38.dp, iconSize = 20.dp, tint = Glass.orange)
            HGap(8.dp)
            RoundButton(R.drawable.ic_close, "Reiniciar", Glass.off, actionRunCallback<TimerResetAction>(),
                size = 38.dp, iconSize = 18.dp)
            HGap(10.dp)
            Label(if (t.running) "Foco" else "Timer", color = Glass.text3, size = 12.sp)
            Spacer(GlanceModifier.defaultWeight())
            AndroidRemoteViews(remoteViews = rv)
        }
    }
}

@Composable
private fun AppsRow() {
    val ctx = LocalContext.current
    GlassTile(modifier = GlanceModifier.fillMaxWidth(), vPad = 10.dp, hPad = 8.dp) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(R.drawable.ic_music, "Música", Glass.pink, Color.White,
                launch(ctx, "music", Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)))
            AppIcon(R.drawable.ic_message, "Mensagens", Glass.green, Color.White,
                launch(ctx, "sms", Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)))
            AppIcon(R.drawable.ic_camera, "Câmera", Color(0xFF3A3A3C), Color.White,
                launch(ctx, "camera", Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)))
            AppIcon(R.drawable.ic_tune, "Ajustes", Glass.gray, Color.White,
                launch(ctx, "settings", Intent(Settings.ACTION_SETTINGS)))
            AppIcon(R.drawable.ic_calendar, "Agenda", Color.White, Glass.red,
                launch(ctx, "cal", Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)))
        }
    }
}

@Composable
private fun androidx.glance.layout.RowScope.AppIcon(
    icon: Int, label: String, bg: Color, tint: Color, action: androidx.glance.action.Action,
) {
    Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
        RoundButton(icon, label, bg, action, size = 46.dp, iconSize = 24.dp, tint = tint, radius = 13.dp)
    }
}

@Composable
private fun MusicRow(m: Media?, allowed: Boolean) {
    val ctx = LocalContext.current
    val openApp = m?.packageName?.let { pkg ->
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { launch(ctx, "media-$pkg", it) }
    }
    GlassTile(modifier = GlanceModifier.fillMaxWidth(), onClick = openApp, vPad = 9.dp, hPad = 10.dp) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (m?.art != null) {
                Image(
                    provider = ImageProvider(m.art),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.size(44.dp).cornerRadius(10.dp)
                )
            } else {
                RoundButton(R.drawable.ic_music, "Música", Glass.pink.copy(alpha = 0.8f), null,
                    size = 44.dp, iconSize = 22.dp, radius = 10.dp)
            }
            HGap(10.dp)
            Column(GlanceModifier.defaultWeight()) {
                when {
                    !allowed -> {
                        Value("Música", size = 14.sp)
                        Label("Abra o Wigters para liberar", color = Glass.text2)
                    }
                    m == null -> {
                        Value("Nada tocando", size = 14.sp)
                        Label("Dê play em qualquer app", color = Glass.text2)
                    }
                    else -> {
                        Value(m.title, size = 14.sp)
                        Label(m.artist, color = Glass.text2, size = 13.sp)
                    }
                }
            }
            if (m != null) {
                RoundButton(if (m.playing) R.drawable.ic_pause else R.drawable.ic_play,
                    if (m.playing) "Pausar" else "Tocar", Glass.off, actionRunCallback<MediaPlayPauseAction>(),
                    size = 36.dp, iconSize = 18.dp)
                HGap(6.dp)
                RoundButton(R.drawable.ic_next, "Próxima", Glass.off, actionRunCallback<MediaNextAction>(),
                    size = 36.dp, iconSize = 18.dp)
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
