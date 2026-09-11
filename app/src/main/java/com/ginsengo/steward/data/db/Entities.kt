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
    /**
     * Local mode: a path relative to Context.filesDir (never absolute, so the record
     * survives an app-data move, and never a MediaStore URI). Cloud mode (Phase 3) would
     * hold a Firebase Storage object path resolved to a signed URL at access time.
     * It is never a public URL in either mode.
     */
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
    /** Share of the model's movement that came from the digger's own checklist answers. */
    val modelShareFromAnswers: Double? = null,
    val createdDate: Long = System.currentTimeMillis(),
)

class Converters {
    @TypeConverter
    fun fromList(value: List<String>?): String = value.orEmpty().joinToString("")

    @TypeConverter
    fun toList(value: String?): List<String> =
        if (value.isNullOrEmpty()) emptyList() else value.split("")
}
