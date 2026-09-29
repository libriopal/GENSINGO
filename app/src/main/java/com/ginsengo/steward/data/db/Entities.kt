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
 * List<String> <-> one TEXT column.
 *
 * The separator is U+001F (INFORMATION SEPARATOR ONE), written as an escape so no editor can
 * silently turn it into something else again. That is exactly what happened in commit
 * 7a3656d: the raw control character in this file became a space, so a two-word species like
 * "Black cohosh" split into two entries, and every reading saved by the field-tested build
 * (which used U+001F) would have read back as one run-together string.
 *
 * Reading: a value containing U+001F is split on it; a value without it is a single element.
 * No build ever wrote space-joined lists through a UI (the build that used spaces had no UI),
 * so there are no space-joined rows to recover, and splitting on spaces would corrupt the
 * single-element rows the field-tested build did write.
 */
class Converters {
    @TypeConverter
    fun fromList(value: List<String>?): String = value.orEmpty().joinToString(SEPARATOR)

    @TypeConverter
    fun toList(value: String?): List<String> =
        if (value.isNullOrEmpty()) emptyList() else value.split(SEPARATOR)

    companion object {
        const val SEPARATOR = "\u001F"
    }
}
