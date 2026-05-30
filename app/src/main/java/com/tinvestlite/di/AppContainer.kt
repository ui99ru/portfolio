package com.tinvestlite.di

import android.content.Context
import com.tinvestlite.BuildConfig
import com.tinvestlite.data.local.TokenStore
import com.tinvestlite.data.repository.InvestRepository

/** Minimal manual DI graph — created once in [com.tinvestlite.TInvestApp]. */
class AppContainer(context: Context) {
    val tokenStore: TokenStore = TokenStore(context.applicationContext)
    val repository: InvestRepository = InvestRepository(tokenStore, enableLogging = BuildConfig.DEBUG)
}
