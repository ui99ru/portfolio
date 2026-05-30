package com.tinvestlite.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.tinvestlite.di.AppContainer
import com.tinvestlite.ui.market.MarketScreen
import com.tinvestlite.ui.navigation.HomeTab
import com.tinvestlite.ui.operations.OperationsScreen
import com.tinvestlite.ui.portfolio.PortfolioScreen
import com.tinvestlite.ui.settings.SettingsScreen

@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenInstrument: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.Portfolio) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(iconFor(entry), contentDescription = entry.label) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                HomeTab.Portfolio -> PortfolioScreen(container, onOpenInstrument)
                HomeTab.Market -> MarketScreen(container, onOpenInstrument)
                HomeTab.Operations -> OperationsScreen(container)
                HomeTab.Settings -> SettingsScreen(container)
            }
        }
    }
}

private fun iconFor(tab: HomeTab): ImageVector = when (tab) {
    HomeTab.Portfolio -> Icons.Filled.AccountBalanceWallet
    HomeTab.Market -> Icons.Filled.Search
    HomeTab.Operations -> Icons.AutoMirrored.Filled.ReceiptLong
    HomeTab.Settings -> Icons.Filled.Settings
}
