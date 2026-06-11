package com.tinvestlite.ui.instrument

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinvestlite.data.ApiResult
import com.tinvestlite.data.getOrNull
import com.tinvestlite.data.remote.dto.Candle
import com.tinvestlite.data.remote.dto.InstrumentDetail
import com.tinvestlite.data.repository.InvestRepository
import com.tinvestlite.util.toBigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

enum class Timeframe(val label: String, val interval: String, val daysBack: Long) {
    Day("1Д", "CANDLE_INTERVAL_15_MIN", 1),
    Week("1Н", "CANDLE_INTERVAL_HOUR", 7),
    Month("1М", "CANDLE_INTERVAL_DAY", 30),
    HalfYear("6М", "CANDLE_INTERVAL_DAY", 182),
    Year("1Г", "CANDLE_INTERVAL_DAY", 365),
}

data class InstrumentUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val detail: InstrumentDetail? = null,
    val lastPrice: BigDecimal? = null,
    val bestBid: BigDecimal? = null,
    val bestAsk: BigDecimal? = null,
    val timeframe: Timeframe = Timeframe.Month,
    val candles: List<Candle> = emptyList(),
    val candlesLoading: Boolean = false,
)

class InstrumentViewModel(
    private val repository: InvestRepository,
    private val uid: String,
) : ViewModel() {

    private val _state = MutableStateFlow(InstrumentUiState())
    val state: StateFlow<InstrumentUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            when (val detail = repository.getInstrument(uid)) {
                is ApiResult.Success -> {
                    _state.value = _state.value.copy(isLoading = false, detail = detail.data)
                    loadQuotes()
                    loadCandles(_state.value.timeframe)
                }
                is ApiResult.Error ->
                    _state.value = _state.value.copy(isLoading = false, error = detail.message)
            }
        }
    }

    private fun loadQuotes() {
        viewModelScope.launch {
            val book = repository.getOrderBook(uid).getOrNull()
            val last = repository.getLastPrice(uid).getOrNull()
            _state.value = _state.value.copy(
                lastPrice = last?.toBigDecimal(),
                bestBid = book?.bids?.firstOrNull()?.price?.toBigDecimal(),
                bestAsk = book?.asks?.firstOrNull()?.price?.toBigDecimal(),
            )
        }
    }

    fun selectTimeframe(timeframe: Timeframe) {
        if (timeframe == _state.value.timeframe && _state.value.candles.isNotEmpty()) return
        _state.value = _state.value.copy(timeframe = timeframe)
        loadCandles(timeframe)
    }

    private fun loadCandles(timeframe: Timeframe) {
        _state.value = _state.value.copy(candlesLoading = true)
        viewModelScope.launch {
            val now = Instant.now()
            val from = now.minus(timeframe.daysBack, ChronoUnit.DAYS)
            val result = repository.getCandles(
                uid = uid,
                from = from.toString(),
                to = now.toString(),
                interval = timeframe.interval,
            )
            _state.value = when (result) {
                is ApiResult.Success ->
                    _state.value.copy(candlesLoading = false, candles = result.data)
                is ApiResult.Error ->
                    _state.value.copy(candlesLoading = false, candles = emptyList())
            }
        }
    }
}
