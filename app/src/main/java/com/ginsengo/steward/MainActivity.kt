package com.ginsengo.steward

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.ginsengo.steward.ui.FieldViewModel
import com.ginsengo.steward.ui.GensingoTheme
import com.ginsengo.steward.ui.MainScreen

/**
 * The one screen.
 *
 * The previous build removed the UI entirely ("headless"), so it requested location and
 * then showed nothing. This restores a user-facing app with the minimum surface: a map,
 * four buttons, three sheets.
 */
class MainActivity : ComponentActivity() {

    private val vm: FieldViewModel by viewModels()

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        vm.onPermissionResult(
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // targetSdk 36 enforces edge-to-edge; the screen pads itself with system-bar insets.
        // The app is always dark, so the bars are too. The default follows the PHONE's theme:
        // on a light-mode phone it drew a light grey navigation bar under the dark map (Phase 7).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { GensingoTheme { MainScreen(vm) } }
        if (!vm.container.location.hasPermission()) {
            permissions.launch(
                buildList {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    // The track-recording notification carries its Stop button.
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                }.toTypedArray()
            )
        }
    }

    override fun onStart() {
        super.onStart()
        vm.onVisible()
    }

    override fun onStop() {
        // Live GPS stops with the screen. A recording track carries on in TrackService.
        vm.onHidden()
        super.onStop()
    }
}
