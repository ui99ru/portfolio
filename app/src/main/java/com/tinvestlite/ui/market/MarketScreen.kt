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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tinvestlite.di.AppContainer
import com.tinvestlite.ui.common.EmptyBox
import com.tinvestlite.ui.common.ErrorBox
import com.tinvestlite.ui.common.InstrumentIcon
import com.tinvestlite.ui.common.changeColor
import com.tinvestlite.ui.common.vmFactory
import com.tinvestlite.util.InstrumentLogo
import com.tinvestlite.util.MoneyFormat

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

        if (!state.isSearching) {
            // Macro sections as tabs.
            TabRow(selectedTabIndex = state.section.ordinal) {
                MarketSection.entries.forEach { section ->
                    Tab(
                        selected = state.section == section,
                        onClick = { vm.selectSection(section) },
                        text = { Text(section.label) },
                    )
                }
            }

            // Sub-section chips (hidden when the section has none, e.g. ETFs).
            if (state.subSections.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.subSections.forEach { sub ->
                        FilterChip(
                            selected = state.sub == sub,
                            onClick = { vm.selectSub(sub) },
                            label = { Text(sub.label) },
                        )
                    }
                }
            }
        }

        if (state.isLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
        }

        when {
            state.error != null -> ErrorBox(state.error!!, Modifier.fillMaxSize(), onRetry = vm::retry)
            state.isSearching && state.items.isEmpty() && !state.isLoading ->
                EmptyBox("Ничего не найдено по запросу «${state.query}».")
            !state.isSearching && state.items.isEmpty() && !state.isLoading ->
                EmptyBox("В этом разделе пока нет инструментов.")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.items, key = { it.instrument.uid }) { item ->
                    InstrumentRow(item, onOpenInstrument)
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun InstrumentRow(item: MarketItem, onOpenInstrument: (String) -> Unit) {
    val instrument = item.instrument
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = instrument.uid.isNotBlank()) { onOpenInstrument(instrument.uid) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InstrumentIcon(
                logoUrl = InstrumentLogo.url(instrument.brand),
                fallbackText = instrument.ticker.ifBlank { instrument.name },
            )
            // Name on top, ticker below (matches the official app).
            Column(Modifier.weight(1f)) {
                Text(
                    text = instrument.name.ifBlank { instrument.ticker },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = instrument.ticker,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Quote: last price + day change.
            val quote = item.quote
            if (quote != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = MoneyFormat.amount(
                            java.math.BigDecimal.valueOf(quote.lastPrice),
                            instrument.currency,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = MoneyFormat.percent(quote.dayChangePercent),
                        style = MaterialTheme.typography.bodyMedium,
                        color = changeColor(quote.dayChangePercent),
                    )
                }
            }
        }
    }
}
