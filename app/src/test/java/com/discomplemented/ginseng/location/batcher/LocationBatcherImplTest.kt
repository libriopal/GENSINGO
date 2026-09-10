package com.discomplemented.ginseng.location.batcher

import com.discomplemented.ginseng.domain.model.TrackNode
import com.discomplemented.ginseng.domain.repository.TrackRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class LocationBatcherImplTest {

    private lateinit var trackRepository: TrackRepository
    private lateinit var locationBatcher: LocationBatcherImpl

    @Before
    fun setup() {
        trackRepository = mock(TrackRepository::class.java)
        locationBatcher = LocationBatcherImpl(trackRepository)
    }

    @Test
    fun `collectAndBatch should flush when buffer reaches batch size`() = runTest {
        // Given
        val nodes = (1..50).map {
            TrackNode(
                timestamp = System.currentTimeMillis(),
                latitude = 35.0 + it * 0.001,
                longitude = -82.0 + it * 0.001,
                altitude = 500f,
                accuracy = 5f
            )
        }
        val nodeFlow = flowOf(*nodes.toTypedArray())

        // When
        locationBatcher.collectAndBatch(nodeFlow)

        // Then
        verify(trackRepository, times(1)).saveTrackNodes(nodes)
    }

    @Test
    fun `flush should save current buffer even if below batch size`() = runTest {
        // Given
        val nodes = (1..10).map {
            TrackNode(
                timestamp = System.currentTimeMillis(),
                latitude = 35.0 + it * 0.001,
                longitude = -82.0 + it * 0.001,
                altitude = 500f,
                accuracy = 5f
            )
        }

        // We need to bypass the flow collection to manually populate the buffer
        // Since the buffer is private, we simulate this by calling collectAndBatch with a small flow
        // and then calling flush.

        val nodeFlow = flowOf(*nodes.toTypedArray())

        // When
        // We launch collection in a separate job so we can call flush manually
        val job = kotlinx.coroutines.launch {
            locationBatcher.collectAndBatch(nodeFlow)
        }

        // Small delay to allow the flow to be processed into the buffer
        kotlinx.coroutines.delay(100)
        locationBatcher.flush()
        job.cancel()

        // Then
        verify(trackRepository, atLeastOnce()).saveTrackNodes(anyList())
    }
}
