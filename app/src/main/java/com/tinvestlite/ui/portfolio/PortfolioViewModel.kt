package com.tinvestlite.ui.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinvestlite.data.ApiResult
import com.tinvestlite.data.getOrNull
import com.tinvestlite.data.local.TokenStore
import com.tinvestlite.data.remote.dto.PortfolioResponse
import com.tinvestlite.data.repository.InvestRepository
import com.tinvestlite.util.toBigDecimal
import com.tinvestlite.util.toDouble
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

sealed interface PortfolioUiState {
    data object Loading : PortfolioUiState
    data class Error(val message: String) : PortfolioUiState
    data class Data(
        val totalValue: BigDecimal,
        val totalCurrency: String,
        val totalYieldPercent: Double,
        val freeCash: BigDecimal,
        val rows: List<PortfolioRow>,
    ) : PortfolioUiState
}

class PortfolioViewModel(
    private val repository: InvestRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow<PortfolioUiState>(PortfolioUiState.Loading)
    val state: StateFlow<PortfolioUiState> = _state.asStateFlow()

    private val nameCache = mutableMapOf<String, Pair<String, String>>()

    init { refresh() }

    fun refresh() {
        _state.value = PortfolioUiState.Loading
        viewModelScope.launch {
            val accountId = tokenStore.accountId
                ?: when (val acc = repository.ensureSandboxAccount()) {
                    is ApiResult.Success -> acc.data
                    is ApiResult.Error -> {
                        _state.value = PortfolioUiState.Error(acc.message)
                        return@launch
                    }
                }

            when (val result = repository.getPortfolio(accountId)) {
                is ApiResult.Success -> _state.value = buildState(result.data)
                is ApiResult.Error -> _state.value = PortfolioUiState.Error(result.message)
            }
        }
    }

    private suspend fun buildState(portfolio: PortfolioResponse): PortfolioUiState {
        val rows = portfolio.positions
            .filter { it.instrumentType != "currency" }
            .map { position ->
                viewModelScope.async {
                    val (name, ticker) = resolveName(position.instrumentUid, position.figi, position.ticker)
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

        return PortfolioUiState.Data(
            totalValue = portfolio.totalAmountPortfolio.toBigDecimal(),
            totalCurrency = portfolio.totalAmountPortfolio.currency.ifBlank { "rub" },
            totalYieldPercent = portfolio.expectedYield.toDouble(),
            freeCash = portfolio.totalAmountCurrencies.toBigDecimal(),
            rows = rows,
        )
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
