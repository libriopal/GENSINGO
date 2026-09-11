package com.ginsengo.steward

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ginsengo.steward.ui.AppNav
import com.ginsengo.steward.ui.FieldViewModel
import com.ginsengo.steward.ui.theme.Gen
import com.ginsengo.steward.ui.theme.GensingoTheme

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
        enableEdgeToEdge()
        setContent {
            GensingoTheme {
                Surface(Modifier.fillMaxSize(), color = Gen.Base) {
                    AppNav(
                        vm = vm,
                        onRequestLocationPermission = {
                            locationPermission.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            )
                        },
                    )
                }
            }
        }
    }
}
