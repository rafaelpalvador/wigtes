package com.wigters.app.widget

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.wigters.app.R
import com.wigters.app.data.SystemStats
import com.wigters.app.work.RefreshWorker
import java.util.Locale

class SystemWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val stats = SystemStats.read(context)
        provideContent { SystemContent(stats) }
    }
}

class SystemWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SystemWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedule(context)
    }
}

private val TILE_H = 76.dp

@Composable
private fun SystemContent(s: SystemStats) {
    val width = LocalSize.current.width
    val inner = width - 18.dp                 // largura útil dentro do painel
    val half = (inner - 7.dp) / 2 - 26.dp     // largura útil dentro de um bloco pequeno

    GlassPanel {
        Header(s)
        VGap()
        Row(GlanceModifier.fillMaxWidth()) {
            RamTile(s.ramPercent, GlanceModifier.defaultWeight())
            HGap()
            BrightnessTile(s.brightnessPercent, half, GlanceModifier.defaultWeight())
        }
        VGap()
        Row(GlanceModifier.fillMaxWidth()) {
            CpuTile(s.cpuPercent, half, GlanceModifier.defaultWeight())
            HGap()
            BatteryTile(s.batteryPercent, s.charging, GlanceModifier.defaultWeight())
        }
        VGap()
        Toggles(s)
        VGap()
        UptimeTile(s.uptime)
        VGap()
        StorageTile(s, inner - 26.dp)
    }
}

@Composable
private fun Header(s: SystemStats) {
    GlassTile(
        modifier = GlanceModifier.fillMaxWidth(),
        onClick = actionRunCallback<RefreshAction>(),
        vPad = 10.dp
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = GlanceModifier.size(24.dp, 38.dp)
                    .background(ImageProvider(R.drawable.phone_badge)).cornerRadius(8.dp),
                content = {}
            )
            HGap(11.dp)
            Column(GlanceModifier.defaultWeight()) {
                Value(s.deviceName, size = 17.sp)
                VGap(4.dp)
                Row {
                    Chip(s.androidVersion, Color(0xFFFF6961))
                    s.soc?.let {
                        HGap(5.dp)
                        Chip(it, Color(0xFF8FA8F0))
                    }
                }
            }
            RoundButton(R.drawable.ic_refresh, "Atualizar", Glass.off, null, size = 34.dp, iconSize = 18.dp)
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Box(
        modifier = GlanceModifier.cornerRadius(9.dp).background(cp(color))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Label(text, color = Glass.white, size = 10.sp)
    }
}

/** Bloco pequeno: rótulo no topo, valor grande e um indicador visual à direita. */
@Composable
private fun SmallTile(
    label: String,
    value: String,
    modifier: GlanceModifier,
    visual: @Composable () -> Unit,
    bottom: (@Composable () -> Unit)? = null,
) {
    GlassTile(modifier = modifier, height = TILE_H) {
        Column(GlanceModifier.fillMaxWidth()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    Label(label)
                    Value(value)
                }
                visual()
            }
            bottom?.let { VGap(6.dp); it() }
        }
    }
}

@Composable
private fun RamTile(percent: Int, modifier: GlanceModifier) {
    val filled = ((percent + 10) / 20).coerceIn(0, 5)
    SmallTile("RAM", "$percent%", modifier, visual = {
        Row(verticalAlignment = Alignment.Bottom) {
            repeat(5) { i ->
                Box(
                    modifier = GlanceModifier.size(6.dp, (12 + i * 4).dp).cornerRadius(3.dp)
                        .background(cp(if (i < filled) Glass.coral else Glass.coral.copy(alpha = 0.28f))),
                    content = {}
                )
                if (i < 4) Spacer(GlanceModifier.width(3.dp))
            }
        }
    })
}

@Composable
private fun BrightnessTile(percent: Int?, barWidth: Dp, modifier: GlanceModifier) {
    SmallTile("Brilho", percent?.let { "$it%" } ?: "—", modifier,
        visual = {
            Image(
                provider = ImageProvider(R.drawable.wx_sun),
                contentDescription = null,
                modifier = GlanceModifier.size(26.dp)
            )
        },
        bottom = { Bar((percent ?: 0) / 100f, barWidth, 6.dp, Glass.yellow) }
    )
}

@Composable
private fun CpuTile(percent: Int?, barWidth: Dp, modifier: GlanceModifier) {
    SmallTile("CPU", percent?.let { "$it%" } ?: "—", modifier,
        visual = {
            Row(verticalAlignment = Alignment.Bottom) {
                val levels = listOf(0.45f, 0.8f, 0.6f, 1f)
                levels.forEachIndexed { i, l ->
                    val active = (percent ?: 0) / 100f >= (i + 1) / 5f
                    Box(
                        modifier = GlanceModifier.size(6.dp, (26 * l).dp).cornerRadius(3.dp)
                            .background(cp(if (active) Glass.sky else Glass.sky.copy(alpha = 0.3f))),
                        content = {}
                    )
                    if (i < levels.lastIndex) Spacer(GlanceModifier.width(3.dp))
                }
            }
        },
        bottom = { Bar((percent ?: 0) / 100f, barWidth, 6.dp, Glass.sky) }
    )
}

@Composable
private fun BatteryTile(percent: Int, charging: Boolean, modifier: GlanceModifier) {
    val fill = when {
        charging -> Glass.green
        percent <= 20 -> Glass.red
        else -> Glass.green
    }
    SmallTile(if (charging) "Carregando" else "Bateria", "$percent%", modifier, visual = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = GlanceModifier.size(40.dp, 20.dp).cornerRadius(6.dp)
                    .background(cp(Color(0x66FFFFFF))).padding(2.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = GlanceModifier.size((36 * percent / 100).coerceAtLeast(4).dp, 16.dp)
                        .cornerRadius(4.dp).background(cp(fill)),
                    content = {}
                )
            }
            Spacer(GlanceModifier.width(2.dp))
            Box(GlanceModifier.size(3.dp, 8.dp).cornerRadius(2.dp).background(cp(Color(0x66FFFFFF))), content = {})
        }
    })
}

@Composable
private fun Toggles(s: SystemStats) {
    val ctx = LocalContext.current
    GlassTile(modifier = GlanceModifier.fillMaxWidth(), vPad = 10.dp, hPad = 8.dp) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ToggleSlot(GlanceModifier.defaultWeight()) {
                RoundButton(R.drawable.ic_flash, "Lanterna",
                    if (s.torchOn) Color(0xFFF2F2F7) else Glass.off,
                    actionRunCallback<ToggleTorchAction>(),
                    tint = if (s.torchOn) Color(0xFF1C1C1E) else Color.White)
            }
            ToggleSlot(GlanceModifier.defaultWeight()) {
                RoundButton(if (s.ringerSilent) R.drawable.ic_bell_off else R.drawable.ic_bell, "Silencioso",
                    if (s.ringerSilent) Glass.red else Glass.off, actionRunCallback<ToggleRingerAction>())
            }
            ToggleSlot(GlanceModifier.defaultWeight()) {
                RoundButton(R.drawable.ic_wifi, "Wi‑Fi", if (s.wifiOn) Glass.blue else Glass.off,
                    launch(ctx, "wifi", Intent(Settings.Panel.ACTION_WIFI)))
            }
            ToggleSlot(GlanceModifier.defaultWeight()) {
                RoundButton(R.drawable.ic_plane, "Modo avião", if (s.airplaneOn) Glass.orange else Glass.off,
                    launch(ctx, "airplane", Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)))
            }
            ToggleSlot(GlanceModifier.defaultWeight()) {
                RoundButton(R.drawable.ic_signal, "Internet", Glass.green,
                    launch(ctx, "internet", Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)))
            }
        }
    }
}

@Composable
private fun ToggleSlot(modifier: GlanceModifier, content: @Composable () -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun UptimeTile(uptime: String) {
    GlassTile(modifier = GlanceModifier.fillMaxWidth(), vPad = 9.dp) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(R.drawable.ic_phone),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Glass.white),
                modifier = GlanceModifier.size(24.dp)
            )
            HGap(10.dp)
            Label("Ligado há", color = cp(Glass.orange.copy(alpha = 0.85f)), size = 13.sp)
            Spacer(GlanceModifier.defaultWeight())
            Value(uptime, color = cp(Glass.orange), size = 28.sp)
        }
    }
}

@Composable
private fun StorageTile(s: SystemStats, barWidth: Dp) {
    GlassTile(modifier = GlanceModifier.fillMaxWidth()) {
        Column(GlanceModifier.fillMaxWidth()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(GlanceModifier.defaultWeight()) {
                    Label("Armazenamento")
                    Value("${gb(s.storageUsedBytes)} / ${gb(s.storageTotalBytes)} GB", size = 22.sp)
                }
                Label("${(s.storageFraction * 100).toInt()}%", color = Glass.text2, size = 13.sp)
            }
            VGap(8.dp)
            Bar(s.storageFraction, barWidth, 10.dp, Glass.sky)
        }
    }
}

private fun gb(bytes: Long): String = String.format(Locale.ROOT, "%.0f", bytes / 1_000_000_000.0)
