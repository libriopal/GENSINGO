package com.discomplemented.ginseng.location.tracker

import com.discomplemented.ginseng.data.local.database.entity.TrackNodeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Interface for high-accuracy GPS location tracking.
 */
interface LocationTracker {
    /**
     * Starts continuous location updates.
     * Emits location updates as Flow.
     */
    fun startTracking(): Flow<TrackNodeEntity>

    /**
     * Stops tracking.
     */
    fun stopTracking()

    /**
     * Returns true if tracking is active.
     */
    fun isTracking(): Boolean
}
