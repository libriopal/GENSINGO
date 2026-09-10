package com.discomplemented.ginseng.domain.model

import java.util.UUID

data class GinsengPatch(
    val id: UUID = UUID.randomUUID(),
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val confidence: Float,
    val metadata: Map<String, String> // Store as JSON string in DB
)
