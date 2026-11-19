package com.wigters.app

import android.Manifest
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import com.wigters.app.data.AppPrefs
import com.wigters.app.data.Slot
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

        AppSlotsSection(tick, onChanged)

        Text("Permissões", style = MaterialTheme.typography.titleMedium)

        PermissionCard(
            title = "Brilho pelo widget",
            text = "Permite que os botões − e + do bloco Brilho mudem o brilho da tela.",
            granted = remember(tick) { Settings.System.canWrite(context) },
        ) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

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

@Composable
private fun AppSlotsSection(tick: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Slot?>(null) }
    var version by remember { mutableIntStateOf(0) }

    Text("Apps dos atalhos", style = MaterialTheme.typography.titleMedium)
    Text(
        "Escolha qual app abre em cada parte do Painel do dia.",
        style = MaterialTheme.typography.bodyMedium
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Slot.entries.forEach { slot ->
                val pkg = remember(tick, version) { AppPrefs.get(context, slot) }
                val icon = remember(pkg) { pkg?.let { AppPrefs.icon(context, it, 96)?.asImageBitmap() } }
                ListItem(
                    modifier = Modifier.clickable { editing = slot },
                    headlineContent = { Text(slot.title) },
                    supportingContent = {
                        Text(pkg?.let { AppPrefs.label(context, it) } ?: "Padrão · ${slot.hint}")
                    },
                    leadingContent = {
                        if (icon != null) Image(icon, null, Modifier.size(36.dp))
                        else Box(Modifier.size(36.dp))
                    },
                    trailingContent = { Text("Trocar", color = MaterialTheme.colorScheme.primary) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
            }
        }
    }

    editing?.let { slot ->
        AppPickerDialog(
            title = slot.title,
            onDismiss = { editing = null },
            onPick = { pkg ->
                AppPrefs.set(context, slot, pkg)
                editing = null
                version++
                onChanged()
            }
        )
    }
}

@Composable
private fun AppPickerDialog(title: String, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    val context = LocalContext.current
    val apps = remember { AppPrefs.installed(context) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) { apps.filter { it.label.contains(query, ignoreCase = true) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item {
                        ListItem(
                            modifier = Modifier.clickable { onPick(null) },
                            headlineContent = { Text("Padrão") },
                            supportingContent = { Text("Usar o comportamento original") },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                        )
                    }
                    items(filtered, key = { it.packageName }) { app ->
                        val icon = remember(app.packageName) { AppPrefs.icon(context, app.packageName, 96)?.asImageBitmap() }
                        ListItem(
                            modifier = Modifier.clickable { onPick(app.packageName) },
                            headlineContent = { Text(app.label) },
                            leadingContent = {
                                if (icon != null) Image(icon, null, Modifier.size(32.dp)) else Box(Modifier.size(32.dp))
                            },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
