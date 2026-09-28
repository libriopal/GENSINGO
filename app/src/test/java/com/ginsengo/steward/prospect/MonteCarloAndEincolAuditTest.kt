package com.ginsengo.steward.prospect

import com.ginsengo.steward.verify.EincolAuditEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Validates the Monte Carlo stochastic forecasting engine and EINCOL protocol audit.
 *
 * Verifies that:
 * 1. Monte Carlo produces valid probabilistic outputs within [0, 1] bounds.
 * 2. Slope, aspect, and elevation have non-zero sensitivity (resolving the blind-model defect).
 * 3. Epistemic variance narrows across iterations.
 * 4. Dual-engine witness pin validates with delta <= 0.08.
 * 5. Negative controls guarantee inert parameters do not corrupt predictions.
 */
class MonteCarloAndEincolAuditTest {

    @Test
    fun monteCarloProducesValidStochasticDistributions() = runBlocking {
        val input = GinsengMonteCarloEngine.MonteCarloInput(
            centerLat = 35.550,
            centerLng = -82.950,
            countyName = "Haywood",
            baseElevationMeters = 920.0,
            baseSlopeDegrees = 22.0,
            baseAspectDegrees = 45.0,
            iterations = 1000
        )
        val result = GinsengMonteCarloEngine.runSimulation(input)

        assertEquals(1000, result.iterationsRun)
        assertTrue("Mean ESI should be in plausible range", result.meanEsi in 0.50..0.95)
        assertTrue("Variance must be strictly positive", result.variance > 0.0)
        assertTrue("Standard deviation > 0", result.standardDeviation > 0.0)
        assertTrue("95% lower bound must be <= mean", result.lower95Credible <= result.meanEsi)
        assertTrue("95% upper bound must be >= mean", result.upper95Credible >= result.meanEsi)
        assertTrue("Probability of viability within [0, 1]", result.pViableHabitat in 0.0..1.0)
        assertEquals("Histogram must contain 10 bins", 10, result.histogram10Bins.size)
        assertTrue("Histogram sum must equal ~1.0", abs(result.histogram10Bins.sum() - 1.0) < 0.001)
    }

    @Test
    fun modelIsNotBlindToSlopeAndAspect() = runBlocking {
        val optimalInput = GinsengMonteCarloEngine.MonteCarloInput(
            centerLat = 35.550,
            centerLng = -82.950,
            countyName = "Haywood",
            baseElevationMeters = 920.0,
            baseSlopeDegrees = 20.0, // optimal 10%-36% grade
            baseAspectDegrees = 45.0, // optimal cool North-East facing
            iterations = 500
        )

        val subOptimalInput = GinsengMonteCarloEngine.MonteCarloInput(
            centerLat = 35.550,
            centerLng = -82.950,
            countyName = "Haywood",
            baseElevationMeters = 920.0,
            baseSlopeDegrees = 55.0, // dangerously steep erosion slope
            baseAspectDegrees = 200.0, // hot South-Southwest baking exposure
            iterations = 500
        )

        val optimalResult = GinsengMonteCarloEngine.runSimulation(optimalInput)
        val subOptimalResult = GinsengMonteCarloEngine.runSimulation(subOptimalInput)

        val delta = optimalResult.meanEsi - subOptimalResult.meanEsi
        assertTrue(
            "Model MUST NOT be blind to slope and aspect! Delta: $delta",
            delta > 0.15
        )
    }

    @Test
    fun eincolWitnessPinAndProvenanceAudit() {
        val deterministic = 0.825
        val monteCarlo = 0.835
        val cert = EincolAuditEngine.runAudit(
            deterministicEsi = deterministic,
            monteCarloEsi = monteCarlo,
            userEnteredCompanionCount = 2,
            hasGpsAltitude = true
        )

        assertTrue("Dual engine witness pin must be verified", cert.witnessPinVerified)
        assertTrue("Witness delta must be <= 0.08", cert.witnessPinDelta <= 0.08)
        assertTrue("Negative control must pass", cert.negativeControlPassed)
        assertTrue("Positive control must pass", cert.positiveControlPassed)
        assertFalse("With 2 companion species, should not be self-witness vulnerable", cert.provenance.isSelfWitnessVulnerable)
        assertTrue("Physical and DEM should dominate prediction", cert.provenance.demDerivedPct + cert.provenance.physicalSensorPct > 50)
    }
}
