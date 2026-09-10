package com.discomplemented.ginseng.ui.screens.log

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.discomplemented.ginseng.domain.model.GinsengPatch
import com.discomplemented.ginseng.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    private val ginsengPatchRepository: com.discomplemented.ginseng.domain.repository.GinsengPatchRepository
) : androidx.lifecycle.ViewModel() {

    val patches: StateFlow<List<GinsengPatch>> = ginsengPatchRepository.getAllPatches()
        .stateIn(
            scope = androidx.lifecycle.viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}

@Composable
fun LogScreen(
    viewModel: LogViewModel = hiltViewModel()
) {
    val patches by viewModel.patches.collectAsState()
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Scouting Log",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

        if (patches.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No patches recorded yet.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(patches) { patch ->
                    PatchItem(patch, dateFormat)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun PatchItem(patch: GinsengPatch, dateFormat: SimpleDateFormat) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Verified Find",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Date: ${dateFormat.format(Date(patch.timestamp))}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Coords: ${patch.latitude}, ${patch.longitude}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Confidence: ${(patch.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall
            )

            if (patch.metadata.isNotEmpty()) {
                Text(
                    text = "Metadata: ${patch.metadata}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
