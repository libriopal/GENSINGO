package com.discomplemented.ginseng.data.repository

import com.discomplemented.ginseng.data.local.database.TrackNodeDao
import com.discomplemented.ginseng.data.local.database.TrackNodeEntity
import com.discomplemented.ginseng.domain.model.TrackNode
import com.discomplemented.ginseng.domain.repository.TrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.sqlite.db.SimpleSQLiteQuery
import java.util.UUID

class TrackRepositoryImpl(
    private val trackNodeDao: TrackNodeDao
) : TrackRepository {

    override fun getTrackHistory(): Flow<List<TrackNode>> {
        return trackNodeDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveTrackNode(node: TrackNode) {
        trackNodeDao.insert(node.toEntity())
    }

    override suspend fun saveTrackNodes(nodes: List<TrackNode>) {
        trackNodeDao.insertAll(nodes.map { it.toEntity() })
    }

    override suspend fun getTrackNodesInBounds(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<TrackNode> {
        val query = SimpleSQLiteQuery(
            "SELECT t.* FROM track_nodes t JOIN track_nodes_rtree r ON t.rowId = r.id WHERE r.minLat <= ? AND r.maxLat >= ? AND r.minLon <= ? AND r.maxLon >= ?",
            arrayOf(maxLat, minLat, maxLon, minLon)
        )
        return trackNodeDao.getNodesInBoundingBox(query).map { it.toDomain() }
    }

    private fun TrackNodeEntity.toDomain(): TrackNode {
        return TrackNode(
            id = UUID.fromString(uuid),
            timestamp = timestamp,
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            accuracy = accuracy
        )
    }

    private fun TrackNode.toEntity(): TrackNodeEntity {
        return TrackNodeEntity(
            uuid = id.toString(),
            timestamp = timestamp,
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            accuracy = accuracy
        )
    }
}
