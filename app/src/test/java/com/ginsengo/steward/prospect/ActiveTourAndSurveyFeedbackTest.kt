package com.ginsengo.steward.prospect

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests verifying:
 * 1. Live footstep tracking stride math and Great-circle distance calculations.
 * 2. Monte Carlo stochastic simulation convergence and 95% credible intervals.
 * 3. EINCOL protocol controls: ground-truth survey returns adaptively update Bayesian priors
 *    with positive and negative controls.
 */
class ActiveTourAndSurveyFeedbackTest {

    @Test
    fun distanceAndFootstepStrideCalculationsAreAccurate() {
        // Test two points approximately 500 meters apart in Haywood County
        val lat1 = 35.5000
        val lon1 = -82.9500
        val lat2 = 35.5045
        val lon2 = -82.9500 // roughly 500m north

        val dist = Prospects.distanceMetres(lat1, lon1, lat2, lon2)
        assertTrue("Distance should be approximately 500m, was $dist", dist in 490.0..510.0)

        // Stride calculation: average walking stride is ~0.76m
        val steps = (dist / 0.76).toInt()
        assertTrue("Steps should be approximately 650 steps, was $steps", steps in 640..670)

        // Bearing calculation: due north should be ~0 degrees
        val bearing = Prospects.bearingTrue(lat1, lon1, lat2, lon2)
        assertTrue("Bearing should be near 0° True North, was $bearing", bearing < 5.0 || bearing > 355.0)
    }

    @Test
    fun monteCarloSimulationConvergesWithinCredibleInterval() = runBlocking {
        val input = GinsengMonteCarloEngine.MonteCarloInput(
            centerLat = 35.50,
            centerLng = -82.95,
            countyName = "Haywood",
            baseElevationMeters = 920.0,
            baseSlopeDegrees = 22.0,
            baseAspectDegrees = 45.0,
            baseCanopyEstimate = 0.80,
            baseSoilMoistureEstimate = 0.75,
            iterations = 1000
        )

        val result = GinsengMonteCarloEngine.runSimulation(input)

        assertEquals(1000, result.iterationsRun)
        assertTrue("Mean ESI should be realistic (0.5 to 0.95), got ${result.meanEsi}", result.meanEsi in 0.5..0.95)
        assertTrue("Lower credible interval <= Mean ESI", result.lower95Credible <= result.meanEsi)
        assertTrue("Upper credible interval >= Mean ESI", result.upper95Credible >= result.meanEsi)
        assertTrue("Probability of viable habitat in [0, 1]", result.pViableHabitat in 0.0..1.0)
        assertTrue("Variance must be non-negative", result.variance >= 0.0)
    }

    /**
     * EINCOL Positive Control:
     * When verified harvest zones with real roots dug are supplied to the Monte Carlo engine,
     * the simulation detects the harvest adaptation and shifts the posterior elevation mean.
     */
    @Test
    fun positiveControl_harvestGroundTruthAdaptsSimulation() = runBlocking {
        val harvestPrior = GinsengMonteCarloEngine.HarvestZonePrior(
            hasVerifiedHarvest = true,
            harvestCount = 3,
            totalRootsDug = 18,
            verifiedMeanSlope = 24.0,
            verifiedMeanElevation = 980.0,
            verifiedAspectDegrees = 42.0,
            verifiedSoilRating = 0.92
        )

        val input = GinsengMonteCarloEngine.MonteCarloInput(
            centerLat = 35.50,
            centerLng = -82.95,
            countyName = "Haywood",
            baseElevationMeters = 920.0,
            baseSlopeDegrees = 22.0,
            baseAspectDegrees = 45.0,
            iterations = 1000,
            harvestPrior = harvestPrior
        )

        val result = GinsengMonteCarloEngine.runSimulation(input)

        assertTrue("Simulation should register harvest adaptation", result.isHarvestAdapted)
        assertEquals(18, result.harvestRootsInfluencing)
        assertTrue("Posterior elevation should shift toward 980m (was ${result.posteriorElevationMean})",
            result.posteriorElevationMean in 921.0..980.0)
        assertTrue("Posterior slope should shift toward 24° (was ${result.posteriorSlopeMean})",
            result.posteriorSlopeMean in 22.0..24.0)
    }

    /**
     * EINCOL Negative Control:
     * When no harvest occurs (default prior), the engine must NOT falsely report harvest adaptation.
     */
    @Test
    fun negativeControl_defaultPriorHasNoHarvestFalsePositive() = runBlocking {
        val input = GinsengMonteCarloEngine.MonteCarloInput(
            centerLat = 35.50,
            centerLng = -82.95,
            countyName = "Haywood",
            baseElevationMeters = 920.0,
            baseSlopeDegrees = 22.0,
            baseAspectDegrees = 45.0,
            iterations = 500,
            harvestPrior = GinsengMonteCarloEngine.HarvestZonePrior(hasVerifiedHarvest = false)
        )

        val result = GinsengMonteCarloEngine.runSimulation(input)
        assertFalse("Uninformed run must not report harvest adaptation", result.isHarvestAdapted)
        assertEquals(0, result.harvestRootsInfluencing)
    }
}
