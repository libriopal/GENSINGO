package com.discomplemented.ginseng.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.discomplemented.ginseng.data.local.database.entity.GinsengPatchEntity
import com.discomplemented.ginseng.data.local.database.entity.TrackNodeEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SyncService with network availability check.
 * Deferred: Actual HTTP sync logic awaits backend API definition.
 */
@Singleton
class SyncServiceImpl @Inject constructor(
    private val context: Context
) : SyncService {

    override suspend fun syncTrackNodes(nodes: List<TrackNodeEntity>): Boolean {
        if (!isNetworkAvailable()) return false
        // TODO: Implement HTTP POST to backend
        return true
    }

    override suspend fun syncGinsengPatches(patches: List<GinsengPatchEntity>): Boolean {
        if (!isNetworkAvailable()) return false
        // TODO: Implement HTTP POST to backend
        return true
    }

    override suspend fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
