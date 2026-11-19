package com.wigters.app

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.wigters.app.data.ActivityRepo
import com.wigters.app.data.CalendarRepo
import com.wigters.app.data.NowPlaying
import com.wigters.app.data.WeatherRepo
import com.wigters.app.widget.DashboardWidgetReceiver
import com.wigters.app.widget.StackWidgetReceiver
import com.wigters.app.widget.SystemWidgetReceiver
import com.wigters.app.work.RefreshWorker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var refreshTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = dynamicDarkColorScheme(LocalContext.current)) {
                Surface(Modifier.fillMaxSize()) { Home(refreshTick) { onPermissionsChanged() } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        onPermissionsChanged()
    }

    /** Com o app em primeiro plano dá para ler localização e saúde; guardamos e atualizamos os widgets. */
    private fun onPermissionsChanged() {
        refreshTick++
        lifecycleScope.launch {
            runCatching { WeatherRepo.saveLocation(this@MainActivity) }
            runCatching { WeatherRepo.refreshIfStale(this@MainActivity, force = true) }
            runCatching { ActivityRepo.refresh(this@MainActivity) }
            RefreshWorker.runNow(this@MainActivity)
        }
    }
}

@Composable
private fun Home(tick: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    fun open(action: String) = runCatching {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    val runtimePerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onChanged() }
    val healthPerms = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { onChanged() }

    var calendarOk by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { calendarOk = tick }

    Column(
        modifier = Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Wigters", style = MaterialTheme.typography.displaySmall)
        Text(
            "Widgets para a tela inicial: o Wigters junta o Painel do sistema e o Painel do dia, com pontinhos para alternar. " +
                "Adicione pelos botões ou segurando a tela inicial › Widgets › Wigters.",
            style = MaterialTheme.typography.bodyLarge
        )

        Button(modifier = Modifier.fillMaxWidth(), onClick = { pin(context, StackWidgetReceiver::class.java) }) {
            Text("Adicionar Wigters (2 páginas)")
        }
        Text("Ou os painéis separados:", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(modifier = Modifier.weight(1f), onClick = { pin(context, SystemWidgetReceiver::class.java) }) {
                Text("Painel do sistema")
            }
            FilledTonalButton(modifier = Modifier.weight(1f), onClick = { pin(context, DashboardWidgetReceiver::class.java) }) {
                Text("Painel do dia")
            }
        }

        Text("Permissões", style = MaterialTheme.typography.titleMedium)

        PermissionCard(
            title = "Agenda e localização",
            text = "Para mostrar o próximo evento e o clima da sua cidade (sem isso, usa Viseu).",
            granted = remember(tick, calendarOk) {
                CalendarRepo.hasPermission(context) && has(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            },
        ) {
            runtimePerms.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        PermissionCard(
            title = "Passos, distância e energia",
            text = "Lidos do Health Connect. No Honor Saúde, ative a ligação com o Health Connect.",
            granted = null,
        ) {
            if (ActivityRepo.available(context)) healthPerms.launch(ActivityRepo.permissions)
            else open("android.settings.HEALTH_CONNECT_SETTINGS")
        }

        PermissionCard(
            title = "Música tocando",
            text = "Precisa do acesso a notificações para ver e controlar o que está tocando.",
            granted = remember(tick) { NowPlaying.hasAccess(context) },
        ) { open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }

        PermissionCard(
            title = "Modo silencioso total",
            text = "Sem isso, o botão de silêncio só coloca o celular para vibrar.",
            granted = null,
        ) { open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) }

        PermissionCard(
            title = "Bateria",
            text = "No Honor, tire o Wigters da otimização de bateria e, em Bateria › Inicialização de apps, " +
                "deixe-o em \"Gerenciar manualmente\" com tudo ativado. Sem isso os widgets param de atualizar.",
            granted = null,
        ) { open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }
    }
}

@Composable
private fun PermissionCard(title: String, text: String, granted: Boolean?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (granted == true) {
                    OutlinedButton(onClick = onClick) { Text("Liberado ✓") }
                } else {
                    FilledTonalButton(onClick = onClick) { Text("Liberar") }
                }
            }
        }
    }
}

private fun has(context: Context, perm: String) =
    context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

private fun pin(context: Context, receiver: Class<*>) {
    val awm = AppWidgetManager.getInstance(context)
    if (awm.isRequestPinAppWidgetSupported) {
        awm.requestPinAppWidget(ComponentName(context, receiver), null, null)
    }
}
