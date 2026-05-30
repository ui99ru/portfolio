package com.tinvestlite.ui.portfolio

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tinvestlite.di.AppContainer
import com.tinvestlite.ui.common.ErrorBox
import com.tinvestlite.ui.common.LoadingBox
import com.tinvestlite.ui.common.changeColor
import com.tinvestlite.ui.common.vmFactory
import com.tinvestlite.util.MoneyFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    container: AppContainer,
    onOpenInstrument: (String) -> Unit,
) {
    val vm: PortfolioViewModel = viewModel(
        factory = vmFactory { PortfolioViewModel(container.repository, container.tokenStore) },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Портфель") },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Обновить")
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            is PortfolioUiState.Loading -> LoadingBox(Modifier.padding(padding))
            is PortfolioUiState.Error -> ErrorBox(s.message, Modifier.padding(padding), vm::refresh)
            is PortfolioUiState.Data -> PortfolioContent(s, onOpenInstrument, Modifier.padding(padding))
        }
    }
}

@Composable
private fun PortfolioContent(
    data: PortfolioUiState.Data,
    onOpenInstrument: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SummaryCard(data) }

        if (data.rows.isEmpty()) {
            item {
                Text(
                    text = "В портфеле пока нет бумаг. Найди инструмент на вкладке «Рынок» " +
                        "и соверши первую сделку.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            items(data.rows, key = { it.uid }) { row ->
                PositionRow(row, onOpenInstrument)
            }
        }
    }
}

@Composable
private fun SummaryCard(data: PortfolioUiState.Data) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Стоимость портфеля",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = MoneyFormat.amount(data.totalValue, data.totalCurrency),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Доходность ${MoneyFormat.percent(data.totalYieldPercent)}",
                style = MaterialTheme.typography.bodyMedium,
                color = changeColor(data.totalYieldPercent),
            )
            Text(
                text = "Свободно: ${MoneyFormat.amount(data.freeCash, data.totalCurrency)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PositionRow(row: PortfolioRow, onOpenInstrument: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenInstrument(row.uid) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = row.ticker.ifBlank { row.name },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = "${MoneyFormat.price(row.quantity)} шт.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = MoneyFormat.amount(row.value, row.currency),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = MoneyFormat.percent(row.yieldPercent),
                style = MaterialTheme.typography.bodyMedium,
                color = changeColor(row.yieldPercent),
            )
        }
    }
}
