package com.discomplemented.ginseng.core.util

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for geographic utilities.
 */
class GeoUtilsTest {
    @Test
    fun testHaversineDistance() {
        // Distance from New York to Los Angeles should be ~3944 km
        val distance = GeoUtils.haversineDistance(
            40.7128, -74.0060,  // NYC
            34.0522, -118.2437  // LA
        )
        assertTrue(distance > 3900 && distance < 4000)
    }

    @Test
    fun testRadianConversion() {
        val degrees = 180.0
        val radians = GeoUtils.toRadians(degrees)
        val backToDegrees = GeoUtils.toDegrees(radians)
        assertTrue(Math.abs(backToDegrees - degrees) < 0.0001)
    }
}
