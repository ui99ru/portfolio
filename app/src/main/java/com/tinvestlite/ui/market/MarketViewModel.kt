package com.tinvestlite.ui.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinvestlite.data.ApiResult
import com.tinvestlite.data.remote.dto.InstrumentShort
import com.tinvestlite.data.repository.InvestRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MarketUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<InstrumentShort> = emptyList(),
    val error: String? = null,
)

class MarketViewModel(
    private val repository: InvestRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MarketUiState())
    val state: StateFlow<MarketUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.value = _state.value.copy(results = emptyList(), isLoading = false, error = null)
            return
        }
        searchJob = viewModelScope.launch {
            delay(350) // debounce
            _state.value = _state.value.copy(isLoading = true, error = null)
            when (val result = repository.findInstruments(query.trim())) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    isLoading = false,
                    results = result.data.distinctBy { it.uid }.take(50),
                )
                is ApiResult.Error -> _state.value = _state.value.copy(
                    isLoading = false,
                    error = result.message,
                )
            }
        }
    }
}
