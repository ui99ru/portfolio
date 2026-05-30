package com.tinvestlite.ui.trade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinvestlite.data.ApiResult
import com.tinvestlite.data.getOrNull
import com.tinvestlite.data.local.TokenStore
import com.tinvestlite.data.remote.dto.InstrumentDetail
import com.tinvestlite.data.repository.InvestRepository
import com.tinvestlite.util.toBigDecimal
import com.tinvestlite.util.toQuotation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class TradeUiState(
    val isLoading: Boolean = true,
    val detail: InstrumentDetail? = null,
    val isBuy: Boolean = true,
    val isMarket: Boolean = true,
    val lots: Int = 1,
    val priceInput: String = "",
    val lastPrice: BigDecimal? = null,
    val isSubmitting: Boolean = false,
    val resultMessage: String? = null,
    val isSuccess: Boolean = false,
    val error: String? = null,
)

class TradeViewModel(
    private val repository: InvestRepository,
    private val tokenStore: TokenStore,
    private val uid: String,
    startAsBuy: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(TradeUiState(isBuy = startAsBuy))
    val state: StateFlow<TradeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val detail = repository.getInstrument(uid).getOrNull()
            val last = repository.getLastPrice(uid).getOrNull()?.toBigDecimal()
            _state.value = _state.value.copy(
                isLoading = false,
                detail = detail,
                lastPrice = last,
                priceInput = last?.toPlainString().orEmpty(),
            )
        }
    }

    fun setBuy(buy: Boolean) { _state.value = _state.value.copy(isBuy = buy) }
    fun setMarket(market: Boolean) { _state.value = _state.value.copy(isMarket = market) }
    fun setPrice(value: String) { _state.value = _state.value.copy(priceInput = value) }

    fun changeLots(delta: Int) {
        val next = (_state.value.lots + delta).coerceAtLeast(1)
        _state.value = _state.value.copy(lots = next)
    }

    /** Estimated order amount = lots × lot size × price. */
    fun estimatedAmount(): BigDecimal? {
        val s = _state.value
        val lot = s.detail?.lot ?: return null
        val price = if (s.isMarket) s.lastPrice else parsePrice()
        price ?: return null
        return price.multiply(BigDecimal(lot)).multiply(BigDecimal(s.lots))
    }

    private fun parsePrice(): BigDecimal? =
        _state.value.priceInput.replace(',', '.').toBigDecimalOrNull()

    fun submit() {
        val s = _state.value
        val accountId = tokenStore.accountId(tokenStore.mode.value)
        if (accountId == null) {
            _state.value = s.copy(error = "Нет активного счёта")
            return
        }
        val priceQuotation = if (s.isMarket) {
            null
        } else {
            val parsed = parsePrice()
            if (parsed == null || parsed <= BigDecimal.ZERO) {
                _state.value = s.copy(error = "Укажите корректную цену для лимитной заявки")
                return
            }
            parsed.toQuotation()
        }

        _state.value = s.copy(isSubmitting = true, error = null, resultMessage = null)
        viewModelScope.launch {
            val result = repository.postOrder(
                accountId = accountId,
                instrumentUid = uid,
                lots = s.lots.toLong(),
                isBuy = s.isBuy,
                isMarket = s.isMarket,
                price = priceQuotation,
            )
            _state.value = when (result) {
                is ApiResult.Success -> {
                    val r = result.data
                    val status = describeStatus(r.executionReportStatus)
                    _state.value.copy(
                        isSubmitting = false,
                        isSuccess = true,
                        resultMessage = "Заявка принята: $status. Исполнено лотов: ${r.lotsExecuted}/${r.lotsRequested}.",
                    )
                }
                is ApiResult.Error ->
                    _state.value.copy(isSubmitting = false, error = result.message)
            }
        }
    }

    private fun describeStatus(status: String): String = when (status) {
        "EXECUTION_REPORT_STATUS_FILL" -> "исполнена"
        "EXECUTION_REPORT_STATUS_NEW" -> "выставлена"
        "EXECUTION_REPORT_STATUS_PARTIALLYFILL" -> "частично исполнена"
        "EXECUTION_REPORT_STATUS_REJECTED" -> "отклонена"
        "EXECUTION_REPORT_STATUS_CANCELLED" -> "отменена"
        else -> status.removePrefix("EXECUTION_REPORT_STATUS_").lowercase()
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(this) }.getOrNull()
