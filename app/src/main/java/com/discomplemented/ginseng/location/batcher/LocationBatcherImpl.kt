package com.discomplemented.ginseng.location.batcher

import com.discomplemented.ginseng.data.local.database.entity.TrackNodeEntity
import com.discomplemented.ginseng.domain.repository.TrackRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of LocationBatcher.
 * Persist-first strategy: each location is written to Room immediately,
 * flagged as unsynced, then batched for later network transmission.
 */
@Singleton
class LocationBatcherImpl @Inject constructor(
    private val trackRepository: TrackRepository
) : LocationBatcher {

    private val inMemoryBuffer = mutableListOf<TrackNodeEntity>()
    private val BUFFER_SIZE = 50
    private val FLUSH_INTERVAL_MS = 60_000L // 60 seconds

    override suspend fun addNode(node: TrackNodeEntity) {
        // Persist first (non-synced)
        trackRepository.insertTrack(node)
        inMemoryBuffer.add(node)

        // Auto-flush if buffer reaches threshold
        if (inMemoryBuffer.size >= BUFFER_SIZE) {
            flush()
        }
    }

    override suspend fun flush() {
        if (inMemoryBuffer.isNotEmpty()) {
            // All nodes are already persisted; just clear buffer
            inMemoryBuffer.clear()
        }
    }

    override suspend fun getUnsyncedBatch(batchSize: Int): List<TrackNodeEntity> {
        return trackRepository.getUnsynced(batchSize)
    }

    override suspend fun markAsSynced(ids: List<String>) {
        trackRepository.markSynced(ids, System.currentTimeMillis())
    }
}
