package com.ginsengo.steward.data.reference

import android.content.Context
import com.ginsengo.steward.core.Provenance
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Bundled read-only reference data (PRD §6.2, §6.3). Not Room entities - these never change
 * on device, so they ship as assets and are parsed once.
 */

@Serializable
data class StateRegulation(
    @SerialName("state_code") val stateCode: String,
    @SerialName("state_name") val stateName: String,
    @SerialName("is_tribal") val isTribal: Boolean = false,
    @SerialName("season_start") val seasonStart: String? = null,
    @SerialName("season_start_verified") val seasonStartVerified: Boolean = false,
    @SerialName("season_end") val seasonEnd: String? = null,
    @SerialName("season_end_verified") val seasonEndVerified: Boolean = false,
    @SerialName("minimum_age_years") val minimumAgeYears: Int = 5,
    @SerialName("minimum_prongs") val minimumProngs: Int = 3,
    @SerialName("permit_required") val permitRequired: Boolean = false,
    @SerialName("agency") val agency: String = "",
    @SerialName("harvest_notes") val harvestNotes: String = "",
    @SerialName("source") val source: String = "",
) {
    /**
     * The season end is the field most likely to be wrong, because FWS does not publish it
     * per state. When it is unverified the UI must never render a confident date.
     */
    val seasonEndDisplay: String
        get() = when {
            seasonEnd != null && seasonEndVerified -> seasonEnd
            else -> "confirm with $agency"
        }

    val provenance: Provenance
        get() = if (seasonEndVerified) Provenance.VERIFIED else Provenance.PROTOTYPE
}

@Serializable
data class CompanionPlant(
    @SerialName("slug") val slug: String,
    @SerialName("name") val name: String,
    @SerialName("scientific_name") val scientificName: String,
    @SerialName("photo_asset") val photoAsset: String,
    @SerialName("description") val description: String,
    @SerialName("indicator_strength") val indicatorStrength: String,
    @SerialName("photo_license") val photoLicense: String = "",
    @SerialName("photo_author") val photoAuthor: String = "",
    @SerialName("photo_source") val photoSource: String = "",
    @SerialName("photo_title") val photoTitle: String = "",
    @SerialName("media_type") val mediaType: String = "photograph",
) {
    val assetPath: String get() = "images/$photoAsset"
    val attribution: String
        get() = buildString {
            append(photoAuthor.ifBlank { "Wikimedia Commons" })
            if (photoLicense.isNotBlank()) append(" - ").append(photoLicense)
        }
}

class ReferenceRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val states: List<StateRegulation> by lazy {
        runCatching {
            json.decodeFromString<List<StateRegulation>>(read("data/state_regulations.json"))
        }.getOrElse { emptyList() }
    }

    val companions: List<CompanionPlant> by lazy {
        runCatching {
            json.decodeFromString<List<CompanionPlant>>(read("data/companion_plants.json"))
        }.getOrElse { emptyList() }
    }

    fun stateByCode(code: String?): StateRegulation? =
        code?.let { c -> states.firstOrNull { it.stateCode.equals(c, ignoreCase = true) } }

    fun read(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    fun openAsset(path: String) = context.assets.open(path)
}
