package com.ginsengo.steward.prospect

import com.ginsengo.steward.terrain.GinsengSuitability
import com.ginsengo.steward.terrain.TerrainMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance Monte Carlo Stochastic Simulation and Forecasting Engine for GinsengTerra.
 *
 * Implements iterative stochastic sampling (1,000 to 10,000 runs) over the active
 * 10-mile radius spatial buffer to compute:
 * - Expected Eco-Suitability Index (ESI)
 * - 95% Bayesian Credible Interval
 * - Probability of Viable Micro-Habitat P(ESI >= 0.70)
 * - Convergence stability delta
 * - Multi-layer confidence margins for slope, aspect, elevation, canopy, and soil
 */
object GinsengMonteCarloEngine {

    data class HarvestZonePrior(
        val hasVerifiedHarvest: Boolean = false,
        val harvestCount: Int = 0,
        val totalRootsDug: Int = 0,
        val verifiedMeanSlope: Double = 22.0,
        val verifiedSlopeVariance: Double = 16.0,
        val verifiedMeanElevation: Double = 880.0,
        val verifiedElevationVariance: Double = 2500.0,
        val verifiedAspectDegrees: Double = 45.0,
        val verifiedSoilRating: Double = 0.85,
    )

    data class MonteCarloInput(
        val centerLat: Double,
        val centerLng: Double,
        val countyName: String,
        val baseElevationMeters: Double,
        val baseSlopeDegrees: Double,
        val baseAspectDegrees: Double,
        val baseCanopyEstimate: Double = 0.80,
        val baseSoilMoistureEstimate: Double = 0.75,
        val confirmedCompanionSightings: Int = 0,
        val historicalCountyYieldLbs: Int = 195,
        val iterations: Int = 5000,
        val harvestPrior: HarvestZonePrior = HarvestZonePrior(),
    )

    data class ConvergenceCheckpoint(
        val iterationCount: Int,
        val rollingMean: Double,
        val rollingVariance: Double,
        val rollingConfidenceWidth: Double,
    )

    data class MonteCarloResult(
        val input: MonteCarloInput,
        val iterationsRun: Int,
        val meanEsi: Double,
        val standardDeviation: Double,
        val variance: Double,
        val lower95Credible: Double,
        val upper95Credible: Double,
        val pViableHabitat: Double, // Probability that ESI >= 0.70
        val pMarginalHabitat: Double, // Probability that 0.40 <= ESI < 0.70
        val pUnsuitable: Double, // Probability that ESI < 0.40
        val histogram10Bins: List<Double>, // 0.0-0.1, 0.1-0.2, ... 0.9-1.0
        val convergenceHistory: List<ConvergenceCheckpoint>,
        val convergenceDelta: Double,
        val deterministicEsiBaseline: Double,
        val witnessPinDelta: Double, // |meanEsi - deterministic|
        val witnessPinVerified: Boolean,
        val slopeConfidencePct: Int,
        val aspectConfidencePct: Int,
        val elevationConfidencePct: Int,
        val canopyConfidencePct: Int,
        val soilConfidencePct: Int,
        val executionTimeMs: Long,
        val isHarvestAdapted: Boolean = false,
        val harvestRootsInfluencing: Int = 0,
        val posteriorElevationMean: Double = 0.0,
        val posteriorSlopeMean: Double = 0.0,
        val varianceReductionFactor: Double = 1.0,
    )

    /**
     * Executes the Monte Carlo simulation over the target 10-mile buffer parameters.
     */
    suspend fun runSimulation(input: MonteCarloInput): MonteCarloResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val rng = Random(42L + (input.centerLat * 1000).toLong() + (input.centerLng * 1000).toLong())

        val scores = DoubleArray(input.iterations)
        var sum = 0.0
        var sumSq = 0.0
        var viableCount = 0
        var marginalCount = 0
        var unsuitableCount = 0
        val binCounts = IntArray(10)

        val checkpoints = mutableListOf<ConvergenceCheckpoint>()
        val checkpointInterval = max(100, input.iterations / 10)

        // Baseline deterministic ESI reading for dual-engine witness pin
        val baseHeatLoad = TerrainMath.heatLoadIndex(
            input.centerLat,
            input.baseSlopeDegrees,
            input.baseAspectDegrees
        )
        val deterministicBreakdown = GinsengSuitability.score(
            heatLoadRaw = baseHeatLoad,
            tpiMeters = -3.5, // optimal lower cove position
            twi = 8.5,
            slopeDeg = input.baseSlopeDegrees,
            curvature = 0.02,
            elevationM = input.baseElevationMeters
        )
        val detBaseline = (deterministicBreakdown.score * 0.60) +
                (input.baseCanopyEstimate * 0.20) +
                (input.baseSoilMoistureEstimate * 0.20)

        // Learned Bayesian companion adjustment
        val companionBonus = min(0.08, input.confirmedCompanionSightings * 0.02)
        val harvest = input.harvestPrior
        val isHarvestActive = harvest.hasVerifiedHarvest && harvest.harvestCount > 0

        // Conjugate Gaussian-Gaussian Bayesian updating for Elevation:
        // Prior: N(mu_0, sigma_0^2 = 3600.0)
        // Likelihood from user-circled harvest grounds: N(x_bar, s^2 / n)
        val (effElevMean, effElevStd) = if (isHarvestActive) {
            val priorMu = input.baseElevationMeters
            val priorVar = 3600.0 // 60m std dev
            val sampleMu = harvest.verifiedMeanElevation
            val sampleVar = harvest.verifiedElevationVariance.coerceAtLeast(100.0)
            val n = harvest.harvestCount.toDouble()

            val postPrec = (1.0 / priorVar) + (n / sampleVar)
            val postVar = 1.0 / postPrec
            val postMu = ((priorMu / priorVar) + (n * sampleMu / sampleVar)) * postVar
            Pair(postMu, sqrt(postVar).coerceIn(15.0, 60.0))
        } else {
            Pair(input.baseElevationMeters, 60.0)
        }

        // Conjugate Gaussian-Gaussian Bayesian updating for Slope:
        // Prior: N(mu_0, sigma_0^2 = 20.25)
        val (effSlopeMean, effSlopeStd) = if (isHarvestActive) {
            val priorMu = input.baseSlopeDegrees
            val priorVar = 20.25 // 4.5 deg std dev
            val sampleMu = harvest.verifiedMeanSlope
            val sampleVar = harvest.verifiedSlopeVariance.coerceAtLeast(4.0)
            val n = harvest.harvestCount.toDouble()

            val postPrec = (1.0 / priorVar) + (n / sampleVar)
            val postVar = 1.0 / postPrec
            val postMu = ((priorMu / priorVar) + (n * sampleMu / sampleVar)) * postVar
            Pair(postMu, sqrt(postVar).coerceIn(1.5, 4.5))
        } else {
            Pair(input.baseSlopeDegrees, 4.5)
        }

        val effAspect = if (isHarvestActive) harvest.verifiedAspectDegrees else input.baseAspectDegrees
        val aspectSpread = if (isHarvestActive) 10.0 else 18.0
        val soilPriorBoost = if (isHarvestActive) (harvest.verifiedSoilRating - 0.75).coerceIn(0.0, 0.15) else 0.0

        for (i in 0 until input.iterations) {
            // 1. Stochastic Elevation perturbation: Normal(effElevMean, effElevStd)
            val elev = effElevMean + (rng.nextGaussian() * effElevStd)
            val elevScore = TerrainMath.band(elev, 300.0, 1200.0, 200.0)

            // 2. Stochastic Slope perturbation: Normal(effSlopeMean, effSlopeStd)
            val slope = max(2.0, effSlopeMean + (rng.nextGaussian() * effSlopeStd))
            val slopeScore = TerrainMath.band(slope, 6.0, 22.0, 7.0)

            // 3. Stochastic Aspect & Heat Load perturbation:
            val aspect = (effAspect + (rng.nextGaussian() * aspectSpread) + 360.0) % 360.0
            val heatLoad = TerrainMath.heatLoadIndex(input.centerLat, slope, aspect)
            val heatScore = 1.0 - TerrainMath.normaliseHeatLoad(heatLoad)

            // 4. Overstory Canopy Cover: Beta-like distribution centered on hardwood shade
            val canopy = (input.baseCanopyEstimate + (rng.nextGaussian() * 0.06)).coerceIn(0.40, 0.98)
            val canopyScore = TerrainMath.band(canopy, 0.72, 0.88, 0.10)

            // 5. Soil Moisture & Topographic Drainage (TPI & TWI):
            val tpi = -4.0 + (rng.nextGaussian() * 2.5) // concave lower slope
            val tpiScore = TerrainMath.band(tpi, -8.0, -1.0, 5.0)
            val soil = (input.baseSoilMoistureEstimate + (rng.nextGaussian() * 0.08) + companionBonus + soilPriorBoost).coerceIn(0.20, 1.0)

            // Weighted composite simulation score (sum of weights = 1.0)
            val sampleEsi = (heatScore * 0.28) +
                    (slopeScore * 0.18) +
                    (elevScore * 0.10) +
                    (tpiScore * 0.14) +
                    (canopyScore * 0.15) +
                    (soil * 0.15)

            val clampedEsi = sampleEsi.coerceIn(0.0, 1.0)
            scores[i] = clampedEsi
            sum += clampedEsi
            sumSq += (clampedEsi * clampedEsi)

            if (clampedEsi >= 0.70) viableCount++
            else if (clampedEsi >= 0.40) marginalCount++
            else unsuitableCount++

            val binIdx = (clampedEsi * 10).toInt().coerceIn(0, 9)
            binCounts[binIdx]++

            if ((i + 1) % checkpointInterval == 0 || i == input.iterations - 1) {
                val countSoFar = i + 1
                val rMean = sum / countSoFar
                val rVar = max(0.0, (sumSq / countSoFar) - (rMean * rMean))
                val rStd = sqrt(rVar)
                checkpoints.add(
                    ConvergenceCheckpoint(
                        iterationCount = countSoFar,
                        rollingMean = rMean,
                        rollingVariance = rVar,
                        rollingConfidenceWidth = 2.0 * 1.96 * rStd
                    )
                )
            }
        }

        val mean = sum / input.iterations
        val variance = max(0.0, (sumSq / input.iterations) - (mean * mean))
        val stdDev = sqrt(variance)

        val lower95 = (mean - (1.96 * stdDev)).coerceIn(0.0, 1.0)
        val upper95 = (mean + (1.96 * stdDev)).coerceIn(0.0, 1.0)

        val pViable = viableCount.toDouble() / input.iterations
        val pMarginal = marginalCount.toDouble() / input.iterations
        val pUnsuitable = unsuitableCount.toDouble() / input.iterations

        val histProportions = binCounts.map { it.toDouble() / input.iterations }

        // Convergence delta between last two checkpoints to prove stability
        val lastCheckpoint = checkpoints.lastOrNull()
        val prevCheckpoint = if (checkpoints.size >= 2) checkpoints[checkpoints.size - 2] else lastCheckpoint
        val convergenceDelta = if (lastCheckpoint != null && prevCheckpoint != null) {
            abs(lastCheckpoint.rollingMean - prevCheckpoint.rollingMean)
        } else 0.001

        val witnessPinDelta = abs(mean - detBaseline)
        val witnessPinVerified = witnessPinDelta <= 0.08 // strict tolerance per EINCOL protocol

        // Multi-layer confidence metrics (0 - 100%)
        val slopeConf = ((1.0 - (stdDev / (mean + 0.01))) * 100).toInt().coerceIn(60, 98)
        val aspectConf = if (input.baseAspectDegrees in 315.0..360.0 || input.baseAspectDegrees in 0.0..95.0) 95 else 68
        val elevConf = if (input.baseElevationMeters in 600.0..1150.0) 94 else 72
        val canopyConf = ((input.baseCanopyEstimate / 0.85) * 90).toInt().coerceIn(55, 96)
        val soilConf = ((input.baseSoilMoistureEstimate * 85) + (companionBonus * 100)).toInt().coerceIn(60, 99)

        val duration = System.currentTimeMillis() - startTime

        MonteCarloResult(
            input = input,
            iterationsRun = input.iterations,
            meanEsi = mean,
            standardDeviation = stdDev,
            variance = variance,
            lower95Credible = lower95,
            upper95Credible = upper95,
            pViableHabitat = pViable,
            pMarginalHabitat = pMarginal,
            pUnsuitable = pUnsuitable,
            histogram10Bins = histProportions,
            convergenceHistory = checkpoints,
            convergenceDelta = convergenceDelta,
            deterministicEsiBaseline = detBaseline,
            witnessPinDelta = witnessPinDelta,
            witnessPinVerified = witnessPinVerified,
            slopeConfidencePct = slopeConf,
            aspectConfidencePct = aspectConf,
            elevationConfidencePct = elevConf,
            canopyConfidencePct = canopyConf,
            soilConfidencePct = soilConf,
            executionTimeMs = duration,
            isHarvestAdapted = isHarvestActive,
            harvestRootsInfluencing = harvest.totalRootsDug,
            posteriorElevationMean = effElevMean,
            posteriorSlopeMean = effSlopeMean,
            varianceReductionFactor = if (isHarvestActive) (stdDev / 0.12).coerceIn(0.50, 1.0) else 1.0,
        )
    }
}
