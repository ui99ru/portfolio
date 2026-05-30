package com.tinvestlite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinvestlite.ui.navigation.AppNavHost
import com.tinvestlite.ui.theme.TInvestLiteTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as TInvestApp).container

        setContent {
            TInvestLiteTheme {
                val authorized by container.tokenStore.isAuthorized.collectAsStateWithLifecycle()
                AppNavHost(
                    container = container,
                    isAuthorized = authorized,
                )
            }
        }
    }
}
