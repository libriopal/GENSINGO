package com.ginsengo.steward.ui

import android.content.Context
import com.ginsengo.steward.research.RadiusScan
import com.ginsengo.steward.terrain.DemTileStore
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.math.cos

/**
 * "Save this area": everything the app needs to keep working with no signal across the
 * 10-mile radius.
 *
 *  - Elevation: z11-z13 over the whole radius (the radius scan and the landscape heatmap),
 *    plus z14-z15 within [DETAIL_M] of the user (the field-scale heatmap and 3D view).
 *    Tiles land in the same cache the app reads from, so nothing else changes offline.
 *  - Basemap: a MapLibre offline region over the radius at z8-z14, which also caches the
 *    style, so the map opens with no network.
 *
 * Sizes are bounded: ~150 elevation tiles (~15 MB) plus the vector region.
 */
object OfflineArea {

    const val DETAIL_M = 1_500.0

    /** Tile ranges to fetch, as (z, x, y). Pure, so the count is tested. */
    fun demTiles(lat: Double, lng: Double): List<Triple<Int, Int, Int>> {
        val out = ArrayList<Triple<Int, Int, Int>>()
        fun add(z: Int, radiusM: Double) {
            val dLat = radiusM / 111_320.0
            val dLng = radiusM / (111_320.0 * cos(Math.toRadians(lat)))
            val x0 = DemTileStore.lonToTileX(lng - dLng, z); val x1 = DemTileStore.lonToTileX(lng + dLng, z)
            val y0 = DemTileStore.latToTileY(lat + dLat, z); val y1 = DemTileStore.latToTileY(lat - dLat, z)
            for (x in x0..x1) for (y in y0..y1) out += Triple(z, x, y)
        }
        for (z in 11..13) add(z, RadiusScan.RADIUS_M + RadiusScan.TPI_RADIUS_M * 1.5)
        add(14, DETAIL_M); add(15, DETAIL_M)
        return out
    }

    suspend fun prefetchDem(
        dem: DemTileStore, lat: Double, lng: Double,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Int {
        val tiles = demTiles(lat, lng)
        var ok = 0
        tiles.forEachIndexed { i, (z, x, y) ->
            if (dem.tile(z, x, y) != null) ok++
            if (i % 10 == 0) onProgress(i, tiles.size)
        }
        return ok
    }

    fun downloadBasemap(
        context: Context, styleUrl: String, lat: Double, lng: Double,
        onDone: (String) -> Unit,
    ) {
        val r = RadiusScan.RADIUS_M
        val dLat = r / 111_320.0
        val dLng = r / (111_320.0 * cos(Math.toRadians(lat)))
        val bounds = LatLngBounds.Builder()
            .include(LatLng(lat + dLat, lng - dLng))
            .include(LatLng(lat - dLat, lng + dLng))
            .build()
        val def = OfflineTilePyramidRegionDefinition(
            styleUrl, bounds, 8.0, 14.0, context.resources.displayMetrics.density,
        )
        OfflineManager.getInstance(context).createOfflineRegion(
            def, "gensingo-area".toByteArray(),
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            if (status.isComplete) {
                                offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                onDone("Map saved: ${status.completedResourceCount} tiles")
                            }
                        }
                        override fun onError(error: OfflineRegionError) {
                            onDone("Map download: ${error.message}")
                        }
                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            onDone("Map download hit the tile limit ($limit)")
                        }
                    })
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) = onDone("Map download failed: $error")
            },
        )
    }
}
