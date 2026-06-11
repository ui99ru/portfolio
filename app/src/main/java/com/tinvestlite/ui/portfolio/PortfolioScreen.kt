package com.tinvestlite.ui.portfolio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tinvestlite.data.repository.OverviewItem
import com.tinvestlite.di.AppContainer
import com.tinvestlite.ui.common.ErrorBox
import com.tinvestlite.ui.common.InstrumentIcon
import com.tinvestlite.ui.common.LoadingBox
import com.tinvestlite.ui.common.changeColor
import com.tinvestlite.ui.common.vmFactory
import com.tinvestlite.util.MoneyFormat
import java.math.BigDecimal

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

    val data = state as? PortfolioUiState.Data
    val drilledIn = data?.isConsolidated == false

    Scaffold(
        // Nested inside HomeScreen's Scaffold, which already applies the system
        // bar insets — zero them here to avoid doubled top/bottom padding.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(if (drilledIn) data?.selectedAccount?.title ?: "Счёт" else "Портфель") },
                navigationIcon = {
                    if (drilledIn) {
                        IconButton(onClick = { vm.selectAccount(null) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
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
            is PortfolioUiState.Data ->
                if (s.isConsolidated) {
                    ConsolidatedContent(s, vm::selectAccount, vm::setPeriod, onOpenInstrument, Modifier.padding(padding))
                } else {
                    val account = s.selectedAccount
                    if (account == null) {
                        ErrorBox("Счёт не найден", Modifier.padding(padding)) { vm.selectAccount(null) }
                    } else {
                        AccountDetailContent(account, s.period, vm::setPeriod, onOpenInstrument, Modifier.padding(padding))
                    }
                }
        }
    }
}

@Composable
private fun ConsolidatedContent(
    data: PortfolioUiState.Data,
    onSelectAccount: (String) -> Unit,
    onSetPeriod: (Period) -> Unit,
    onOpenInstrument: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val collapsed = remember { mutableStateListOf<String>() }
    val onToggle: (String) -> Unit = { key ->
        if (key in collapsed) collapsed.remove(key) else collapsed.add(key)
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SummaryCard(data, onSetPeriod) }

        if (data.accounts.isEmpty()) {
            item { EmptyHint(data.isReal) }
            if (data.overview.isNotEmpty()) {
                item { MarketOverviewSection(data.overview, onOpenInstrument) }
            }
            return@LazyColumn
        }

        if (data.showAccountList) {
            // Multiple accounts → show each as a tappable card to drill in.
            item {
                Text(
                    "Счета",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            items(data.accounts, key = { it.accountId }) { account ->
                AccountCard(account, onSelectAccount)
            }
        } else {
            // Single account → show its positions inline, grouped.
            val only = data.accounts.first()
            if (only.rows.isEmpty()) {
                item { EmptyHint(data.isReal) }
            } else {
                groupedPositions(only.groups, collapsed.toSet(), data.period, onToggle, onOpenInstrument)
            }
        }

        // Market overview goes at the bottom, after positions.
        if (data.overview.isNotEmpty()) {
            item { MarketOverviewSection(data.overview, onOpenInstrument) }
        }
    }
}

/** Renders collapsible sections (liquid → frozen) with subgroups and subtotals. */
private fun androidx.compose.foundation.lazy.LazyListScope.groupedPositions(
    groups: GroupedPositions,
    collapsed: Set<String>,
    period: Period,
    onToggle: (String) -> Unit,
    onOpenInstrument: (String) -> Unit,
) {
    groups.sections.forEach { section ->
        val sectionCollapsed = section.key in collapsed
        item(key = "sec-${section.key}") {
            SectionHeader(
                title = section.title,
                rubTotal = section.rubTotal,
                collapsed = sectionCollapsed,
                onClick = { onToggle(section.key) },
            )
        }
        if (!sectionCollapsed) {
            section.subgroups.forEach { sub ->
                val subKey = "${section.key}/${sub.title}"
                // A subgroup header for a single asset just duplicates the row —
                // skip it and show the row directly (always expanded).
                val showHeader = sub.rows.size > 1
                val subCollapsed = showHeader && subKey in collapsed
                if (showHeader) {
                    item(key = "sub-$subKey") {
                        SubgroupHeader(
                            title = sub.title,
                            rubTotal = sub.rubTotal,
                            collapsed = subCollapsed,
                            onClick = { onToggle(subKey) },
                        )
                    }
                }
                if (!subCollapsed) {
                    items(sub.rows, key = { "$subKey-${it.uid}" }) { row ->
                        PositionRow(row, period, onOpenInstrument)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, rubTotal: BigDecimal, collapsed: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 14.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                imageVector = if (collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (rubTotal.signum() != 0) {
            Text(
                MoneyFormat.amount(rubTotal, "rub"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SubgroupHeader(title: String, rubTotal: BigDecimal?, collapsed: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                imageVector = if (collapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (rubTotal != null && rubTotal.signum() != 0) {
            Text(
                MoneyFormat.amount(rubTotal, "rub"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccountDetailContent(
    account: AccountBlock,
    period: Period,
    onSetPeriod: (Period) -> Unit,
    onOpenInstrument: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val collapsed = remember { mutableStateListOf<String>() }
    val onToggle: (String) -> Unit = { key ->
        if (key in collapsed) collapsed.remove(key) else collapsed.add(key)
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { AccountSummaryCard(account, period, onSetPeriod) }
        if (account.rows.isEmpty()) {
            item {
                Text(
                    "На этом счёте нет бумаг.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            groupedPositions(account.groups, collapsed.toSet(), period, onToggle, onOpenInstrument)
        }
    }
}

/** Money + percent change line, e.g. "+1 234,00 ₽ · +1,23%". */
private fun changeText(money: BigDecimal, percent: Double, currency: String): String {
    val sign = if (money.signum() >= 0) "+" else ""
    return "$sign${MoneyFormat.amount(money, currency)} · ${MoneyFormat.percent(percent)}"
}

/** Clean two-option pill toggle (replaces the bulky SegmentedButton). */
@Composable
private fun PeriodToggle(period: Period, onSetPeriod: (Period) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Period.entries.forEach { p ->
            val selected = p == period
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                    .clickable { onSetPeriod(p) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(
                    p.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(data: PortfolioUiState.Data, onSetPeriod: (Period) -> Unit) {
    val money = if (data.period == Period.Day) data.totalDayChange else data.totalAllTimeChange
    // Day percent is shown only for all-time (weighted) here; day % is derived per account.
    val percent = if (data.period == Period.Day) {
        val base = data.totalValue.subtract(data.totalDayChange)
        if (base.signum() != 0) data.totalDayChange.toDouble() / base.toDouble() * 100.0 else 0.0
    } else {
        data.totalYieldPercent
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (data.showAccountList) "Все счета" else "Стоимость портфеля",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (data.isReal) ReadOnlyChip()
            }
            Text(
                text = MoneyFormat.amount(data.totalValue, data.totalCurrency),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = changeText(money, percent, data.totalCurrency),
                style = MaterialTheme.typography.bodyMedium,
                color = changeColor(money.toDouble()),
            )
            PeriodToggle(data.period, onSetPeriod)
        }
    }
}

@Composable
private fun AccountSummaryCard(account: AccountBlock, period: Period, onSetPeriod: (Period) -> Unit) {
    val money = if (period == Period.Day) account.dayChange else account.allTimeChange
    val percent = if (period == Period.Day) account.dayPercent else account.allTimePercent
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = MoneyFormat.amount(account.totalValue, account.currency),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = changeText(money, percent, account.currency),
                style = MaterialTheme.typography.bodyMedium,
                color = changeColor(money.toDouble()),
            )
            PeriodToggle(period, onSetPeriod)
        }
    }
}

@Composable
private fun AccountCard(account: AccountBlock, onSelect: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(account.accountId) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(account.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${account.rows.size} позиц. · ${MoneyFormat.percent(account.allTimePercent)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = changeColor(account.allTimePercent),
                )
            }
            Text(
                text = MoneyFormat.amount(account.totalValue, account.currency),
                style = MaterialTheme.typography.titleMedium,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Compact colored "view-only" chip shown in the summary header. */
@Composable
private fun ReadOnlyChip() {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            "Только просмотр",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Collapsible "market overview" card: USD, MOEX index, gold, Brent, BTC. */
@Composable
private fun MarketOverviewSection(
    items: List<OverviewItem>,
    onOpenInstrument: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Обзор рынка",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Свернуть" else "Развернуть",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                items.forEach { item ->
                    OverviewRow(item, onOpenInstrument)
                }
            }
        }
    }
}

@Composable
private fun OverviewRow(item: OverviewItem, onOpenInstrument: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.uid.isNotBlank()) { onOpenInstrument(item.uid) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InstrumentIcon(logoUrl = item.logoUrl, fallbackText = item.title, size = 32)
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = MoneyFormat.amount(
                    java.math.BigDecimal.valueOf(item.quote.lastPrice),
                    item.currency,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = MoneyFormat.percent(item.quote.dayChangePercent),
                style = MaterialTheme.typography.bodyMedium,
                color = changeColor(item.quote.dayChangePercent),
            )
        }
    }
}

@Composable
private fun EmptyHint(isReal: Boolean) {
    Text(
        text = if (isReal) {
            "На счетах нет открытых позиций."
        } else {
            "В портфеле пока нет бумаг. Найди инструмент на вкладке «Рынок» " +
                "и соверши первую сделку."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun PositionRow(row: PortfolioRow, period: Period, onOpenInstrument: (String) -> Unit) {
    val change = if (period == Period.Day) row.dayChange else row.allTimeChange
    val percent = if (period == Period.Day) row.dayPercent else row.allTimePercent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = row.uid.isNotBlank()) { onOpenInstrument(row.uid) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InstrumentIcon(
            logoUrl = row.logoUrl,
            fallbackText = row.ticker.ifBlank { row.name },
        )
        Column(Modifier.weight(1f)) {
            // Name on top, ticker below (matches the official app and Market).
            Text(
                text = row.name.ifBlank { row.ticker },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = if (row.ticker.isNotBlank()) {
                    "${row.ticker} · ${MoneyFormat.price(row.quantity)} шт."
                } else {
                    "${MoneyFormat.price(row.quantity)} шт."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = MoneyFormat.amount(row.value, row.currency),
                style = MaterialTheme.typography.titleMedium,
            )
            // Change in the asset's own currency for the selected period.
            val sign = if (change.signum() >= 0) "+" else ""
            Text(
                text = "$sign${MoneyFormat.amount(change, row.currency)} · ${MoneyFormat.percent(percent)}",
                style = MaterialTheme.typography.bodyMedium,
                color = changeColor(change.toDouble()),
            )
        }
    }
}
