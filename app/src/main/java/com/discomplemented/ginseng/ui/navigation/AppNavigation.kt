package com.discomplemented.ginseng.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.discomplemented.ginseng.ui.screens.log.LogScreen
import com.discomplemented.ginseng.ui.screens.map.MapScreen
import com.discomplemented.ginseng.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Map : Screen("map")
    object Log : Screen("log")
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Map.route
    ) {
        composable(Screen.Map.route) {
            MapScreen()
        }
        composable(Screen.Log.route) {
            LogScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}

@Composable
fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = name)
    }
}
