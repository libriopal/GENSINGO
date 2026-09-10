package com.discomplemented.ginseng.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents landowner permission metadata and encrypted documents.
 * Stores encrypted file references for sensitive permission data.
 */
@Entity(tableName = "landowner_permissions")
data class LandownerPermissionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    val landownerName: String,
    
    val contactPhone: String,
    
    val propertyBounds: String, // GeoJSON polygon (encrypted at rest)
    
    val permissionStartDate: Long,
    
    val permissionEndDate: Long,
    
    val encryptedDocumentPath: String?,
    
    val timestamp: Long = System.currentTimeMillis()
)
