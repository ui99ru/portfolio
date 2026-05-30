package com.tinvestlite.ui.navigation

object Routes {
    const val TOKEN = "token"
    const val HOME = "home"
    const val INSTRUMENT = "instrument"
    const val TRADE = "trade"

    fun instrument(uid: String) = "$INSTRUMENT/$uid"
    fun trade(uid: String, buy: Boolean) = "$TRADE/$uid/$buy"
}

/** Bottom-navigation tabs inside the authorized [HomeScreen]. */
enum class HomeTab(val route: String, val label: String) {
    Portfolio("tab_portfolio", "Портфель"),
    Market("tab_market", "Рынок"),
    Operations("tab_operations", "Операции"),
    Settings("tab_settings", "Настройки"),
}
