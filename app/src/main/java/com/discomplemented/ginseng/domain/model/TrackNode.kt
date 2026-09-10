package com.discomplemented.ginseng.domain.model

import java.util.UUID

data class TrackNode(
    val id: UUID = UUID.randomUUID(),
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Float,
    val accuracy: Float
)
