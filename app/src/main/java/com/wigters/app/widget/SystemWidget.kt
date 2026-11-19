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
import androidx.glance.action.actionParametersOf
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


val SYSTEM_NATURAL_HEIGHT = 505.dp

@Composable
internal fun SystemContent(s: SystemStats, endInset: Dp = 0.dp.k()) = Scaled(SYSTEM_NATURAL_HEIGHT) {
    SystemBody(s, endInset)
}

@Composable
private fun SystemBody(s: SystemStats, endInset: Dp) {
    val ctx = LocalContext.current
    val width = LocalSize.current.width - endInset
    val inner = width - 18.dp.k()                 // largura útil dentro do painel
    val half = (inner - 7.dp.k()) / 2 - 26.dp.k()     // largura útil dentro de um bloco pequeno

    GlassPanel {
        Header(s)
        VGap()
        Row(GlanceModifier.fillMaxWidth()) {
            RamTile(s.ramPercent, GlanceModifier.defaultWeight(),
                launch(ctx, "ram", memoryIntent(ctx), Intent(Settings.ACTION_APPLICATION_SETTINGS)))
            HGap()
            BrightnessTile(s.brightnessPercent, half - 52.dp.k(), GlanceModifier.defaultWeight())
        }
        VGap()
        Row(GlanceModifier.fillMaxWidth()) {
            CpuTile(s.cpuPercent, half, GlanceModifier.defaultWeight(),
                launch(ctx, "cpu", Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)))
            HGap()
            BatteryTile(s.batteryPercent, s.charging, GlanceModifier.defaultWeight(),
                launch(ctx, "battery", Intent(Intent.ACTION_POWER_USAGE_SUMMARY), Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)))
        }
        VGap()
        Toggles(s)
        VGap()
        UptimeTile(s.uptime, launch(ctx, "uptime", Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)))
        VGap()
        StorageTile(s, inner - 26.dp.k(),
            launch(ctx, "storage", Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), Intent(Settings.ACTION_SETTINGS)))
    }
}

@Composable
private fun Header(s: SystemStats) {
    GlassTile(
        modifier = GlanceModifier.fillMaxWidth(),
        onClick = actionRunCallback<RefreshAction>(),
        vPad = 10.dp.k()
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = GlanceModifier.size(24.dp.k(), 38.dp.k())
                    .background(ImageProvider(R.drawable.phone_badge)).cornerRadius(8.dp.k()),
                content = {}
            )
            HGap(11.dp.k())
            Column(GlanceModifier.defaultWeight()) {
                Value(s.deviceName, size = 17.sp.k())
                VGap(4.dp.k())
                Row {
                    Chip(s.androidVersion, Color(0xFFFF6961))
                    s.soc?.let {
                        HGap(5.dp.k())
                        Chip(it, Color(0xFF8FA8F0))
                    }
                }
            }
            RoundButton(R.drawable.ic_refresh, "Atualizar", Glass.off, null, size = 34.dp.k(), iconSize = 18.dp.k())
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Box(
        modifier = GlanceModifier.cornerRadius(9.dp.k()).background(cp(color))
            .padding(horizontal = 8.dp.k(), vertical = 2.dp.k())
    ) {
        Label(text, color = Glass.white, size = 10.sp.k())
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
    onClick: androidx.glance.action.Action? = null,
) {
    GlassTile(modifier = modifier, height = 76.dp.k(), onClick = onClick) {
        Column(GlanceModifier.fillMaxWidth()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    Label(label)
                    Value(value)
                }
                visual()
            }
            bottom?.let { VGap(6.dp.k()); it() }
        }
    }
}

@Composable
private fun RamTile(percent: Int, modifier: GlanceModifier, onClick: androidx.glance.action.Action) {
    val filled = ((percent + 10) / 20).coerceIn(0, 5)
    SmallTile("RAM", "$percent%", modifier, onClick = onClick, visual = {
        Row(verticalAlignment = Alignment.Bottom) {
            repeat(5) { i ->
                Box(
                    modifier = GlanceModifier.size(6.dp.k(), (12 + i * 4).dp).cornerRadius(3.dp.k())
                        .background(cp(if (i < filled) Glass.coral else Glass.coral.copy(alpha = 0.28f))),
                    content = {}
                )
                if (i < 4) Spacer(GlanceModifier.width(3.dp.k()))
            }
        }
    })
}

@Composable
private fun BrightnessTile(percent: Int?, barWidth: Dp, modifier: GlanceModifier) {
    val ctx = LocalContext.current
    val canWrite = Settings.System.canWrite(ctx)
    val askPermission = launch(ctx, "write-settings",
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}")))
    fun step(delta: Int) = if (canWrite) {
        actionRunCallback<BrightnessAction>(actionParametersOf(BRIGHTNESS_STEP to delta))
    } else askPermission
    SmallTile("Brilho", percent?.let { "$it%" } ?: "—", modifier,
        onClick = launch(ctx, "display", Intent(Settings.ACTION_DISPLAY_SETTINGS)),
        visual = {
            Image(
                provider = ImageProvider(R.drawable.wx_sun),
                contentDescription = null,
                modifier = GlanceModifier.size(24.dp.k())
            )
        },
        bottom = {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(R.drawable.ic_minus, "Diminuir brilho", Glass.off, step(-10), size = 22.dp.k(), iconSize = 14.dp.k())
                HGap(4.dp.k())
                Bar((percent ?: 0) / 100f, barWidth, 6.dp.k(), Glass.yellow)
                HGap(4.dp.k())
                RoundButton(R.drawable.ic_plus, "Aumentar brilho", Glass.off, step(10), size = 22.dp.k(), iconSize = 14.dp.k())
            }
        }
    )
}

@Composable
private fun CpuTile(percent: Int?, barWidth: Dp, modifier: GlanceModifier, onClick: androidx.glance.action.Action) {
    SmallTile("CPU", percent?.let { "$it%" } ?: "—", modifier, onClick = onClick,
        visual = {
            Row(verticalAlignment = Alignment.Bottom) {
                val levels = listOf(0.45f, 0.8f, 0.6f, 1f)
                levels.forEachIndexed { i, l ->
                    val active = (percent ?: 0) / 100f >= (i + 1) / 5f
                    Box(
                        modifier = GlanceModifier.size(6.dp.k(), (26 * l).dp).cornerRadius(3.dp.k())
                            .background(cp(if (active) Glass.sky else Glass.sky.copy(alpha = 0.3f))),
                        content = {}
                    )
                    if (i < levels.lastIndex) Spacer(GlanceModifier.width(3.dp.k()))
                }
            }
        },
        bottom = { Bar((percent ?: 0) / 100f, barWidth, 6.dp.k(), Glass.sky) }
    )
}

@Composable
private fun BatteryTile(percent: Int, charging: Boolean, modifier: GlanceModifier, onClick: androidx.glance.action.Action) {
    val fill = when {
        charging -> Glass.green
        percent <= 20 -> Glass.red
        else -> Glass.green
    }
    SmallTile(if (charging) "Carregando" else "Bateria", "$percent%", modifier, onClick = onClick, visual = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = GlanceModifier.size(40.dp.k(), 20.dp.k()).cornerRadius(6.dp.k())
                    .background(cp(Color(0x66FFFFFF))).padding(2.dp.k()),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = GlanceModifier.size((36 * percent / 100).coerceAtLeast(4).dp, 16.dp.k())
                        .cornerRadius(4.dp.k()).background(cp(fill)),
                    content = {}
                )
            }
            Spacer(GlanceModifier.width(2.dp.k()))
            Box(GlanceModifier.size(3.dp.k(), 8.dp.k()).cornerRadius(2.dp.k()).background(cp(Color(0x66FFFFFF))), content = {})
        }
    })
}

@Composable
private fun Toggles(s: SystemStats) {
    val ctx = LocalContext.current
    GlassTile(modifier = GlanceModifier.fillMaxWidth(), vPad = 10.dp.k(), hPad = 8.dp.k()) {
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
                    launch(ctx, "wifi", Intent(Settings.Panel.ACTION_WIFI), Intent(Settings.ACTION_WIFI_SETTINGS)))
            }
            ToggleSlot(GlanceModifier.defaultWeight()) {
                RoundButton(R.drawable.ic_plane, "Modo avião", if (s.airplaneOn) Glass.orange else Glass.off,
                    launch(ctx, "airplane", Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS)))
            }
            ToggleSlot(GlanceModifier.defaultWeight()) {
                RoundButton(R.drawable.ic_signal, "Internet", Glass.green,
                    launch(ctx, "internet", Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY), Intent(Settings.ACTION_WIRELESS_SETTINGS)))
            }
        }
    }
}

@Composable
private fun ToggleSlot(modifier: GlanceModifier, content: @Composable () -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun UptimeTile(uptime: String, onClick: androidx.glance.action.Action) {
    GlassTile(modifier = GlanceModifier.fillMaxWidth(), vPad = 9.dp.k(), onClick = onClick) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(R.drawable.ic_phone),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Glass.white),
                modifier = GlanceModifier.size(24.dp.k())
            )
            HGap(10.dp.k())
            Label("Ligado há", color = cp(Glass.orange.copy(alpha = 0.85f)), size = 13.sp.k())
            Spacer(GlanceModifier.defaultWeight())
            Value(uptime, color = cp(Glass.orange), size = 28.sp.k())
        }
    }
}

@Composable
private fun StorageTile(s: SystemStats, barWidth: Dp, onClick: androidx.glance.action.Action) {
    GlassTile(modifier = GlanceModifier.fillMaxWidth(), onClick = onClick) {
        Column(GlanceModifier.fillMaxWidth()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(GlanceModifier.defaultWeight()) {
                    Label("Armazenamento")
                    Value("${gb(s.storageUsedBytes)} / ${gb(s.storageTotalBytes)} GB", size = 22.sp.k())
                }
                Label("${(s.storageFraction * 100).toInt()}%", color = Glass.text2, size = 13.sp.k())
            }
            VGap(8.dp.k())
            Bar(s.storageFraction, barWidth, 10.dp.k(), Glass.sky)
        }
    }
}

/** Gerenciador do Honor (limpar memória), se existir; senão, lista de apps. */
private fun memoryIntent(ctx: Context): Intent =
    listOf("com.hihonor.systemmanager", "com.huawei.systemmanager")
        .firstNotNullOfOrNull { ctx.packageManager.getLaunchIntentForPackage(it) }
        ?: Intent(Settings.ACTION_APPLICATION_SETTINGS)

private fun gb(bytes: Long): String = String.format(Locale.ROOT, "%.0f", bytes / 1_000_000_000.0)
