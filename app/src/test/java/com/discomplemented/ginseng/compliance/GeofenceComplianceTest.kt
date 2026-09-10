package com.discomplemented.ginseng.compliance

import com.discomplemented.ginseng.domain.model.LatLng
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for geofence compliance.
 */
class GeofenceComplianceTest {
    private val compliance = GeofenceCompliance()

    @Test
    fun testLocationOutsideProtectedArea() {
        val location = LatLng(36.0, -85.0)  // Outside Great Smoky Mountains
        assertFalse(compliance.isInProtectedArea(location))
    }

    @Test
    fun testLocationInsideProtectedArea() {
        val location = LatLng(35.6, -83.0)  // Inside Great Smoky Mountains
        assertTrue(compliance.isInProtectedArea(location))
    }
}
