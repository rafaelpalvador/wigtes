package com.wigters.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.wigters.app.data.SystemStats
import com.wigters.app.work.RefreshWorker

private const val PAGES = 2
private val GUTTER = 16.dp

/**
 * Um widget só com as duas páginas (Sistema e Dia) numa lista vertical:
 * arraste para cima/baixo para trocar. Cada página tem a altura exata do widget,
 * então um arrasto leva sempre de uma página à outra.
 */
class StackWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val stats = SystemStats.read(context)
        val dashboard = DashboardData.read(context)
        provideContent {
            val h = LocalSize.current.height
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(PAGES) { index ->
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().height(h),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(GlanceModifier.defaultWeight().fillMaxHeight()) {
                            if (index == 0) SystemContent(stats, GUTTER) else DashboardContent(dashboard, GUTTER)
                        }
                        PageDots(index)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageDots(page: Int) {
    Column(
        modifier = GlanceModifier.width(GUTTER).fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(PAGES) { i ->
            val active = i == page
            Box(
                modifier = GlanceModifier.size(7.dp, if (active) 20.dp else 7.dp)
                    .cornerRadius(4.dp)
                    .background(cp(if (active) Color.White else Color(0x8CFFFFFF))),
                content = {}
            )
            if (i < PAGES - 1) Box(GlanceModifier.size(7.dp, 8.dp), content = {})
        }
    }
}

class StackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StackWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedule(context)
        RefreshWorker.runNow(context)
    }
}
