package com.tinvestlite.ui.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinvestlite.data.ApiResult
import com.tinvestlite.data.AppMode
import com.tinvestlite.data.getOrNull
import com.tinvestlite.data.local.TokenStore
import com.tinvestlite.data.repository.AccountPortfolio
import com.tinvestlite.data.repository.InvestRepository
import com.tinvestlite.data.repository.OverviewItem
import com.tinvestlite.data.repository.Quote
import com.tinvestlite.util.InstrumentLogo
import com.tinvestlite.util.toBigDecimal
import com.tinvestlite.util.toDouble
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class PortfolioRow(
    val uid: String,
    val name: String,
    val ticker: String,
    val logoUrl: String?,
    val quantity: BigDecimal,
    val value: BigDecimal,
    val allTimeChange: BigDecimal,
    val allTimePercent: Double,
    val dayChange: BigDecimal,
    val dayPercent: Double,
    val currency: String,
    val instrumentType: String,
    val frozen: Boolean,
)

/** Sub-group within the liquid (Russian) bucket. */
enum class AssetGroup(val title: String) {
    Shares("Акции"),
    Bonds("Облигации"),
    Etfs("Фонды"),
    Other("Другие"),
}

/** A titled subgroup of positions; rubTotal is null when not computable (FX). */
data class PositionSubgroup(
    val title: String,
    val rubTotal: BigDecimal?,
    val rows: List<PortfolioRow>,
)

/** A top-level collapsible section ("Ликвидные"/"Замороженные"). */
data class PositionSection(
    val key: String,
    val title: String,
    val rubTotal: BigDecimal,
    val subgroups: List<PositionSubgroup>,
)

/** Ordered sections: liquid first, then frozen. Empty ones are omitted. */
data class GroupedPositions(
    val sections: List<PositionSection>,
)

/** Which time period the change figures refer to. */
enum class Period(val label: String) { Day("День"), AllTime("За всё время") }

/** One account's worth of data inside the consolidated view. */
data class AccountBlock(
    val accountId: String,
    val title: String,
    val totalValue: BigDecimal,
    val currency: String,
    val allTimeChange: BigDecimal,
    val allTimePercent: Double,
    val dayChange: BigDecimal,
    val dayPercent: Double,
    val rows: List<PortfolioRow>,
    val groups: GroupedPositions,
)

sealed interface PortfolioUiState {
    data object Loading : PortfolioUiState
    data class Error(val message: String) : PortfolioUiState
    data class Data(
        val isReal: Boolean,
        val totalValue: BigDecimal,
        val totalCurrency: String,
        val totalYieldPercent: Double,
        val totalAllTimeChange: BigDecimal,
        val totalDayChange: BigDecimal,
        val accounts: List<AccountBlock>,
        val overview: List<OverviewItem> = emptyList(),
        /** null → consolidated overview; otherwise the drilled-into account. */
        val selectedAccountId: String? = null,
        val period: Period = Period.AllTime,
    ) : PortfolioUiState {
        val isConsolidated: Boolean get() = selectedAccountId == null
        val selectedAccount: AccountBlock?
            get() = accounts.firstOrNull { it.accountId == selectedAccountId }
        val showAccountList: Boolean get() = accounts.size > 1
    }
}

class PortfolioViewModel(
    private val repository: InvestRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow<PortfolioUiState>(PortfolioUiState.Loading)
    val state: StateFlow<PortfolioUiState> = _state.asStateFlow()

    private val infoCache = mutableMapOf<String, InstrumentInfo>()

    init {
        // Reload whenever the mode (sandbox/real) changes.
        viewModelScope.launch {
            tokenStore.mode.collect { refresh() }
        }
    }

    fun refresh() {
        _state.value = PortfolioUiState.Loading
        viewModelScope.launch {
            // Make sure at least one account exists/selected for this mode.
            when (val ensured = repository.ensureAccount()) {
                is ApiResult.Error -> {
                    _state.value = PortfolioUiState.Error(ensured.message)
                    return@launch
                }
                is ApiResult.Success -> Unit
            }

            when (val result = repository.getAllPortfolios()) {
                is ApiResult.Success -> {
                    _state.value = buildState(result.data, overview = emptyList())
                    // Load the market overview in the background; merge when ready.
                    loadOverview()
                }
                is ApiResult.Error -> _state.value = PortfolioUiState.Error(result.message)
            }
        }
    }

    private fun loadOverview() {
        viewModelScope.launch {
            val overview = (repository.getMarketOverview() as? ApiResult.Success)?.data ?: return@launch
            val current = _state.value
            if (current is PortfolioUiState.Data) {
                _state.value = current.copy(overview = overview)
            }
        }
    }

    fun selectAccount(accountId: String?) {
        val current = _state.value
        if (current is PortfolioUiState.Data) {
            _state.value = current.copy(selectedAccountId = accountId)
        }
    }

    fun setPeriod(period: Period) {
        val current = _state.value
        if (current is PortfolioUiState.Data) {
            _state.value = current.copy(period = period)
        }
    }

    private suspend fun buildState(
        data: List<AccountPortfolio>,
        overview: List<OverviewItem>,
    ): PortfolioUiState = coroutineScope {
        val isReal = tokenStore.mode.value == AppMode.Real
        if (data.isEmpty()) {
            return@coroutineScope PortfolioUiState.Data(
                isReal = isReal,
                totalValue = BigDecimal.ZERO,
                totalCurrency = "rub",
                totalYieldPercent = 0.0,
                totalAllTimeChange = BigDecimal.ZERO,
                totalDayChange = BigDecimal.ZERO,
                accounts = emptyList(),
                overview = overview,
            )
        }

        // Day-change needs previous-close quotes for every held instrument.
        val allUids = data.flatMap { ap ->
            ap.portfolio.positions
                .filter { it.instrumentType != "currency" && it.instrumentUid.isNotBlank() }
                .map { it.instrumentUid }
        }.distinct()
        val quotes: Map<String, Quote> =
            when (val r = repository.getQuotes(allUids)) {
                is ApiResult.Success -> r.data
                is ApiResult.Error -> emptyMap()
            }

        val blocks = data.map { ap ->
            async {
                val securities = ap.portfolio.positions.filter { it.instrumentType != "currency" }
                val rows = securities.map { position ->
                    async {
                        val info = resolveInfo(position.instrumentUid, position.figi, position.ticker)
                        val qty = position.quantity.toBigDecimal()
                        val price = position.currentPrice.toBigDecimal()
                        val value = price.multiply(qty)
                        val currency = position.currentPrice.currency
                            .ifBlank { position.currentPriceCurrency ?: "rub" }

                        // All-time change in the position's currency.
                        val avg = position.averagePositionPrice.toBigDecimal()
                        val rowAllTime = price.subtract(avg).multiply(qty)
                        val rowAllTimePct = if (avg.signum() != 0) {
                            price.subtract(avg).toDouble() / avg.toDouble() * 100.0
                        } else {
                            0.0
                        }

                        // Day change from the previous close (derived from quote %).
                        val q = quotes[position.instrumentUid]
                        val dayPct = q?.dayChangePercent ?: 0.0
                        val prevClose = if (q != null && dayPct != -100.0) {
                            price.divide(
                                BigDecimal.valueOf(1.0 + dayPct / 100.0),
                                10, java.math.RoundingMode.HALF_UP,
                            )
                        } else {
                            price
                        }
                        val rowDay = price.subtract(prevClose).multiply(qty)

                        PortfolioRow(
                            uid = position.instrumentUid,
                            name = info.name,
                            ticker = info.ticker,
                            logoUrl = info.logoUrl,
                            quantity = qty,
                            value = value,
                            allTimeChange = rowAllTime,
                            allTimePercent = rowAllTimePct,
                            dayChange = rowDay,
                            dayPercent = dayPct,
                            currency = currency,
                            instrumentType = position.instrumentType,
                            frozen = isFrozen(info, currency),
                        )
                    }
                }.awaitAll().sortedByDescending { it.value }

                val totalValue = ap.portfolio.totalAmountPortfolio.toBigDecimal()

                // Aggregate change is summed only over RUB-priced positions:
                // mixing foreign-currency deltas (USD/HKD) as if they were roubles
                // would distort the total, and we have no per-position FX rate.
                val rubSecurities = securities.filter {
                    it.currentPrice.currency.ifBlank { it.currentPriceCurrency ?: "rub" }
                        .equals("rub", ignoreCase = true)
                }

                // All-time change in roubles: Σ (current − average) × quantity.
                val allTimeChange = rubSecurities.fold(BigDecimal.ZERO) { acc, p ->
                    val diff = p.currentPrice.toBigDecimal().subtract(p.averagePositionPrice.toBigDecimal())
                    acc.add(diff.multiply(p.quantity.toBigDecimal()))
                }

                // Day change in roubles: Σ (current − previousClose) × quantity,
                // using the day-change % from quotes to back out the previous close.
                val dayChange = rubSecurities.fold(BigDecimal.ZERO) { acc, p ->
                    val q = quotes[p.instrumentUid] ?: return@fold acc
                    val current = p.currentPrice.toBigDecimal()
                    val prevClose = if (q.dayChangePercent != -100.0) {
                        current.divide(
                            BigDecimal.valueOf(1.0 + q.dayChangePercent / 100.0),
                            10, java.math.RoundingMode.HALF_UP,
                        )
                    } else {
                        current
                    }
                    acc.add(current.subtract(prevClose).multiply(p.quantity.toBigDecimal()))
                }

                fun pct(change: BigDecimal): Double {
                    val base = totalValue.subtract(change)
                    return if (base.signum() != 0) change.toDouble() / base.toDouble() * 100.0 else 0.0
                }

                AccountBlock(
                    accountId = ap.account.id,
                    title = accountTitle(ap.account.name, ap.account.type, ap.account.id),
                    totalValue = totalValue,
                    currency = ap.portfolio.totalAmountPortfolio.currency.ifBlank { "rub" },
                    allTimeChange = allTimeChange,
                    allTimePercent = ap.portfolio.expectedYield.toDouble(),
                    dayChange = dayChange,
                    dayPercent = pct(dayChange),
                    rows = rows,
                    groups = buildGroups(
                        rows,
                        accountTotalRub = totalValue,
                        cashRub = ap.portfolio.totalAmountCurrencies.toBigDecimal(),
                    ),
                )
            }
        }.awaitAll()

        val totalValue = blocks.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.totalValue) }
        val totalAllTime = blocks.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.allTimeChange) }
        val totalDay = blocks.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.dayChange) }
        // Value-weighted all-time yield % across accounts.
        val weightedYield = if (totalValue.signum() != 0) {
            blocks.sumOf { it.allTimePercent * it.totalValue.toDouble() } / totalValue.toDouble()
        } else {
            0.0
        }

        PortfolioUiState.Data(
            isReal = isReal,
            totalValue = totalValue,
            totalCurrency = blocks.first().currency,
            totalYieldPercent = weightedYield,
            totalAllTimeChange = totalAllTime,
            totalDayChange = totalDay,
            accounts = blocks,
            overview = overview,
        )
    }

    private fun accountTitle(name: String, type: String, id: String): String {
        if (name.isNotBlank()) return name
        return when (type) {
            "ACCOUNT_TYPE_TINKOFF" -> "Брокерский счёт"
            "ACCOUNT_TYPE_TINKOFF_IIS" -> "ИИС"
            "ACCOUNT_TYPE_INVEST_BOX" -> "Инвесткопилка"
            else -> "Счёт ${id.take(8)}"
        }
    }

    /** Resolved display info for a position, including the brand logo URL. */
    private data class InstrumentInfo(
        val name: String,
        val ticker: String,
        val logoUrl: String?,
        val countryOfRisk: String,
        val apiTradeAvailable: Boolean,
    )

    private suspend fun resolveInfo(uid: String, figi: String, ticker: String?): InstrumentInfo {
        val cacheKey = uid.ifBlank { figi }
        infoCache[cacheKey]?.let { return it }

        // Real portfolios sometimes omit the UID; fall back to a FIGI lookup so
        // we still get the human-readable name and logo.
        val detail = uid.takeIf { it.isNotBlank() }
            ?.let { repository.getInstrument(it).getOrNull() }
            ?: figi.takeIf { it.isNotBlank() }
                ?.let { repository.getInstrumentByFigi(it).getOrNull() }

        val resolved = if (detail != null && detail.name.isNotBlank()) {
            InstrumentInfo(
                name = detail.name,
                ticker = detail.ticker,
                logoUrl = InstrumentLogo.url(detail.brand),
                countryOfRisk = detail.countryOfRisk,
                apiTradeAvailable = detail.apiTradeAvailableFlag,
            )
        } else {
            val fallback = ticker?.takeIf { it.isNotBlank() } ?: figi
            InstrumentInfo(fallback, fallback, null, "", true)
        }
        infoCache[cacheKey] = resolved
        return resolved
    }

    /** Frozen = foreign (country ≠ RU), FinEx funds, or non-tradable in non-RUB. */
    private fun isFrozen(info: InstrumentInfo, currency: String): Boolean {
        val foreign = info.countryOfRisk.isNotBlank() && !info.countryOfRisk.equals("RU", ignoreCase = true)
        val finex = info.ticker.startsWith("FX", ignoreCase = true) ||
            info.name.contains("FinEx", ignoreCase = true)
        val nonRub = !currency.equals("rub", ignoreCase = true)
        val nonTradableForeign = !info.apiTradeAvailable && nonRub
        return foreign || finex || nonTradableForeign
    }

    private fun groupOf(instrumentType: String): AssetGroup = when (instrumentType.lowercase()) {
        "share" -> AssetGroup.Shares
        "bond" -> AssetGroup.Bonds
        "etf" -> AssetGroup.Etfs
        else -> AssetGroup.Other
    }

    /** FinEx fund detection (separated from other foreign assets). */
    private fun isFinex(row: PortfolioRow): Boolean =
        row.ticker.startsWith("FX", ignoreCase = true) || row.name.contains("FinEx", ignoreCase = true)

    /**
     * Build liquid + frozen sections (liquid first). Liquid is RUB-priced, so
     * subtotals sum directly. Frozen section total is derived as
     * (account total − liquid − cash) to avoid FX conversion; its subgroups
     * (FinEx funds vs foreign, kept separate) show no rouble subtotal.
     */
    private fun buildGroups(
        rows: List<PortfolioRow>,
        accountTotalRub: BigDecimal,
        cashRub: BigDecimal,
    ): GroupedPositions {
        val frozenRows = rows.filter { it.frozen }
        val liquidRows = rows.filterNot { it.frozen }

        fun rubSum(list: List<PortfolioRow>): BigDecimal =
            list.filter { it.currency.equals("rub", ignoreCase = true) }
                .fold(BigDecimal.ZERO) { acc, r -> acc.add(r.value) }

        val sections = mutableListOf<PositionSection>()

        // ---- Liquid (Russian), sub-grouped by asset type ----
        if (liquidRows.isNotEmpty()) {
            val liquidSubs = AssetGroup.entries.mapNotNull { group ->
                val groupRows = liquidRows.filter { groupOf(it.instrumentType) == group }
                if (groupRows.isEmpty()) null
                else PositionSubgroup(group.title, rubSum(groupRows), groupRows)
            }
            sections += PositionSection(
                key = "liquid",
                title = "Ликвидные",
                rubTotal = rubSum(liquidRows),
                subgroups = liquidSubs,
            )
        }

        // ---- Frozen: FinEx funds kept separate from foreign assets ----
        if (frozenRows.isNotEmpty()) {
            val finexRows = frozenRows.filter { isFinex(it) }
            val foreignRows = frozenRows.filterNot { isFinex(it) }
            val frozenSubs = buildList {
                if (foreignRows.isNotEmpty()) {
                    AssetGroup.entries.forEach { group ->
                        val gr = foreignRows.filter { groupOf(it.instrumentType) == group }
                        if (gr.isNotEmpty()) add(PositionSubgroup(group.title, null, gr))
                    }
                }
                if (finexRows.isNotEmpty()) add(PositionSubgroup("Фонды FinEx", null, finexRows))
            }
            val frozenRub = accountTotalRub.subtract(rubSum(liquidRows)).subtract(cashRub).max(BigDecimal.ZERO)
            sections += PositionSection(
                key = "frozen",
                title = "Замороженные",
                rubTotal = frozenRub,
                subgroups = frozenSubs,
            )
        }

        return GroupedPositions(sections = sections)
    }
}
