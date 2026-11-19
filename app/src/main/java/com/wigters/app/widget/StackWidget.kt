package com.wigters.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import com.wigters.app.data.SystemStats
import com.wigters.app.work.RefreshWorker

private val PAGE = intPreferencesKey("page")
private val PAGE_PARAM = ActionParameters.Key<Int>("page")
private const val PAGES = 2
private val GUTTER = 16.dp

/** Um widget só com as duas páginas (Sistema e Dia), alternadas pelos pontinhos ao lado. */
class StackWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val stats = SystemStats.read(context)
        val dashboard = DashboardData.read(context)
        provideContent {
            val page = currentState<Preferences>()[PAGE] ?: 0
            Box(modifier = GlanceModifier.fillMaxSize()) {
                if (page == 0) SystemContent(stats, GUTTER) else DashboardContent(dashboard, GUTTER)
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    PageDots(page)
                }
            }
        }
    }
}

@Composable
private fun PageDots(page: Int) {
    Column(
        modifier = GlanceModifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(PAGES) { i ->
            Box(
                modifier = GlanceModifier.size(GUTTER, 34.dp)
                    .clickable(actionRunCallback<SwitchPageAction>(actionParametersOf(PAGE_PARAM to i))),
                contentAlignment = Alignment.Center
            ) {
                val active = i == page
                Box(
                    modifier = GlanceModifier.size(7.dp, if (active) 20.dp else 7.dp)
                        .cornerRadius(4.dp)
                        .background(cp(if (active) Color.White else Color(0x8CFFFFFF))),
                    content = {}
                )
            }
        }
    }
}

class SwitchPageAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val target = parameters[PAGE_PARAM] ?: 0
        updateAppWidgetState(context, glanceId) { it[PAGE] = target }
        StackWidget().update(context, glanceId)
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
