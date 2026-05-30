package com.tinvestlite.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tinvestlite.di.AppContainer
import com.tinvestlite.ui.home.HomeScreen
import com.tinvestlite.ui.instrument.InstrumentScreen
import com.tinvestlite.ui.onboarding.TokenScreen
import com.tinvestlite.ui.trade.TradeScreen

@Composable
fun AppNavHost(
    container: AppContainer,
    isAuthorized: Boolean,
) {
    val navController = rememberNavController()
    val start = if (isAuthorized) Routes.HOME else Routes.TOKEN

    // React to logout from anywhere: jump back to the token screen.
    LaunchedEffect(isAuthorized) {
        if (!isAuthorized) {
            navController.navigate(Routes.TOKEN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.TOKEN) {
            TokenScreen(
                container = container,
                onAuthorized = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.TOKEN) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                container = container,
                onOpenInstrument = { uid -> navController.navigate(Routes.instrument(uid)) },
            )
        }

        composable(
            route = "${Routes.INSTRUMENT}/{uid}",
            arguments = listOf(navArgument("uid") { type = NavType.StringType }),
        ) { entry ->
            val uid = entry.arguments?.getString("uid").orEmpty()
            InstrumentScreen(
                container = container,
                uid = uid,
                onBack = { navController.popBackStack() },
                onTrade = { buy -> navController.navigate(Routes.trade(uid, buy)) },
            )
        }

        composable(
            route = "${Routes.TRADE}/{uid}/{buy}",
            arguments = listOf(
                navArgument("uid") { type = NavType.StringType },
                navArgument("buy") { type = NavType.BoolType },
            ),
        ) { entry ->
            val uid = entry.arguments?.getString("uid").orEmpty()
            val buy = entry.arguments?.getBoolean("buy") ?: true
            TradeScreen(
                container = container,
                uid = uid,
                startAsBuy = buy,
                onDone = { navController.popBackStack() },
            )
        }
    }
}
