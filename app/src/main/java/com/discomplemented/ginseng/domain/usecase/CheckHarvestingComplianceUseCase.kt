package com.discomplemented.ginseng.domain.usecase

import com.discomplemented.ginseng.compliance.GeofenceCompliance
import com.discomplemented.ginseng.compliance.SeasonalCompliance
import com.discomplemented.ginseng.domain.model.LatLng
import javax.inject.Inject

/**
 * Use case for checking harvesting compliance.
 */
class CheckHarvestingComplianceUseCase @Inject constructor(
    private val geofenceCompliance: GeofenceCompliance,
    private val seasonalCompliance: SeasonalCompliance
) {
    operator fun invoke(location: LatLng): ComplianceResult {
        val isInProtectedArea = geofenceCompliance.isInProtectedArea(location)
        val isInSeason = seasonalCompliance.isInHarvestingSeason()

        return ComplianceResult(
            compliant = !isInProtectedArea && isInSeason,
            protectedArea = isInProtectedArea,
            inSeason = isInSeason,
            message = seasonalCompliance.getComplianceMessage()
        )
    }

    data class ComplianceResult(
        val compliant: Boolean,
        val protectedArea: Boolean,
        val inSeason: Boolean,
        val message: String
    )
}
