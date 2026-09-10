package com.discomplemented.ginseng.compliance.geofence

import kotlinx.coroutines.flow.Flow

/**
 * Monitors the user's location relative to prohibited areas and emits warnings or violations.
 */
interface GeofenceMonitor {
    /**
     * Starts monitoring proximity to a list of prohibited areas.
     * @param prohibitedAreas The list of areas to monitor.
     * @return A flow of geofence results.
     */
    fun monitor(prohibitedAreas: List<ProhibitedArea>): Flow<GeofenceResult>
}
