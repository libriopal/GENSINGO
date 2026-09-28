package com.ginsengo.steward.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.util.UUID

/** PRD §6.1 */
@Entity(tableName = "ginseng_patches")
data class GinsengPatch(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val lat: Double,
    val lng: Double,
    val photoPath: String? = null,
    val plantCount: Int = 0,
    val notes: String = "",
    val habitatScore: Double? = null,
    val harvested: Boolean = false,
    val rootsHarvested: Int? = null,
    val seedsReplanted: Int? = null,
    val lastVisitedDate: Long = System.currentTimeMillis(),
    val provenance: String = "prototype",
)

/** PRD §6.4 */
@Entity(tableName = "habitat_readings")
data class HabitatReadingRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patchId: String? = null,
    val lat: Double,
    val lng: Double,
    val slopeOrientation: String,
    val slopePosition: String,
    val treesPresent: List<String>,
    val companionPlantsSeen: List<String>,
    val soilCheck: Boolean,
    val result: String,
    val modelScore: Double? = null,
    val modelShareFromAnswers: Double? = null,
    val createdDate: Long = System.currentTimeMillis(),
)

/**
 * Persistent field observation memory for in-app learning and generative Bayesian updating.
 */
@Entity(tableName = "ginseng_observations")
data class GinsengObservationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val county: String,
    val lat: Double,
    val lng: Double,
    val observationType: String,
    val elevationMeters: Double,
    val slopePercent: Double,
    val aspectDegrees: Double,
    val canopyCoverage: Double,
    val soilMoistureScore: Double,
    val companionSpecies: List<String>,
    val observedEsi: Double,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Persistent Monte Carlo stochastic simulation results for local 10-mile radius forecast.
 */
@Entity(tableName = "monte_carlo_logs")
data class MonteCarloRecordEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val bufferCounty: String,
    val centerLat: Double,
    val centerLng: Double,
    val iterations: Int,
    val meanEsi: Double,
    val variance: Double,
    val lowerCredibleInterval: Double,
    val upperCredibleInterval: Double,
    val pViableHabitat: Double,
    val deltaWitnessPin: Double,
    val convergenceDelta: Double,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Persistent 10-mile radius geospatial buffer state.
 */
@Entity(tableName = "radius_buffers")
data class RadiusBufferEntity(
    @PrimaryKey val id: String = "active_buffer",
    val countyName: String,
    val centerLat: Double,
    val centerLng: Double,
    val radiusMiles: Double = 10.0,
    val lastUpdated: Long = System.currentTimeMillis(),
    val cachedConfidence: Double = 0.85,
    val topCandidatesJson: String = "",
)

/**
 * Human-verified harvest ground where user circled/lassoed terrain where they dug wild ginseng.
 * Used for Bayesian prior learning, Monte Carlo parameter adaptation, and in-app LLM research.
 */
@Entity(tableName = "verified_harvest_polygons")
data class VerifiedHarvestPolygonEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val county: String,
    val polygonGeoJson: String, // GeoJSON Coordinates array [[lng, lat], ...]
    val estimatedRootsHarvested: Int,
    val dominantSlopeDeg: Double,
    val dominantAspectDeg: Double,
    val meanElevationMeters: Double,
    val soilConditionRating: Double = 0.85, // 0.0 - 1.0
    val companionNotes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Record of an active foot prospecting tour across forecasted hotspots.
 * Feeds live GPS breadcrumbs, footstep cadence, and post-tour survey evaluations.
 */
@Entity(tableName = "prospecting_tours")
data class ProspectingTourEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val county: String,
    val targetHotspotName: String,
    val targetLat: Double,
    val targetLng: Double,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val totalDistanceMeters: Double,
    val totalSteps: Int,
    val elevationGainMeters: Double,
    val breadcrumbJson: String, // JSON array of [lat, lng, alt]
    val surveyCompleted: Boolean = false,
    val surveyObservationId: String? = null,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

class Converters {
    @TypeConverter
    fun fromList(value: List<String>?): String = value.orEmpty().joinToString(" ")

    @TypeConverter
    fun toList(value: String?): List<String> =
        if (value.isNullOrEmpty()) emptyList() else value.split(" ")
}
