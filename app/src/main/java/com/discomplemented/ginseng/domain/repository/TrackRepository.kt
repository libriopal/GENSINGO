package com.discomplemented.ginseng.domain.repository

import com.discomplemented.ginseng.domain.model.TrackNode
import com.discomplemented.ginseng.domain.model.GinsengPatch
import kotlinx.coroutines.flow.Flow

interface TrackRepository {
    fun getTrackHistory(): Flow<List<TrackNode>>
    suspend fun saveTrackNode(node: TrackNode)
    suspend fun saveTrackNodes(nodes: List<TrackNode>)
    suspend fun getTrackNodesInBounds(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<TrackNode>
}
