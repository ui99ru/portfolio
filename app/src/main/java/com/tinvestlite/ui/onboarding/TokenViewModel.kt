package com.tinvestlite.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinvestlite.data.ApiResult
import com.tinvestlite.data.AppMode
import com.tinvestlite.data.local.TokenStore
import com.tinvestlite.data.repository.InvestRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TokenUiState(
    val isChecking: Boolean = false,
    val error: String? = null,
)

class TokenViewModel(
    private val repository: InvestRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(TokenUiState())
    val state: StateFlow<TokenUiState> = _state.asStateFlow()

    fun submit(token: String, onAuthorized: () -> Unit) {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) {
            _state.value = TokenUiState(error = "Введите токен")
            return
        }
        _state.value = TokenUiState(isChecking = true)
        // Onboarding always sets up the sandbox token; the real read-only token
        // is added later from Settings.
        tokenStore.setMode(AppMode.Sandbox)
        tokenStore.saveToken(AppMode.Sandbox, trimmed)

        viewModelScope.launch {
            when (val result = repository.ensureAccount()) {
                is ApiResult.Success -> {
                    _state.value = TokenUiState()
                    onAuthorized()
                }
                is ApiResult.Error -> {
                    // Token rejected — roll back so the user isn't "half logged in".
                    tokenStore.clearToken(AppMode.Sandbox)
                    _state.value = TokenUiState(error = result.message)
                }
            }
        }
    }
}
