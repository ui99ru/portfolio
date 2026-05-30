package com.tinvestlite.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Securely persists the T-Invest API token (and the chosen account) using
 * EncryptedSharedPreferences backed by the Android Keystore.
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

    private val _isAuthorized = MutableStateFlow(peekToken() != null)
    val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

    /** Synchronous read used by the network interceptor. */
    fun peekToken(): String? = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token.trim()).apply()
        _isAuthorized.value = true
    }

    fun clear() {
        prefs.edit().clear().apply()
        _isAuthorized.value = false
    }

    var accountId: String?
        get() = prefs.getString(KEY_ACCOUNT_ID, null)
        set(value) {
            prefs.edit().putString(KEY_ACCOUNT_ID, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "tinvest_secure_prefs"
        const val KEY_TOKEN = "api_token"
        const val KEY_ACCOUNT_ID = "account_id"
    }
}
