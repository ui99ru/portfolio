package com.tinvestlite.ui.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinvestlite.data.ApiResult
import com.tinvestlite.data.AppMode
import com.tinvestlite.data.getOrNull
import com.tinvestlite.data.local.TokenStore
import com.tinvestlite.data.repository.AccountPortfolio
import com.tinvestlite.data.repository.InvestRepository
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
    val quantity: BigDecimal,
    val value: BigDecimal,
    val yieldPercent: Double,
    val currency: String,
)

/** One account's worth of data inside the consolidated view. */
data class AccountBlock(
    val accountId: String,
    val title: String,
    val totalValue: BigDecimal,
    val yieldPercent: Double,
    val freeCash: BigDecimal,
    val currency: String,
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
        val freeCash: BigDecimal,
        val accounts: List<AccountBlock>,
        /** null → consolidated overview; otherwise the drilled-into account. */
        val selectedAccountId: String? = null,
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

    private val nameCache = mutableMapOf<String, Pair<String, String>>()

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
                is ApiResult.Success -> _state.value = buildState(result.data)
                is ApiResult.Error -> _state.value = PortfolioUiState.Error(result.message)
            }
        }
    }

    fun selectAccount(accountId: String?) {
        val current = _state.value
        if (current is PortfolioUiState.Data) {
            _state.value = current.copy(selectedAccountId = accountId)
        }
    }

    private suspend fun buildState(data: List<AccountPortfolio>): PortfolioUiState = coroutineScope {
        val isReal = tokenStore.mode.value == AppMode.Real
        if (data.isEmpty()) {
            return@coroutineScope PortfolioUiState.Data(
                isReal = isReal,
                totalValue = BigDecimal.ZERO,
                totalCurrency = "rub",
                totalYieldPercent = 0.0,
                freeCash = BigDecimal.ZERO,
                accounts = emptyList(),
            )
        }

        val blocks = data.map { ap ->
            async {
                val rows = ap.portfolio.positions
                    .filter { it.instrumentType != "currency" }
                    .map { position ->
                        async {
                            val (name, ticker) =
                                resolveName(position.instrumentUid, position.figi, position.ticker)
                            val qty = position.quantity.toBigDecimal()
                            val value = position.currentPrice.toBigDecimal().multiply(qty)
                            PortfolioRow(
                                uid = position.instrumentUid,
                                name = name,
                                ticker = ticker,
                                quantity = qty,
                                value = value,
                                yieldPercent = position.expectedYield.toDouble(),
                                currency = position.currentPrice.currency
                                    .ifBlank { position.currentPriceCurrency ?: "rub" },
                            )
                        }
                    }
                    .awaitAll()
                    .sortedByDescending { it.value }

                AccountBlock(
                    accountId = ap.account.id,
                    title = accountTitle(ap.account.name, ap.account.type, ap.account.id),
                    totalValue = ap.portfolio.totalAmountPortfolio.toBigDecimal(),
                    yieldPercent = ap.portfolio.expectedYield.toDouble(),
                    freeCash = ap.portfolio.totalAmountCurrencies.toBigDecimal(),
                    currency = ap.portfolio.totalAmountPortfolio.currency.ifBlank { "rub" },
                    rows = rows,
                )
            }
        }.awaitAll()

        val totalValue = blocks.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.totalValue) }
        val totalCash = blocks.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.freeCash) }
        // Portfolio-value-weighted average yield across accounts.
        val weightedYield = if (totalValue.signum() != 0) {
            blocks.sumOf { it.yieldPercent * it.totalValue.toDouble() } / totalValue.toDouble()
        } else {
            0.0
        }

        PortfolioUiState.Data(
            isReal = isReal,
            totalValue = totalValue,
            totalCurrency = blocks.first().currency,
            totalYieldPercent = weightedYield,
            freeCash = totalCash,
            accounts = blocks,
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

    private suspend fun resolveName(uid: String, figi: String, ticker: String?): Pair<String, String> {
        if (!ticker.isNullOrBlank()) return (ticker to ticker)
        nameCache[uid]?.let { return it }
        val detail = repository.getInstrument(uid).getOrNull()
        val resolved = if (detail != null && detail.name.isNotBlank()) {
            detail.name to detail.ticker
        } else {
            (figi to figi)
        }
        nameCache[uid] = resolved
        return resolved
    }
}
