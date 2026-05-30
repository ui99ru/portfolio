package com.tinvestlite.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tinvestlite.BuildConfig
import com.tinvestlite.data.AppMode
import com.tinvestlite.di.AppContainer
import com.tinvestlite.ui.common.vmFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer) {
    val vm: SettingsViewModel = viewModel(
        factory = vmFactory { SettingsViewModel(container.repository, container.tokenStore) },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { TopAppBar(windowInsets = WindowInsets(0), title = { Text("Настройки") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ModeCard(state, vm)
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            RealTokenCard(state, vm)

            if (state.mode == AppMode.Sandbox) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                SandboxCard(state, vm)
            }

            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }

            OutlinedButton(onClick = vm::logout, modifier = Modifier.fillMaxWidth()) {
                Text("Выйти (удалить все токены)")
            }

            Text(
                text = "Titan v${BuildConfig.VERSION_NAME}. " +
                    "Неофициальное приложение, не связано с каким-либо брокером.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeCard(state: SettingsUiState, vm: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Режим", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.mode == AppMode.Sandbox,
                onClick = { vm.switchMode(AppMode.Sandbox) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Песочница") }
            SegmentedButton(
                selected = state.mode == AppMode.Real,
                onClick = { vm.switchMode(AppMode.Real) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                enabled = state.hasRealToken,
            ) { Text("Реальный") }
        }
        Text(
            text = when (state.mode) {
                AppMode.Sandbox -> "Виртуальные деньги. Торговля доступна."
                AppMode.Real -> "Данные реального счёта. Только просмотр — торговля отключена."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RealTokenCard(state: SettingsUiState, vm: SettingsViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Реальный счёт (только чтение)", style = MaterialTheme.typography.titleMedium)

            if (state.hasRealToken) {
                Text(
                    "Реальный токен подключён.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Allow removal only while in the real mode — avoids accidentally
                // dropping the real token from the sandbox screen.
                if (state.mode == AppMode.Real) {
                    TextButton(onClick = vm::removeRealToken) {
                        Text("Удалить реальный токен")
                    }
                } else {
                    Text(
                        "Удалить его можно в режиме «Реальный».",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                var token by rememberSaveable { mutableStateOf("") }
                Text(
                    "Вставьте токен с доступом «только чтение». Реальные деньги " +
                        "под защитой: приложение никогда не отправляет торговые заявки в этом режиме.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Реальный токен (read-only)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = state.realError != null,
                    enabled = !state.isVerifyingReal,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { vm.saveRealToken(token) },
                    enabled = !state.isVerifyingReal,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isVerifyingReal) {
                        CircularProgressIndicator(
                            Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text("Проверяем…")
                    } else {
                        Text("Подключить реальный счёт")
                    }
                }
            }

            state.realError?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SandboxCard(state: SettingsUiState, vm: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Пополнить виртуальный счёт", style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = { vm.topUp(100_000) },
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("+100 000 ₽") }
        Button(
            onClick = { vm.topUp(1_000_000) },
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("+1 000 000 ₽") }
    }
}
