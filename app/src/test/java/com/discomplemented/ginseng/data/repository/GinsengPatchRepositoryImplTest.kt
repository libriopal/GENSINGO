package com.discomplemented.ginseng.data.repository

import com.discomplemented.ginseng.data.local.database.GinsengPatchDao
import com.discomplemented.ginseng.data.local.database.GinsengPatchEntity
import com.discomplemented.ginseng.domain.model.GinsengPatch
import com.discomplemented.ginseng.domain.repository.GinsengPatchRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.util.UUID

class GinsengPatchRepositoryImplTest {

    @Mock
    private lateinit var ginsengPatchDao: GinsengPatchDao
    private lateinit var gson: Gson
    private lateinit var ginsengPatchRepository: GinsengPatchRepositoryImpl

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        gson = Gson()
        ginsengPatchRepository = GinsengPatchRepositoryImpl(ginsengPatchDao, gson)
    }

    @Test
    fun `getAllPatches should return domain models from entity flow`() = runBlocking {
        // Given
        val uuid = UUID.randomUUID()
        val entity = GinsengPatchEntity(
            id = uuid.toString(),
            latitude = 35.0,
            longitude = -82.0,
            timestamp = 1000L,
            confidence = 0.9f,
            metadata = "{\"canopy\": \"high\"}"
        )
        `when`(ginsengPatchDao.getAll()).thenReturn(kotlinx.coroutines.flow.flowOf(listOf(entity)))

        // When
        val result = ginsengPatchRepository.getAllPatches().first()

        // Then
        assertEquals(1, result.size)
        assertEquals(uuid, result[0].id)
        assertEquals(0.9f, result[0].confidence, 0.0001f)
    }

    @Test
    fun `savePatch should call dao insert`() = runBlocking {
        // Given
        val patch = GinsengPatch(
            id = UUID.randomUUID(),
            latitude = 35.0,
            longitude = -82.0,
            timestamp = 1000L,
            confidence = 0.9f,
            metadata = mapOf("canopy" to "high")
        )

        // When
        ginsengPatchRepository.savePatch(patch)

        // Then
        org.mockito.Mockito.verify(ginsengPatchDao).insert(org.mockito.kotlin.any())
    }
}
