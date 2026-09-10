package com.discomplemented.ginseng.location.batcher

import com.discomplemented.ginseng.data.local.database.entity.TrackNodeEntity

/**
 * Interface for batching location updates.
 * Implements persist-first strategy: writes immediately to Room,
 * then batches for network transmission.
 */
interface LocationBatcher {
    /**
     * Adds a location node to the batch.
     * Persists immediately to database.
     */
    suspend fun addNode(node: TrackNodeEntity)

    /**
     * Flushes accumulated batch to database.
     */
    suspend fun flush()

    /**
     * Returns unsynced batch for network transmission.
     */
    suspend fun getUnsyncedBatch(batchSize: Int = 50): List<TrackNodeEntity>

    /**
     * Marks a batch as synced.
     */
    suspend fun markAsSynced(ids: List<String>)
}
