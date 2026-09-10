package com.discomplemented.ginseng.ai.feature

import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureExtractorImplTest {

    private val extractor = FeatureExtractorImpl()

    @Test
    fun `extract should correctly map all input parameters to FeatureSet`() {
        // Given
        val lat = 35.5
        val lon = -82.5
        val alt = 500f
        val canopy = 0.7f
        val slope = 15.0f
        val aspect = 45.0f
        val moisture = 0.6f

        // When
        val result = extractor.extract(
            latitude = lat,
            longitude = lon,
            altitude = alt,
            canopyCover = canopy,
            slopeAngle = slope,
            aspect = aspect,
            moistureLevel = moisture
        )

        // Then
        assertEquals(lat, result.latitude, 0.0001)
        assertEquals(lon, result.longitude, 0.0001)
        assertEquals(alt, result.altitude, 0.0001f)
        assertEquals(canopy, result.canopyCover, 0.0001f)
        assertEquals(slope, result.slopeAngle, 0.0001f)
        assertEquals(aspect, result.aspect, 0.0001f)
        assertEquals(moisture, result.moistureLevel, 0.0001f)
    }
}
