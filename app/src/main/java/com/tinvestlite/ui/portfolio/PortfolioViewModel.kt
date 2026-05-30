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
    val yieldPercent: Double,
    val currency: String,
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
                        val value = position.currentPrice.toBigDecimal().multiply(qty)
                        PortfolioRow(
                            uid = position.instrumentUid,
                            name = info.name,
                            ticker = info.ticker,
                            logoUrl = info.logoUrl,
                            quantity = qty,
                            value = value,
                            yieldPercent = position.expectedYield.toDouble(),
                            currency = position.currentPrice.currency
                                .ifBlank { position.currentPriceCurrency ?: "rub" },
                        )
                    }
                }.awaitAll().sortedByDescending { it.value }

                val totalValue = ap.portfolio.totalAmountPortfolio.toBigDecimal()

                // All-time change in money: Σ (current − average) × quantity.
                val allTimeChange = securities.fold(BigDecimal.ZERO) { acc, p ->
                    val diff = p.currentPrice.toBigDecimal().subtract(p.averagePositionPrice.toBigDecimal())
                    acc.add(diff.multiply(p.quantity.toBigDecimal()))
                }

                // Day change in money: Σ (current − previousClose) × quantity,
                // using the day-change % from quotes to back out the previous close.
                val dayChange = securities.fold(BigDecimal.ZERO) { acc, p ->
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
    private data class InstrumentInfo(val name: String, val ticker: String, val logoUrl: String?)

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
            InstrumentInfo(detail.name, detail.ticker, InstrumentLogo.url(detail.brand))
        } else {
            val fallback = ticker?.takeIf { it.isNotBlank() } ?: figi
            InstrumentInfo(fallback, fallback, null)
        }
        infoCache[cacheKey] = resolved
        return resolved
    }
}
