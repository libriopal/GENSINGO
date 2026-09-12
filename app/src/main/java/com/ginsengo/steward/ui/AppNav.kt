package com.ginsengo.steward.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ginsengo.steward.ui.screens.PositionScreen
import com.ginsengo.steward.ui.screens.GuideScreen
import com.ginsengo.steward.ui.screens.HabitatReadingScreen
import com.ginsengo.steward.ui.screens.HomeScreen
import com.ginsengo.steward.ui.screens.LogPatchScreen
import com.ginsengo.steward.ui.screens.PatchDetailScreen
import com.ginsengo.steward.ui.screens.PatchesScreen
import com.ginsengo.steward.ui.screens.SettingsScreen
import com.ginsengo.steward.ui.screens.VerifyScreen

object Routes {
    const val HOME = "home"
    const val HABITAT = "habitat"
    const val VERIFY = "verify"
    const val LOG = "log"
    const val LOG_WITH_SCORE = "log?score={score}"
    const val PATCHES = "patches"
    const val PATCH_DETAIL = "patch/{id}"
    const val GUIDE = "guide"
    const val SETTINGS = "settings"
    const val POSITION = "position"

    fun patchDetail(id: String) = "patch/$id"
    fun logWithScore(score: Double?) = if (score == null) "log?score=" else "log?score=$score"
}

@Composable
fun AppNav(vm: FieldViewModel, onRequestLocationPermission: () -> Unit) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                vm = vm,
                onRequestLocationPermission = onRequestLocationPermission,
                onNavigate = { nav.navigate(it) },
            )
        }

        composable(Routes.HABITAT) {
            HabitatReadingScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onLogPatch = { score ->
                    nav.navigate(Routes.logWithScore(score))
                },
            )
        }

        composable(Routes.VERIFY) {
            VerifyScreen(vm = vm, onBack = { nav.popBackStack() })
        }

        composable(
            route = Routes.LOG_WITH_SCORE,
            arguments = listOf(navArgument("score") {
                type = NavType.StringType; defaultValue = ""; nullable = true
            }),
        ) { entry ->
            LogPatchScreen(
                vm = vm,
                initialScore = entry.arguments?.getString("score")?.toDoubleOrNull(),
                onBack = { nav.popBackStack() },
                onSaved = {
                    nav.popBackStack(Routes.HOME, inclusive = false)
                    nav.navigate(Routes.PATCHES)
                },
            )
        }

        composable(Routes.PATCHES) {
            PatchesScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpen = { nav.navigate(Routes.patchDetail(it)) },
            )
        }

        composable(
            route = Routes.PATCH_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            PatchDetailScreen(
                vm = vm,
                patchId = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }

        composable(Routes.GUIDE) {
            GuideScreen(vm = vm, onBack = { nav.popBackStack() })
        }

        composable(Routes.POSITION) {
            PositionScreen(vm = vm, onBack = { nav.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
