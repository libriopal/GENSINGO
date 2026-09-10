package com.discomplemented.ginseng.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.discomplemented.ginseng.ui.viewmodel.MapViewModel

/**
 * Main map screen with MapLibre integration.
 */
@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel()
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // MapLibre composable will be integrated here
        Text("Map Screen - MapLibre Integration Ready")
    }
}
