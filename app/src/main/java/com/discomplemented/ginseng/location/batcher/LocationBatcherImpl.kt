package com.discomplemented.ginseng.location.batcher

import com.discomplemented.ginseng.domain.model.TrackNode
import com.discomplemented.ginseng.domain.repository.TrackRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class LocationBatcherImpl @Inject constructor(
    private val trackRepository: TrackRepository
) : LocationBatcher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val buffer = mutableListOf<TrackNode>()

    private var batchJob: Job? = null
    private val batchSize = 50
    private val flushIntervalMs = 60_000L // 1 minute

    override suspend fun collectAndBatch(nodes: Flow<TrackNode>) {
        batchJob = scope.launch {
            // Start the periodic flush timer
            launch {
                while (isActive) {
                    delay(flushIntervalMs)
                    flush()
                }
            }

            // Collect nodes from the flow
            nodes.collect { node ->
                mutex.withLock {
                    buffer.add(node)
                    if (buffer.size >= batchSize) {
                        flushInternal()
                    }
                }
            }
        }
    }

    override suspend fun flush() {
        mutex.withLock {
            flushInternal()
        }
    }

    override suspend fun stop() {
        batchJob?.cancelAndJoin()
        flush()
        scope.cancel()
    }

    private suspend fun flushInternal() {
        if (buffer.isEmpty()) return

        val nodesToSave = buffer.toList()
        buffer.clear()

        try {
            trackRepository.saveTrackNodes(nodesToSave)
        } catch (e: Exception) {
            // In a real app, we might want to re-add nodes to the buffer or log the error
            // For now, we log and move on to avoid infinite retry loops
            e.printStackTrace()
        }
    }
}
