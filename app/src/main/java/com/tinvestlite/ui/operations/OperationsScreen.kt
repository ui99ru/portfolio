package com.tinvestlite.ui.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tinvestlite.data.remote.dto.Operation
import com.tinvestlite.di.AppContainer
import com.tinvestlite.ui.common.EmptyBox
import com.tinvestlite.ui.common.ErrorBox
import com.tinvestlite.ui.common.LoadingBox
import com.tinvestlite.ui.common.changeColor
import com.tinvestlite.ui.common.vmFactory
import com.tinvestlite.util.MoneyFormat
import com.tinvestlite.util.toDouble
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationsScreen(container: AppContainer) {
    val vm: OperationsViewModel = viewModel(
        factory = vmFactory { OperationsViewModel(container.repository, container.tokenStore) },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Операции") },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Обновить")
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            is OperationsUiState.Loading -> LoadingBox(Modifier.padding(padding))
            is OperationsUiState.Error -> ErrorBox(s.message, Modifier.padding(padding), vm::refresh)
            is OperationsUiState.Data ->
                if (s.operations.isEmpty()) {
                    EmptyBox("За последние 90 дней операций не было.", Modifier.padding(padding))
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                        items(s.operations, key = { it.id.ifBlank { it.date + it.type } }) { op ->
                            OperationRow(op)
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
        }
    }
}

@Composable
private fun OperationRow(op: Operation) {
    val payment = op.payment.toDouble()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = operationLabel(op),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = formatDate(op.date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = MoneyFormat.money(op.payment),
            style = MaterialTheme.typography.titleMedium,
            color = if (payment == 0.0) MaterialTheme.colorScheme.onSurface else changeColor(payment),
        )
    }
}

private fun operationLabel(op: Operation): String {
    val base = when (op.operationType) {
        "OPERATION_TYPE_BUY", "OPERATION_TYPE_BUY_CARD" -> "Покупка"
        "OPERATION_TYPE_SELL" -> "Продажа"
        "OPERATION_TYPE_BROKER_FEE" -> "Комиссия брокера"
        "OPERATION_TYPE_DIVIDEND" -> "Дивиденды"
        "OPERATION_TYPE_COUPON" -> "Купон"
        "OPERATION_TYPE_INPUT" -> "Пополнение"
        "OPERATION_TYPE_OUTPUT" -> "Вывод"
        "OPERATION_TYPE_TAX" -> "Налог"
        else -> op.type.ifBlank { op.operationType.removePrefix("OPERATION_TYPE_") }
    }
    val qty = if (op.quantity > 0) " · ${op.quantity} шт." else ""
    return base + qty
}

private val displayFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale("ru"))

private fun formatDate(raw: String): String = runCatching {
    OffsetDateTime.parse(raw).format(displayFormat)
}.getOrDefault(raw)
