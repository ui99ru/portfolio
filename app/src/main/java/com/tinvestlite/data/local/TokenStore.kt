package com.tinvestlite.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tinvestlite.data.AppMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Securely persists the T-Invest API tokens using EncryptedSharedPreferences
 * backed by the Android Keystore.
 *
 * Two independent tokens are kept:
 *  - a sandbox token (full access, virtual money), and
 *  - a real token (expected to be read-only).
 *
 * The active [mode] selects which token the network layer uses. The app is
 * considered authorized as long as at least one token is present.
 */
class TokenStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _mode = MutableStateFlow(readMode())
    val mode: StateFlow<AppMode> = _mode.asStateFlow()

    private val _isAuthorized = MutableStateFlow(hasAnyToken())
    val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

    private val _hasReal = MutableStateFlow(tokenFor(AppMode.Real) != null)
    val hasRealToken: StateFlow<Boolean> = _hasReal.asStateFlow()

    // ---- Tokens ----

    private fun tokenKey(mode: AppMode) = when (mode) {
        AppMode.Sandbox -> KEY_TOKEN_SANDBOX
        AppMode.Real -> KEY_TOKEN_REAL
    }

    fun tokenFor(mode: AppMode): String? =
        prefs.getString(tokenKey(mode), null)?.takeIf { it.isNotBlank() }

    /** Token for the network interceptor — the one matching the active mode. */
    fun peekToken(): String? = tokenFor(_mode.value)

    fun saveToken(mode: AppMode, token: String) {
        prefs.edit().putString(tokenKey(mode), token.trim()).apply()
        if (mode == AppMode.Real) _hasReal.value = true
        _isAuthorized.value = hasAnyToken()
    }

    fun clearToken(mode: AppMode) {
        prefs.edit().remove(tokenKey(mode)).remove(accountKey(mode)).apply()
        if (mode == AppMode.Real) {
            _hasReal.value = false
            // Never leave the app stuck in a mode with no token.
            if (_mode.value == AppMode.Real) setMode(AppMode.Sandbox)
        }
        _isAuthorized.value = hasAnyToken()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        _hasReal.value = false
        _mode.value = AppMode.Sandbox
        _isAuthorized.value = false
    }

    private fun hasAnyToken(): Boolean =
        tokenFor(AppMode.Sandbox) != null || tokenFor(AppMode.Real) != null

    // ---- Mode ----

    private fun readMode(): AppMode =
        if (prefs.getString(KEY_MODE, null) == MODE_REAL) AppMode.Real else AppMode.Sandbox

    fun setMode(mode: AppMode) {
        prefs.edit().putString(KEY_MODE, if (mode == AppMode.Real) MODE_REAL else MODE_SANDBOX).apply()
        _mode.value = mode
    }

    // ---- Selected account (per mode) ----

    private fun accountKey(mode: AppMode) = when (mode) {
        AppMode.Sandbox -> KEY_ACCOUNT_SANDBOX
        AppMode.Real -> KEY_ACCOUNT_REAL
    }

    fun accountId(mode: AppMode): String? = prefs.getString(accountKey(mode), null)

    fun setAccountId(mode: AppMode, value: String?) {
        prefs.edit().putString(accountKey(mode), value).apply()
    }

    private companion object {
        const val PREFS_NAME = "tinvest_secure_prefs"
        const val KEY_TOKEN_SANDBOX = "api_token"        // kept for backward compat
        const val KEY_TOKEN_REAL = "api_token_real"
        const val KEY_ACCOUNT_SANDBOX = "account_id"
        const val KEY_ACCOUNT_REAL = "account_id_real"
        const val KEY_MODE = "app_mode"
        const val MODE_SANDBOX = "sandbox"
        const val MODE_REAL = "real"
    }
}
