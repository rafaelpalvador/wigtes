package com.wigters.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
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
import com.wigters.app.LaunchActivity
import com.wigters.app.R

/** Cores e tipografia compartilhadas pelos widgets. */
object Glass {
    val white = ColorProvider(Color.White)
    val text2 = ColorProvider(Color(0xDEFFFFFF))
    val text3 = ColorProvider(Color(0x9EFFFFFF))
    val track = Color(0x33FFFFFF)
    val off = Color(0x2EFFFFFF)

    val coral = Color(0xFFFF6B6B)
    val yellow = Color(0xFFFFD60A)
    val green = Color(0xFF34D17A)
    val orange = Color(0xFFFF9F0A)
    val sky = Color(0xFF5AC8FA)
    val blue = Color(0xFF3B82F6)
    val red = Color(0xFFFF453A)
    val pink = Color(0xFFFF2D55)
    val gray = Color(0xFF8E8E93)
    val accent = Color(0xFF9CCBFF)
}

fun cp(color: Color) = ColorProvider(color)

/** Painel externo com "vidro": gradiente translúcido e borda clara. */
@Composable
fun GlassPanel(endInset: Dp = 0.dp.k(), content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = GlanceModifier.fillMaxSize().padding(end = endInset), contentAlignment = Alignment.Center) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ImageProvider(R.drawable.glass_panel))
                .cornerRadius(30.dp.k())
                .padding(9.dp.k()),
            content = content
        )
    }
}

/** Bloco interno do painel. */
@Composable
fun GlassTile(
    modifier: GlanceModifier = GlanceModifier,
    height: Dp? = null,
    onClick: Action? = null,
    hPad: Dp = 13.dp.k(),
    vPad: Dp = 11.dp.k(),
    alignment: Alignment = Alignment.CenterStart,
    content: @Composable () -> Unit,
) {
    var m = modifier
        .background(ImageProvider(R.drawable.glass_tile))
        .cornerRadius(22.dp.k())
    if (height != null) m = m.height(height)
    if (onClick != null) m = m.clickable(onClick)
    Box(modifier = m.padding(horizontal = hPad, vertical = vPad), contentAlignment = alignment) {
        content()
    }
}

@Composable
fun Label(text: String, color: ColorProvider = Glass.text3, size: TextUnit = 12.sp.k(), maxLines: Int = 1) {
    Text(text, maxLines = maxLines, style = TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Medium))
}

@Composable
fun Value(text: String, color: ColorProvider = Glass.white, size: TextUnit = 21.sp.k(), maxLines: Int = 1) {
    Text(text, maxLines = maxLines, style = TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Bold))
}

/** Barra arredondada com preenchimento proporcional. */
@Composable
fun Bar(fraction: Float, width: Dp, height: Dp, color: Color, track: Color = Glass.track) {
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier = GlanceModifier.size(width, height).cornerRadius(height / 2).background(cp(track)),
        contentAlignment = Alignment.CenterStart
    ) {
        if (f > 0f) {
            Box(
                modifier = GlanceModifier.size(maxOf(height, width * f), height)
                    .cornerRadius(height / 2).background(cp(color)),
                content = {}
            )
        }
    }
}

/** Botão redondo com ícone. */
@Composable
fun RoundButton(
    icon: Int,
    description: String,
    background: Color,
    action: Action?,
    size: Dp = 44.dp.k(),
    iconSize: Dp = 22.dp.k(),
    tint: Color = Color.White,
    radius: Dp = size / 2,
) {
    var m = GlanceModifier.size(size).cornerRadius(radius).background(cp(background))
    if (action != null) m = m.clickable(action)
    Box(modifier = m, contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = description,
            colorFilter = ColorFilter.tint(cp(tint)),
            modifier = GlanceModifier.size(iconSize)
        )
    }
}

@Composable fun VGap(h: Dp = 7.dp.k()) = Spacer(GlanceModifier.height(h))
@Composable fun HGap(w: Dp = 7.dp.k()) = Spacer(GlanceModifier.width(w))

/**
 * Abre outra tela passando por uma Activity própria (intent explícito),
 * o que evita problemas com PendingIntents implícitos no Android 14+.
 */
fun launch(context: Context, key: String, target: Intent, fallback: Intent? = null): Action =
    actionStartActivity(
        Intent(context, LaunchActivity::class.java)
            .setData(Uri.parse("wigters://launch/$key"))
            .putExtra(Intent.EXTRA_INTENT, target)
            .apply { if (fallback != null) putExtra(LaunchActivity.EXTRA_FALLBACK, fallback) }
    )

/** Fator de escala do conteúdo, para caber na altura do widget. */
val LocalK = staticCompositionLocalOf { 1f }

@Composable fun Dp.k(): Dp = this * LocalK.current
@Composable fun TextUnit.k(): TextUnit = this * LocalK.current

/** Calcula o fator para uma página que precisa de [natural] de altura e fornece aos filhos. */
@Composable
fun Scaled(natural: Dp, content: @Composable () -> Unit) {
    val h = androidx.glance.LocalSize.current.height
    val k = (h / natural).coerceIn(0.72f, 1.18f)
    CompositionLocalProvider(LocalK provides k) { content() }
}

/** Atualiza todos os widgets do app (separados e empilhado). */
suspend fun updateAllWidgets(context: Context) {
    SystemWidget().updateAll(context)
    DashboardWidget().updateAll(context)
    StackWidget().updateAll(context)
}
