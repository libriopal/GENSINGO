package com.discomplemented.ginseng.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.discomplemented.ginseng.ui.screens.MapScreen
import com.discomplemented.ginseng.ui.screens.SettingsScreen

/**
 * Main navigation host for the app.
 */
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = "map"
    ) {
        composable("map") {
            MapScreen()
        }
        composable("settings") {
            SettingsScreen()
        }
    }
}
