package com.tinvestlite.ui.trade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tinvestlite.di.AppContainer
import com.tinvestlite.ui.common.LoadingBox
import com.tinvestlite.ui.common.vmFactory
import com.tinvestlite.ui.theme.LossRed
import com.tinvestlite.ui.theme.ProfitGreen
import com.tinvestlite.util.MoneyFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeScreen(
    container: AppContainer,
    uid: String,
    startAsBuy: Boolean,
    onDone: () -> Unit,
) {
    val vm: TradeViewModel = viewModel(
        factory = vmFactory { TradeViewModel(container.repository, container.tokenStore, uid, startAsBuy) },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    if (state.isLoading) {
        LoadingBox()
        return
    }

    val currency = state.detail?.currency.orEmpty()

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = state.detail?.let { it.ticker.ifBlank { it.name } } ?: "Заявка",
            style = MaterialTheme.typography.titleLarge,
        )
        state.detail?.name?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Buy / Sell
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.isBuy,
                onClick = { vm.setBuy(true) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = ProfitGreen),
            ) { Text("Покупка") }
            SegmentedButton(
                selected = !state.isBuy,
                onClick = { vm.setBuy(false) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = LossRed),
            ) { Text("Продажа") }
        }

        // Market / Limit
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = state.isMarket,
                onClick = { vm.setMarket(true) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Рыночная") }
            SegmentedButton(
                selected = !state.isMarket,
                onClick = { vm.setMarket(false) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("Лимитная") }
        }

        // Lots stepper
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Лотов:", style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = { vm.changeLots(-1) }, modifier = Modifier.size(48.dp), contentPadding = PaddingZero) {
                Icon(Icons.Filled.Remove, contentDescription = "Меньше")
            }
            Text(
                state.lots.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            OutlinedButton(onClick = { vm.changeLots(1) }, modifier = Modifier.size(48.dp), contentPadding = PaddingZero) {
                Icon(Icons.Filled.Add, contentDescription = "Больше")
            }
            state.detail?.lot?.let {
                Text("× $it шт.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (!state.isMarket) {
            OutlinedTextField(
                value = state.priceInput,
                onValueChange = vm::setPrice,
                label = { Text("Цена лимитной заявки") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        vm.estimatedAmount()?.let {
            Text(
                text = "Примерная сумма: ${MoneyFormat.amount(it, currency)}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        if (state.isSuccess) {
            state.resultMessage?.let {
                Text(it, color = ProfitGreen, style = MaterialTheme.typography.bodyLarge)
            }
            FilledTonalButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Готово")
            }
        } else {
            Button(
                onClick = vm::submit,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isBuy) ProfitGreen else LossRed,
                ),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        if (state.isBuy) "Купить" else "Продать",
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

private val PaddingZero = androidx.compose.foundation.layout.PaddingValues(0.dp)
