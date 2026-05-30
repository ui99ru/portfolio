package com.tinvestlite.data.remote

import com.tinvestlite.data.local.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the Bearer token (read fresh on each call so re-login takes effect
 * immediately) and the headers the REST gateway expects.
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("x-app-name", "tinvestlite.android")

        tokenStore.peekToken()?.let { token ->
            builder.header("Authorization", "Bearer $token")
        }
        return chain.proceed(builder.build())
    }
}
