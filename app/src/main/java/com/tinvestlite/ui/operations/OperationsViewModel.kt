package com.tinvestlite.ui.operations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinvestlite.data.ApiResult
import com.tinvestlite.data.local.TokenStore
import com.tinvestlite.data.remote.dto.Operation
import com.tinvestlite.data.repository.InvestRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

sealed interface OperationsUiState {
    data object Loading : OperationsUiState
    data class Error(val message: String) : OperationsUiState
    data class Data(val operations: List<Operation>) : OperationsUiState
}

class OperationsViewModel(
    private val repository: InvestRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow<OperationsUiState>(OperationsUiState.Loading)
    val state: StateFlow<OperationsUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = OperationsUiState.Loading
        viewModelScope.launch {
            val accountId = when (val acc = repository.ensureAccount()) {
                is ApiResult.Success -> acc.data
                is ApiResult.Error -> {
                    _state.value = OperationsUiState.Error(acc.message)
                    return@launch
                }
            }

            val now = Instant.now()
            val from = now.minus(90, ChronoUnit.DAYS)
            when (val result = repository.getOperations(accountId, from.toString(), now.toString())) {
                is ApiResult.Success -> _state.value = OperationsUiState.Data(result.data)
                is ApiResult.Error -> _state.value = OperationsUiState.Error(result.message)
            }
        }
    }
}
