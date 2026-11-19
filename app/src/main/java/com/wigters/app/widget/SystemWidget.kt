package com.wigters.app.widget

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.wigters.app.R
import com.wigters.app.data.SystemStats
import com.wigters.app.work.RefreshWorker
import java.util.Locale

// ---------- Paleta "vidro" ----------
private object Palette {
    val panel = ColorProvider(Color(0x552B5BD7))
    val tile = ColorProvider(Color(0x55102A6B))
    val white = ColorProvider(Color.White)
    val whiteSoft = ColorProvider(Color(0xCCFFFFFF))
    val whiteDim = ColorProvider(Color(0x33FFFFFF))
    val coral = Color(0xFFFF6B6B)
    val yellow = Color(0xFFFFD60A)
    val green = Color(0xFF4ADE80)
    val orange = Color(0xFFFF9F0A)
    val sky = Color(0xFF5AC8FA)
    val blue = Color(0xFF3B82F6)
    val red = Color(0xFFEF4444)
    val gray = Color(0xFFB0B8C8)
    val off = Color(0x40FFFFFF)
}

private fun c(color: Color) = ColorProvider(color)

class SystemWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val stats = SystemStats.read(context)
        provideContent { WidgetContent(stats) }
    }
}

class SystemWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SystemWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedule(context)
    }
}

@Composable
private fun WidgetContent(s: SystemStats) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(30.dp)
            .background(Palette.panel)
            .padding(10.dp)
    ) {
        Header(s)
        Gap()
        Row(GlanceModifier.fillMaxWidth()) {
            RamTile(s.ramPercent, GlanceModifier.defaultWeight())
            HGap()
            BrightnessTile(s.brightnessPercent, GlanceModifier.defaultWeight())
        }
        Gap()
        Row(GlanceModifier.fillMaxWidth()) {
            CpuTile(s.cpuPercent, GlanceModifier.defaultWeight())
            HGap()
            BatteryTile(s.batteryPercent, s.charging, GlanceModifier.defaultWeight())
        }
        Gap()
        Toggles(s)
        Gap()
        UptimeRow(s.uptime)
        Gap()
        StorageCard(s)
    }
}

// ---------- Blocos ----------

@Composable
private fun Header(s: SystemStats) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(22.dp)
            .background(Palette.tile)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clickable(actionRunCallback<RefreshAction>()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier.size(26.dp, 40.dp).cornerRadius(8.dp).background(c(Color(0xFFF0A6E8))),
            content = {}
        )
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(s.deviceName, maxLines = 1, style = TextStyle(color = Palette.white, fontSize = 17.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(4.dp))
            Row {
                Chip(s.androidVersion, Color(0xFFE5484D))
                s.soc?.let {
                    Spacer(GlanceModifier.width(6.dp))
                    Chip(it, Color(0xFF8DA4EF))
                }
            }
        }
        Image(
            provider = ImageProvider(R.drawable.ic_refresh),
            contentDescription = "Atualizar",
            colorFilter = ColorFilter.tint(Palette.whiteSoft),
            modifier = GlanceModifier.size(20.dp)
        )
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Box(
        modifier = GlanceModifier.cornerRadius(10.dp).background(c(color.copy(alpha = 0.85f)))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text, style = TextStyle(color = Palette.white, fontSize = 10.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun Tile(modifier: GlanceModifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.height(78.dp).cornerRadius(20.dp).background(Palette.tile).padding(12.dp),
        contentAlignment = Alignment.BottomStart
    ) { content() }
}

@Composable
private fun TileLabel(text: String) {
    Text(text, style = TextStyle(color = Palette.white, fontSize = 13.sp, fontWeight = FontWeight.Bold))
}

@Composable
private fun RamTile(percent: Int, modifier: GlanceModifier) = Tile(modifier) {
    Column {
        val filled = ((percent + 10) / 20).coerceIn(0, 5)
        Row {
            repeat(5) { i ->
                Box(
                    modifier = GlanceModifier.size(7.dp, 20.dp).cornerRadius(4.dp)
                        .background(c(if (i < filled) Palette.coral else Palette.coral.copy(alpha = 0.3f))),
                    content = {}
                )
                if (i < 4) Spacer(GlanceModifier.width(3.dp))
            }
        }
        Spacer(GlanceModifier.height(10.dp))
        TileLabel("RAM $percent%")
    }
}

@Composable
private fun BrightnessTile(percent: Int?, modifier: GlanceModifier) = Tile(modifier) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = GlanceModifier.cornerRadius(6.dp).background(c(Palette.yellow)).padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(percent?.let { "$it%" } ?: "—", style = TextStyle(color = c(Color(0xFF1F2937)), fontSize = 14.sp, fontWeight = FontWeight.Bold))
            }
            Spacer(GlanceModifier.width(3.dp))
            Box(GlanceModifier.size(6.dp, 22.dp).cornerRadius(3.dp).background(Palette.white), content = {})
        }
        Spacer(GlanceModifier.height(8.dp))
        TileLabel("Brilho")
    }
}

@Composable
private fun CpuTile(percent: Int?, modifier: GlanceModifier) = Tile(modifier) {
    Column {
        LinearProgressIndicator(
            progress = (percent ?: 0) / 100f,
            modifier = GlanceModifier.size(64.dp, 14.dp).cornerRadius(7.dp),
            color = c(Palette.sky),
            backgroundColor = Palette.whiteDim
        )
        Spacer(GlanceModifier.height(14.dp))
        TileLabel("CPU ${percent?.let { "$it%" } ?: "—"}")
    }
}

@Composable
private fun BatteryTile(percent: Int, charging: Boolean, modifier: GlanceModifier) = Tile(modifier) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = GlanceModifier.size(46.dp, 22.dp).cornerRadius(7.dp).background(Palette.white).padding(2.dp)
            ) {
                Box(
                    modifier = GlanceModifier.size(42.dp, 18.dp).cornerRadius(5.dp).background(c(Color(0xFF1E3A8A))),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val w = (42 * percent / 100).coerceAtLeast(3)
                    val fill = if (percent <= 20) Palette.red else Palette.green
                    Box(GlanceModifier.size(w.dp, 18.dp).cornerRadius(5.dp).background(c(fill)), content = {})
                }
            }
            Spacer(GlanceModifier.width(2.dp))
            Box(GlanceModifier.size(3.dp, 8.dp).cornerRadius(2.dp).background(Palette.white), content = {})
        }
        Spacer(GlanceModifier.height(12.dp))
        TileLabel(if (charging) "Bateria $percent% ⚡" else "Bateria $percent%")
    }
}

@Composable
private fun Toggles(s: SystemStats) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().cornerRadius(22.dp).background(Palette.tile).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToggleButton(R.drawable.ic_flash, "Lanterna", if (s.torchOn) Palette.gray else Palette.off,
            actionRunCallback<ToggleTorchAction>(), GlanceModifier.defaultWeight())
        ToggleButton(if (s.ringerSilent) R.drawable.ic_bell_off else R.drawable.ic_bell, "Silencioso",
            if (s.ringerSilent) Palette.red else Palette.off,
            actionRunCallback<ToggleRingerAction>(), GlanceModifier.defaultWeight())
        ToggleButton(R.drawable.ic_wifi, "Wi‑Fi", if (s.wifiOn) Palette.blue else Palette.off,
            actionStartActivity(panelIntent(Settings.Panel.ACTION_WIFI)), GlanceModifier.defaultWeight())
        ToggleButton(R.drawable.ic_plane, "Modo avião", if (s.airplaneOn) Palette.orange else Palette.off,
            actionStartActivity(panelIntent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)), GlanceModifier.defaultWeight())
        ToggleButton(R.drawable.ic_signal, "Internet", Palette.green,
            actionStartActivity(panelIntent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)), GlanceModifier.defaultWeight())
    }
}

private fun panelIntent(action: String) = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

@Composable
private fun ToggleButton(icon: Int, label: String, color: Color, action: Action, modifier: GlanceModifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = GlanceModifier.size(46.dp).cornerRadius(15.dp).background(c(color)).clickable(action),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = label,
                colorFilter = ColorFilter.tint(Palette.white),
                modifier = GlanceModifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun UptimeRow(uptime: String) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().cornerRadius(22.dp).background(Palette.tile)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_phone),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Palette.white),
            modifier = GlanceModifier.size(26.dp)
        )
        Spacer(GlanceModifier.defaultWeight())
        Text("Ligado há ", style = TextStyle(color = c(Palette.orange), fontSize = 12.sp, fontWeight = FontWeight.Medium))
        Text(uptime, style = TextStyle(color = c(Palette.orange), fontSize = 28.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun StorageCard(s: SystemStats) {
    Column(
        modifier = GlanceModifier.fillMaxWidth().cornerRadius(22.dp).background(Palette.tile)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text("Armazenamento", style = TextStyle(color = Palette.whiteSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium))
        Text(
            "${gb(s.storageUsedBytes)} / ${gb(s.storageTotalBytes)}",
            style = TextStyle(color = Palette.white, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = s.storageFraction,
            modifier = GlanceModifier.fillMaxWidth().height(14.dp).cornerRadius(7.dp),
            color = c(Palette.sky),
            backgroundColor = Palette.whiteDim
        )
    }
}

private fun gb(bytes: Long): String = String.format(Locale.ROOT, "%.0fGB", bytes / 1_000_000_000.0)

@Composable private fun Gap() = Spacer(GlanceModifier.height(8.dp))
@Composable private fun HGap() = Spacer(GlanceModifier.width(8.dp))
