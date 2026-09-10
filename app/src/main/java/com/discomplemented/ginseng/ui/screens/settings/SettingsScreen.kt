package com.discomplemented.ginseng.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(text = "Application Settings", style = MaterialTheme.typography.titleMedium)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(text = "Version: 1.0.0-alpha", style = MaterialTheme.typography.bodyMedium)
        Text(text = "Compliance Mode: NC Regulations", style = MaterialTheme.typography.bodyMedium)
        Text(text = "Offline Mode: Enabled", style = MaterialTheme.typography.bodyMedium)

        // Placeholders for future settings
        // - Notification settings
        // - Map layer toggles
        // - AI Model management
        // - Data export/import
    }
}
