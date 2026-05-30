package com.tinvestlite.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinvestlite.data.ApiResult
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
        tokenStore.saveToken(trimmed)

        viewModelScope.launch {
            when (val result = repository.ensureSandboxAccount()) {
                is ApiResult.Success -> {
                    _state.value = TokenUiState()
                    onAuthorized()
                }
                is ApiResult.Error -> {
                    // Token rejected — roll back so the user isn't "half logged in".
                    tokenStore.clear()
                    _state.value = TokenUiState(error = result.message)
                }
            }
        }
    }
}
