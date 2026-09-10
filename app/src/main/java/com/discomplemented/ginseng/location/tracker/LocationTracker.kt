package com.discomplemented.ginseng.location.tracker

import com.discomplemented.ginseng.domain.model.TrackNode
import kotlinx.coroutines.flow.Flow

interface LocationTracker {
    /**
     * Returns a flow of high-accuracy location updates.
     */
    fun getLocationUpdates(): Flow<TrackNode>

    /**
     * Starts tracking location.
     */
    fun startTracking()

    /**
     * Stops tracking location.
     */
    fun stopTracking()
}
