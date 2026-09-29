package com.ginsengo.steward.learn

import com.ginsengo.steward.terrain.GinsengSuitability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FindVerifierTest {

    private val all = FindVerifier.Check.ALL

    @Test
    fun verifiedNeedsPositionFreshnessAndAllThreeChecks() {
        assertEquals(FindVerifier.Level.VERIFIED, FindVerifier.verify(12f, 5_000, all).level)
    }

    @Test
    fun eachMissingConditionAloneBreaksVerification() {
        assertEquals(FindVerifier.Level.UNVERIFIED, FindVerifier.verify(25f, 5_000, all).level)
        assertEquals(FindVerifier.Level.UNVERIFIED, FindVerifier.verify(12f, 120_000, all).level)
        assertEquals(FindVerifier.Level.UNVERIFIED, FindVerifier.verify(null, 5_000, all).level)
        assertEquals(FindVerifier.Level.UNVERIFIED, FindVerifier.verify(12f, null, all).level)
        for (c in FindVerifier.Check.entries) {
            val r = FindVerifier.verify(12f, 5_000, all and c.bit.inv())
            assertEquals("missing ${c.name}", FindVerifier.Level.UNVERIFIED, r.level)
            assertTrue(r.reasons.isNotEmpty())
        }
    }

    @Test
    fun theThresholdIsInclusiveAtTwentyMetres() {
        assertEquals(FindVerifier.Level.VERIFIED, FindVerifier.verify(20f, 0, all).level)
    }
}

class FindLearnerTest {

    private val prior = GinsengSuitability.PRIOR_WEIGHTS

    /** Terrain-like background: six factors, loosely correlated, in 0..1. */
    private fun background(n: Int, r: Random) = (0 until n).map {
        val base = r.nextDouble()
        FindLearner.Sample(DoubleArray(6) { (0.6 * base + 0.4 * r.nextDouble()).coerceIn(0.0, 1.0) },
            35.0 + r.nextDouble() * 0.2, -83.0 + r.nextDouble() * 0.2)
    }

    /** [clusters] spots at least 1 km apart, [perCluster] finds within 30 m of each. */
    private fun finds(clusters: Int, perCluster: Int, r: Random, factors: (Random) -> DoubleArray) =
        (0 until clusters).flatMap { c ->
            val lat = 35.0 + c * 0.02; val lng = -83.0
            (0 until perCluster).map {
                FindLearner.Sample(factors(r), lat + r.nextDouble() * 0.0002, lng + r.nextDouble() * 0.0002)
            }
        }

    @Test
    fun belowFiveFindsNothingIsLearned() {
        val r = Random(1)
        val v = FindLearner.evaluate(finds(4, 1, r) { DoubleArray(6) { 1.0 } }, background(400, r), prior)
        assertFalse(v.adopted)
        assertTrue(v.active.contentEquals(prior))
    }

    @Test
    fun manyFindsInFewSpotsCannotBeAdoptedBecauseTheyCannotBeHeldOut() {
        val r = Random(2)
        // A strong, real signal - but only in 3 places.
        val f = finds(3, 10, r) { rr -> DoubleArray(6) { i -> if (i == 5) 0.95 else rr.nextDouble() } }
        val v = FindLearner.evaluate(f, background(400, r), prior)
        assertEquals(3, v.clusters)
        assertFalse(v.adopted)
    }

    @Test
    fun clustersAreSpatialNotPerFind() {
        val r = Random(3)
        val f = finds(6, 3, r) { DoubleArray(6) { 0.5 } }
        assertEquals(6, FindLearner.clusters(f).distinct().size)
    }

    /**
     * NEGATIVE CONTROL. Finds that are just random places from the same terrain carry no
     * signal. A learner judged on its own training data would still "improve"; this gate must
     * reject nearly all of them. Measured over many seeds, not one.
     */
    @Test
    fun randomFindsAreRejectedByTheHeldOutGate() {
        var adopted = 0
        val trials = 60
        for (seed in 0 until trials) {
            val r = Random(100 + seed)
            val bg = background(400, r)
            val f = finds(8, 2, r) { rr -> bg[rr.nextInt(bg.size)].factors.copyOf() }
            if (FindLearner.evaluate(f, bg, prior, seed = seed.toLong()).adopted) adopted++
        }
        assertTrue("random finds adopted in $adopted/$trials trials", adopted <= trials * 0.10)
    }

    /**
     * POSITIVE CONTROL. Finds that really do concentrate on a factor the prior barely weighs
     * (elevation, 0.06) must be learnable, or the gate is a wall rather than a test.
     */
    @Test
    fun aRealSignalThePriorUnderweightsIsAdopted() {
        var adopted = 0
        val trials = 20
        for (seed in 0 until trials) {
            val r = Random(500 + seed)
            val bg = background(400, r)
            val f = finds(8, 2, r) { rr ->
                val x = bg[rr.nextInt(bg.size)].factors.copyOf()
                x[5] = 0.9 + 0.1 * rr.nextDouble()   // elevation band: always right
                x[0] = rr.nextDouble() * 0.5         // heat load: NOT what these finds share
                x
            }
            val v = FindLearner.evaluate(f, bg, prior, seed = seed.toLong())
            if (v.adopted) {
                adopted++
                assertTrue("learned elevation weight should rise", v.active[5] > prior[5])
            }
        }
        assertTrue("real signal adopted in only $adopted/$trials trials", adopted >= trials * 0.7)
    }

    @Test
    fun learnedWeightsAreAValidSurface() {
        val r = Random(9)
        val bg = background(300, r)
        val w = FindLearner.fit(bg.take(12).map { it.factors }, bg.map { it.factors }, prior)
        assertEquals(1.0, w.sum(), 1e-9)
        assertTrue(w.all { it >= 0.0 })
    }

    @Test
    fun signFlipIsExact() {
        assertEquals(1.0 / 32, FindLearner.signFlipP(doubleArrayOf(0.1, 0.2, 0.3, 0.4, 0.5)), 1e-12)
        assertEquals(1.0 / 16, FindLearner.signFlipP(doubleArrayOf(0.1, 0.2, 0.3, 0.4)), 1e-12)
        assertTrue(FindLearner.signFlipP(doubleArrayOf(-0.1, -0.2, -0.3, -0.4, -0.5)) > 0.9)
    }
}
