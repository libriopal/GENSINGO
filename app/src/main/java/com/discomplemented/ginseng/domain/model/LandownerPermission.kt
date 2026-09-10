package com.discomplemented.ginseng.domain.model

import java.util.UUID

data class LandownerPermission(
    val id: UUID = UUID.randomUUID(),
    val ownerName: String,
    val imageUri: String, // Path to the scanned slip in scoped storage
    val expiryDate: Long,
    val isVerified: Boolean,
    val locationPolygonGeoJson: String // GeoJSON string for the permitted area
)
