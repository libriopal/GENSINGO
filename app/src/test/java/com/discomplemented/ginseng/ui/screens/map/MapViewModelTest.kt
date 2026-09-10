package com.discomplemented.ginseng.ui.screens.map

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.discomplemented.ginseng.domain.model.GinsengPatch
import com.discomplemented.ginseng.domain.model.TrackNode
import com.discomplemented.ginseng.domain.repository.GinsengPatchRepository
import com.discomplemented.ginseng.domain.repository.TrackRepository
import com.discomplemented.ginseng.domain.usecase.GetHabitatSuitabilityUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var trackRepository: TrackRepository

    @Mock
    private lateinit var ginsengPatchRepository: GinsengPatchRepository

    @Mock
    private lateinit var getHabitatSuitabilityUseCase: GetHabitatSuitabilityUseCase

    private lateinit var viewModel: MapViewModel

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        viewModel = MapViewModel(trackRepository, ginsengPatchRepository, getHabitatSuitabilityUseCase)
    }

    @Test
    fun `trackHistory should emit values from repository`() = runTest {
        // Given
        val node = TrackNode(UUID.randomUUID(), 1000L, 35.0, -82.0, 500f, 5f)
        `when`(trackRepository.getTrackHistory()).thenReturn(flowOf(listOf(node)))

        // When
        val result = viewModel.trackHistory.value

        // Then
        assertEquals(1, result.size)
        assertEquals(node.id, result[0].id)
    }

    @Test
    fun `ginsengPatches should emit values from repository`() = runTest {
        // Given
        val patch = GinsengPatch(UUID.randomUUID(), 35.0, -82.0, 1000L, 0.9f, mapOf())
        `when`(ginsengPatchRepository.getAllPatches()).thenReturn(flowOf(listOf(patch)))

        // When
        val result = viewModel.ginsengPatches.value

        // Then
        assertEquals(1, result.size)
        assertEquals(patch.id, result[0].id)
    }
}
