package com.tinvestlite

import android.app.Application
import com.tinvestlite.di.AppContainer

class TInvestApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
