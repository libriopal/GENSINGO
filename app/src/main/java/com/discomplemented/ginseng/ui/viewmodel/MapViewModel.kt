package com.discomplemented.ginseng.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.discomplemented.ginseng.domain.repository.TrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for Map screen.
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val trackRepository: TrackRepository
) : ViewModel() {
    // State and business logic to be implemented
}
