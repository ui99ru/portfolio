package com.tinvestlite.ui.instrument

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.tinvestlite.ui.common.InstrumentIcon
import com.tinvestlite.ui.common.LoadingBox
import com.tinvestlite.ui.common.vmFactory
import com.tinvestlite.ui.theme.LossRed
import com.tinvestlite.ui.theme.ProfitGreen
import com.tinvestlite.util.InstrumentLogo
import com.tinvestlite.util.MoneyFormat
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstrumentScreen(
    container: AppContainer,
    uid: String,
    onBack: () -> Unit,
    onTrade: (buy: Boolean) -> Unit,
) {
    val vm: InstrumentViewModel = viewModel(
        factory = vmFactory { InstrumentViewModel(container.repository, uid) },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val mode by container.tokenStore.mode.collectAsStateWithLifecycle()
    val tradingEnabled = !mode.isReal
    val title = state.detail?.let { it.ticker.ifBlank { it.name } } ?: "Инструмент"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        bottomBar = {
            if (state.detail != null) {
                if (tradingEnabled) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { onTrade(true) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = ProfitGreen),
                        ) { Text("Купить", color = MaterialTheme.colorScheme.onPrimary) }
                        Button(
                            onClick = { onTrade(false) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = LossRed),
                        ) { Text("Продать", color = MaterialTheme.colorScheme.onPrimary) }
                    }
                } else {
                    Text(
                        text = "Реальный счёт — только просмотр. Торговля доступна в режиме песочницы.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingBox(Modifier.padding(padding))
            state.error != null -> ErrorBox(state.error!!, Modifier.padding(padding), vm::load)
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PriceHeader(state)
                TimeframeRow(state.timeframe, vm::selectTimeframe)
                if (state.candlesLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (state.candles.isNotEmpty()) {
                    CandleChart(state.candles)
                } else if (!state.candlesLoading) {
                    Text(
                        "Нет данных по свечам за выбранный период.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OrderBookRow("Покупка (bid)", state.bestBid)
                OrderBookRow("Продажа (ask)", state.bestAsk)
            }
        }
    }
}

@Composable
private fun PriceHeader(state: InstrumentUiState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.detail?.let { detail ->
            InstrumentIcon(
                logoUrl = InstrumentLogo.url(detail.brand),
                fallbackText = detail.ticker.ifBlank { detail.name },
                size = 48,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.detail?.name?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = state.lastPrice?.let { MoneyFormat.price(it) + " " + MoneyFormat.currencySymbol(state.detail?.currency.orEmpty()) }
                    ?: "—",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeframeRow(selected: Timeframe, onSelect: (Timeframe) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Timeframe.entries.forEach { tf ->
            FilterChip(
                selected = tf == selected,
                onClick = { onSelect(tf) },
                label = { Text(tf.label) },
            )
        }
    }
}

@Composable
private fun OrderBookRow(label: String, price: BigDecimal?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(price?.let { MoneyFormat.price(it) } ?: "—", style = MaterialTheme.typography.bodyLarge)
    }
}
