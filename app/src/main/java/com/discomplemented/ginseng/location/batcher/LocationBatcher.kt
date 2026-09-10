package com.discomplemented.ginseng.location.batcher

import com.discomplemented.ginseng.domain.model.TrackNode

interface LocationBatcher {
    /**
     * Starts collecting location updates from the provided flow and
     * manages the batching/persistence logic.
     */
    suspend fun collectAndBatch(nodes: kotlinx.coroutines.flow.Flow<TrackNode>)

    /**
     * Forces an immediate flush of any currently buffered nodes to the repository.
     * This is used for the "Emergency Flush" mechanism.
     */
    suspend fun flush()

    /**
     * Stops the collection and performs a final flush.
     */
    suspend fun stop()
}
