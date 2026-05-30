package com.tinvestlite.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinvestlite.data.ApiResult
import com.tinvestlite.data.local.TokenStore
import com.tinvestlite.data.repository.InvestRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val accountId: String? = null,
    val isBusy: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(
    private val repository: InvestRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState(accountId = tokenStore.accountId))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun topUp(units: Long) {
        val accountId = tokenStore.accountId ?: return
        _state.value = _state.value.copy(isBusy = true, message = null)
        viewModelScope.launch {
            val result = repository.payIn(accountId, units)
            _state.value = when (result) {
                is ApiResult.Success ->
                    _state.value.copy(isBusy = false, message = "Счёт пополнен на $units ₽")
                is ApiResult.Error ->
                    _state.value.copy(isBusy = false, message = result.message)
            }
        }
    }

    fun logout() {
        tokenStore.clear()
    }
}
