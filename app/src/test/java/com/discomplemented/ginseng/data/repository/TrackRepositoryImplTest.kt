package com.discomplemented.ginseng.data.repository

import com.discomplemented.ginseng.data.local.database.TrackNodeDao
import com.discomplemented.ginseng.data.local.database.TrackNodeEntity
import com.discomplemented.ginseng.domain.model.TrackNode
import com.discomplemented.ginseng.domain.repository.TrackRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.util.UUID

class TrackRepositoryImplTest {

    @Mock
    private lateinit var trackNodeDao: TrackNodeDao

    private lateinit var trackRepository: TrackRepositoryImpl

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        trackRepository = TrackRepositoryImpl(trackNodeDao)
    }

    @Test
    fun `getTrackHistory should return domain models from entity flow`() = runBlocking {
        // Given
        val uuid = UUID.randomUUID()
        val entity = TrackNodeEntity(
            id = uuid.toString(),
            timestamp = 1000L,
            latitude = 35.0,
            longitude = -82.0,
            altitude = 500f,
            accuracy = 5f
        )
        `when`(trackNodeDao.getAll()).thenReturn(flowOf(listOf(entity)))

        // When
        val result = trackRepository.getTrackHistory().first()

        // Then
        assertEquals(1, result.size)
        assertEquals(uuid, result[0].id)
        assertEquals(35.0, result[0].latitude, 0.0001)
    }

    @Test
    fun `saveTrackNode should call dao insert`() = runBlocking {
        // Given
        val node = TrackNode(
            id = UUID.randomUUID(),
            timestamp = 1000L,
            latitude = 35.0,
            longitude = -82.0,
            altitude = 500f,
            accuracy = 5f
        )

        // When
        trackRepository.saveTrackNode(node)

        // Then
        org.mockito.Mockito.verify(trackNodeDao).insert(org.mockito.kotlin.any())
    }
}
