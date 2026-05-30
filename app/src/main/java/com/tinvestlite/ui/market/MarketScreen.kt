package com.tinvestlite.ui.market

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tinvestlite.data.remote.dto.InstrumentShort
import com.tinvestlite.di.AppContainer
import com.tinvestlite.ui.common.EmptyBox
import com.tinvestlite.ui.common.ErrorBox
import com.tinvestlite.ui.common.InstrumentIcon
import com.tinvestlite.ui.common.vmFactory
import com.tinvestlite.util.InstrumentLogo

@Composable
fun MarketScreen(
    container: AppContainer,
    onOpenInstrument: (String) -> Unit,
) {
    val vm: MarketViewModel = viewModel(factory = vmFactory { MarketViewModel(container.repository) })
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            label = { Text("Поиск: тикер, название, ISIN") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Очистить")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        // Category chips — hidden while searching, since search spans all types.
        if (!state.isSearching) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MarketCategory.entries.forEach { category ->
                    FilterChip(
                        selected = state.category == category,
                        onClick = { vm.selectCategory(category) },
                        label = { Text(category.label) },
                    )
                }
            }
        }

        if (state.isLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
        }

        when {
            state.error != null -> ErrorBox(state.error!!, Modifier.fillMaxSize(), onRetry = vm::retry)
            state.isSearching && state.results.isEmpty() && !state.isLoading ->
                EmptyBox("Ничего не найдено по запросу «${state.query}».")
            !state.isSearching && state.results.isEmpty() && !state.isLoading ->
                EmptyBox("В разделе «${state.category.label}» пока нет инструментов.")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.results, key = { it.uid }) { item ->
                    InstrumentRow(item, onOpenInstrument)
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun InstrumentRow(item: InstrumentShort, onOpenInstrument: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = item.uid.isNotBlank()) { onOpenInstrument(item.uid) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InstrumentIcon(
                logoUrl = InstrumentLogo.url(item.brand),
                fallbackText = item.ticker.ifBlank { item.name },
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.ticker.ifBlank { item.name },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                text = instrumentTypeLabel(item.instrumentType),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun instrumentTypeLabel(type: String): String = when (type.lowercase()) {
    "share" -> "Акция"
    "bond" -> "Облигация"
    "etf" -> "Фонд"
    "currency" -> "Валюта"
    "futures" -> "Фьючерс"
    else -> type
}
