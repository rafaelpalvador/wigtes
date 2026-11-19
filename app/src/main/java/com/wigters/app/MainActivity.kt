package com.wigters.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wigters.app.widget.SystemWidgetReceiver

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = dynamicDarkColorScheme(LocalContext.current)) {
                Surface(Modifier.fillMaxSize()) { Home() }
            }
        }
    }
}

@Composable
private fun Home() {
    val context = LocalContext.current
    fun open(action: String) =
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    Column(
        modifier = Modifier.safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Wigters", style = MaterialTheme.typography.displaySmall)
        Text(
            "Widgets de sistema para a tela inicial. Adicione o \"Painel do sistema\" " +
                "pelo botão abaixo ou segurando a tela inicial › Widgets › Wigters.",
            style = MaterialTheme.typography.bodyLarge
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val awm = AppWidgetManager.getInstance(context)
                val provider = ComponentName(context, SystemWidgetReceiver::class.java)
                if (awm.isRequestPinAppWidgetSupported) {
                    awm.requestPinAppWidget(provider, null, null)
                }
            }
        ) { Text("Adicionar widget à tela inicial") }

        Text("Ajustes recomendados", style = MaterialTheme.typography.titleMedium)

        FilledTonalButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) }
        ) { Text("Permitir modo silencioso total") }

        FilledTonalButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }
        ) { Text("Tirar da otimização de bateria") }

        Text(
            "No Honor, tire o Wigters da otimização de bateria e, em Bateria › Inicialização " +
                "de apps, deixe-o em \"Gerenciar manualmente\" com tudo ativado. Sem isso o " +
                "widget pode parar de atualizar.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
