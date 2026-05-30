package com.tinvestlite.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tinvestlite.BuildConfig
import com.tinvestlite.di.AppContainer
import com.tinvestlite.ui.common.vmFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer) {
    val vm: SettingsViewModel = viewModel(
        factory = vmFactory { SettingsViewModel(container.repository, container.tokenStore) },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Настройки") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Песочница", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Счёт: ${state.accountId ?: "—"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                "Пополнить виртуальный счёт",
                style = MaterialTheme.typography.titleMedium,
            )
            Button(
                onClick = { vm.topUp(100_000) },
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("+100 000 ₽")
            }
            Button(
                onClick = { vm.topUp(1_000_000) },
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("+1 000 000 ₽")
            }

            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }

            OutlinedButton(
                onClick = vm::logout,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сменить токен / выйти")
            }

            Text(
                text = "Т-Инвест Lite v${BuildConfig.VERSION_NAME} · режим песочницы. " +
                    "Приложение не аффилировано с Т-Банком.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
