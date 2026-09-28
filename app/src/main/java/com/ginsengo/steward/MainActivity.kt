package com.ginsengo.steward

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.ginsengo.steward.ui.FieldViewModel

/**
 * Headless entry activity for backend services, governance engines,
 * location feeds, and telemetry persistence.
 *
 * Direct user-facing visual design front-end code removed per user specification.
 */
class MainActivity : ComponentActivity() {

    private val vm: FieldViewModel by viewModels()

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        vm.onPermissionResult(
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationPermission.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }
}
