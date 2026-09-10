package com.discomplemented.ginseng.data.repository

import com.discomplemented.ginseng.data.local.database.LandownerPermissionDao
import com.discomplemented.ginseng.data.local.database.LandownerPermissionEntity
import com.discomplemented.ginseng.domain.model.LandownerPermission
import com.discomplemented.ginseng.domain.repository.LandownerPermissionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.util.UUID

class LandownerPermissionRepositoryImplTest {

    @Mock
    private lateinit var landownerPermissionDao: LandownerPermissionDao
    private lateinit var landownerPermissionRepository: LandownerPermissionRepositoryImpl

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        landownerPermissionRepository = LandownerPermissionRepositoryImpl(landownerPermissionDao)
    }

    @Test
    fun `getAllPermissions should return domain models from entity flow`() = runBlocking {
        // Given
        val uuid = UUID.randomUUID()
        val entity = LandownerPermissionEntity(
            id = uuid.toString(),
            ownerName = "John Doe",
            imageUri = "content://path/to/image",
            expiryDate = 1735689600000L, // Dec 31 2024
            isVerified = true,
            locationPolygonGeoJson = "{\"type\": \"Polygon\", \"coordinates\": []}",
            latitude = 0.0, // Required by entity now
            longitude = 0.0
        )
        `when`(landownerPermissionDao.getAll()).thenReturn(kotlinx.coroutines.flow.flowOf(listOf(entity)))

        // When
        val result = landownerPermissionRepository.getAllPermissions().first()

        // Then
        assertEquals(1, result.size)
        assertEquals(uuid, result[0].id)
        assertEquals("John Doe", result[0].ownerName)
    }

    @Test
    fun `savePermission should call dao insert`() = runBlocking {
        // Given
        val permission = LandownerPermission(
            id = UUID.randomUUID(),
            ownerName = "Jane Doe",
            imageUri = "content://path/to/image",
            expiryDate = 1735689600000L,
            isVerified = true,
            locationPolygonGeoJson = "{\"type\": \"Polygon\", \"coordinates\": []}"
        )

        // When
        landownerPermissionRepository.savePermission(permission)

        // Then
        org.mockito.Mockito.verify(landownerPermissionDao).insert(org.mockito.kotlin.any())
    }
}
