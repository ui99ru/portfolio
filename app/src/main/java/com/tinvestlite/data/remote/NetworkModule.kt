package com.tinvestlite.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.tinvestlite.data.local.TokenStore
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object NetworkModule {

    private const val BASE_URL = "https://invest-public-api.tinkoff.ru/rest/"

    val json: Json = Json {
        ignoreUnknownKeys = true
        // The proto-JSON gateway serializes int64 fields (units, quantity, ...)
        // as quoted strings; lenient parsing accepts both quoted and bare numbers.
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun create(tokenStore: TokenStore, enableLogging: Boolean): TInvestApi {
        val logging = HttpLoggingInterceptor().apply {
            level = if (enableLogging) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            // Never leak the Authorization header into logs.
            redactHeader("Authorization")
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(TInvestApi::class.java)
    }
}
