package com.ginsengo.steward.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/*
 * The app's persistent memory: where you have been, what you found, what was suggested and
 * what became of each suggestion, and every research run. All of it lives in app-private
 * storage; android:allowBackup is false, so none of it leaves the phone through backup.
 */

/** One stored fix on a recorded track. Only fixes that pass [com.ginsengo.steward.field.TrackFilter]. */
@Entity(tableName = "track_points", indices = [Index("time"), Index("sessionId")])
data class TrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
    val altitudeM: Double?,
    val time: Long,
)

/**
 * A place where the user found ginseng.
 *
 * [verification] is computed by [com.ginsengo.steward.learn.FindVerifier] from the recorded
 * accuracy, fix age and identification checks, never typed in. Only VERIFIED finds are used
 * by the learner.
 *
 * The six `f*` columns are the terrain factors at this point, filled in by the radius scan
 * (same DEM zoom as the learner's background) and null until a scan has covered the find.
 */
@Entity(tableName = "finds", indices = [Index("time")])
data class Find(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val lat: Double,
    val lng: Double,
    val accuracyM: Float?,
    val fixCount: Int,
    val fixTime: Long?,
    val time: Long = System.currentTimeMillis(),
    val plantCount: Int,
    val maxProngs: Int?,
    val note: String,
    val checks: Int,
    val verification: String,
    val fHeat: Double? = null,
    val fPosition: Double? = null,
    val fWetness: Double? = null,
    val fSlope: Double? = null,
    val fCurvature: Double? = null,
    val fElevation: Double? = null,
    val featureZoom: Int? = null,
    /** Set when this row was imported from a patch logged by the previous app. */
    val sourcePatchId: String? = null,
) {
    fun factors(): DoubleArray? {
        val v = listOf(fHeat, fPosition, fWetness, fSlope, fCurvature, fElevation)
        if (v.any { it == null }) return null
        return DoubleArray(6) { v[it]!! }
    }

    fun withFactors(f: DoubleArray, zoom: Int) = copy(
        fHeat = f[0], fPosition = f[1], fWetness = f[2],
        fSlope = f[3], fCurvature = f[4], fElevation = f[5], featureZoom = zoom,
    )
}

/**
 * One suggestion from one research run, and what became of it.
 *
 * Status moves NEW -> VISITED automatically when a recorded track passes within 40 m, then
 * to FOUND (a find recorded within 60 m) or NOT_FOUND (the user says they walked it and
 * found none). Those outcomes are what the next run's prompt is told about, as counts.
 */
@Entity(tableName = "suggestions", indices = [Index("runId"), Index("status")])
data class Suggestion(
    @PrimaryKey val id: String,
    val runId: String,
    val candidateKey: String,
    val label: String,
    val lat: Double,
    val lng: Double,
    val rank: Int,
    val terrainScore: Double,
    val factorsCsv: String,
    val elevationM: Double,
    val slopeDeg: Double,
    val aspectDeg: Double,
    val headline: String,
    val rationale: String,
    val lookFor: String,
    /** JSON array of {"url","title"}; only sources the model's own search retrieved. */
    val sourcesJson: String,
    /** COMPUTED (on device only) or MODEL (annotated by the research model). */
    val provenance: String,
    val status: String = STATUS_NEW,
    val statusTime: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val STATUS_NEW = "NEW"
        const val STATUS_VISITED = "VISITED"
        const val STATUS_FOUND = "FOUND"
        const val STATUS_NOT_FOUND = "NOT_FOUND"
        const val PROVENANCE_COMPUTED = "COMPUTED"
        const val PROVENANCE_MODEL = "MODEL"
    }
}

/**
 * One research run. The counts are the audit trail: how many candidates the device
 * computed, how many IDs and citations the validator threw away, which weights ranked it.
 * [centerLat]/[centerLng] are the exact centre, kept on the device; the prompt only ever
 * carried the coarse cell.
 */
@Entity(tableName = "research_runs", indices = [Index("time")])
data class ResearchRun(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val time: Long = System.currentTimeMillis(),
    val centerLat: Double,
    val centerLng: Double,
    val radiusM: Double,
    val provider: String?,
    val model: String?,
    val status: String,
    val message: String?,
    val summary: String?,
    val candidatesComputed: Int,
    val idsRejected: Int,
    val citationsKept: Int,
    val citationsRejected: Int,
    val weights: String,
    val durationMs: Long,
)
