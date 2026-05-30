package com.tinvestlite.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinvestlite.data.ApiResult
import com.tinvestlite.data.AppMode
import com.tinvestlite.data.local.TokenStore
import com.tinvestlite.data.repository.InvestRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val mode: AppMode = AppMode.Sandbox,
    val hasRealToken: Boolean = false,
    val isBusy: Boolean = false,
    val isVerifyingReal: Boolean = false,
    val message: String? = null,
    val realError: String? = null,
)

class SettingsViewModel(
    private val repository: InvestRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _local = MutableStateFlow(SettingsUiState())

    /** Merge live mode/real-token flags from the store with local UI state. */
    val state: StateFlow<SettingsUiState> =
        combine(tokenStore.mode, tokenStore.hasRealToken, _local) { mode, hasReal, local ->
            local.copy(mode = mode, hasRealToken = hasReal)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SettingsUiState(mode = tokenStore.mode.value, hasRealToken = tokenStore.hasRealToken.value),
        )

    val sandboxAccountId: String? get() = tokenStore.accountId(AppMode.Sandbox)

    fun switchMode(mode: AppMode) {
        if (mode == AppMode.Real && !tokenStore.hasRealToken.value) {
            _local.value = _local.value.copy(realError = "Сначала добавьте реальный токен (только чтение).")
            return
        }
        tokenStore.setMode(mode)
        _local.value = _local.value.copy(message = null, realError = null)
    }

    /** Validates and saves the real read-only token. */
    fun saveRealToken(token: String) {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) {
            _local.value = _local.value.copy(realError = "Введите токен")
            return
        }
        _local.value = _local.value.copy(isVerifyingReal = true, realError = null)
        // Temporarily save + switch so the validation call uses the new token.
        tokenStore.saveToken(AppMode.Real, trimmed)
        tokenStore.setMode(AppMode.Real)
        viewModelScope.launch {
            when (val result = repository.listAccounts()) {
                is ApiResult.Success -> _local.value = _local.value.copy(
                    isVerifyingReal = false,
                    message = "Реальный счёт подключён (только просмотр).",
                )
                is ApiResult.Error -> {
                    // Roll back to sandbox on failure.
                    tokenStore.clearToken(AppMode.Real)
                    tokenStore.setMode(AppMode.Sandbox)
                    _local.value = _local.value.copy(isVerifyingReal = false, realError = result.message)
                }
            }
        }
    }

    fun removeRealToken() {
        tokenStore.clearToken(AppMode.Real)
        _local.value = _local.value.copy(message = "Реальный токен удалён.", realError = null)
    }

    fun topUp(units: Long) {
        val accountId = tokenStore.accountId(AppMode.Sandbox) ?: return
        _local.value = _local.value.copy(isBusy = true, message = null)
        viewModelScope.launch {
            val result = repository.payIn(accountId, units)
            _local.value = when (result) {
                is ApiResult.Success ->
                    _local.value.copy(isBusy = false, message = "Счёт пополнен на $units ₽")
                is ApiResult.Error ->
                    _local.value.copy(isBusy = false, message = result.message)
            }
        }
    }

    fun logout() {
        tokenStore.clearAll()
    }
}
