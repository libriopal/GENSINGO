package com.discomplemented.ginseng.data.remote

import com.discomplemented.ginseng.data.local.database.entity.TrackNodeEntity
import com.discomplemented.ginseng.data.local.database.entity.GinsengPatchEntity

/**
 * Network API interface for syncing data when internet is available.
 */
interface SyncService {
    suspend fun syncTrackNodes(nodes: List<TrackNodeEntity>): Boolean
    suspend fun syncGinsengPatches(patches: List<GinsengPatchEntity>): Boolean
    suspend fun isNetworkAvailable(): Boolean
}
