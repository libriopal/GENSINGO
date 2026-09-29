package com.ginsengo.steward.learn

import com.ginsengo.steward.prospect.Prospects
import kotlin.math.exp
import kotlin.random.Random

/**
 * Learns from the user's verified finds, and decides whether the learning earned its keep.
 *
 * THE MODEL. The terrain surface scores a place as a weighted sum of six factor values in
 * 0..1 (GinsengSuitability). The published weights are the PRIOR. Learning fits new weights
 * from verified finds (presences) against points sampled across the same radius
 * (background): an L2-regularised logistic regression whose penalty pulls toward the prior,
 * and pulls less as finds accumulate. Negative coefficients are clipped to zero and the
 * weights renormalised, so the learned surface is still a 0..1 weighted sum the heatmap can
 * draw with the same ramp.
 *
 * THE EVALUATOR, which is the part that matters. A learner judged on the finds it trained
 * on always "improves": add finds, fit them, admire the fit. So the learned weights are
 * adopted only when they rank HELD-OUT finds better than the prior does:
 *
 *  - Finds are grouped into spatial clusters by single linkage at the terrain's own
 *    correlation range (measured by the radius scan; never below [CLUSTER_M]). Holding out
 *    one find while a neighbour in the same autocorrelated ground stays in training is not
 *    holding anything out. (First version: a fixed 200 m. The Phase 7 independent critic
 *    objected that this leaks; LearnerLeakageTest measured it on real terrain.)
 *  - For each cluster: train without it, then score its finds against held-out background
 *    points the training never saw. A find's score is its percentile among them.
 *  - Gain = learned percentile minus prior percentile, averaged per cluster.
 *  - Adopt only if the mean gain is at least [MARGIN] AND an exact sign-flip test over
 *    clusters gives p < [ALPHA]. With fewer than 5 clusters the smallest attainable p is
 *    1/16, so adoption is impossible below [MIN_CLUSTERS] by arithmetic, not by a rule
 *    someone could loosen.
 *
 * Pure Kotlin; deterministic for a given seed.
 */
object FindLearner {

    const val FACTORS = 6
    const val MIN_FINDS = 5
    const val MIN_CLUSTERS = 5
    const val CLUSTER_M = 200.0
    const val MARGIN = 0.02
    const val ALPHA = 0.05

    /** One place described by its six factor values, in GinsengSuitability.Factor order. */
    class Sample(val factors: DoubleArray, val lat: Double, val lng: Double) {
        init { require(factors.size == FACTORS) }
    }

    data class Verdict(
        val adopted: Boolean,
        /** Weights to draw with: learned if adopted, otherwise the prior. */
        val active: DoubleArray,
        /** What the fit on ALL verified finds produced, shown even when not adopted. */
        val learned: DoubleArray?,
        val prior: DoubleArray,
        val findsUsed: Int,
        val clusters: Int,
        val heldOutAucPrior: Double?,
        val heldOutAucLearned: Double?,
        val meanGain: Double?,
        val pValue: Double?,
        val reason: String,
    )

    fun score(f: DoubleArray, w: DoubleArray): Double {
        var s = 0.0
        for (i in 0 until FACTORS) s += f[i] * w[i]
        return s
    }

    /**
     * Fits weights on [presence] vs [background]. Returns normalised, non-negative weights,
     * or the prior when no factor carries positive signal.
     */
    fun fit(presence: List<DoubleArray>, background: List<DoubleArray>, prior: DoubleArray): DoubleArray {
        require(presence.isNotEmpty() && background.isNotEmpty())
        val np = presence.size.toDouble()
        val nb = background.size.toDouble()
        val wPos = 1.0
        val wNeg = np / nb   // balance the classes
        val total = np * wPos + nb * wNeg

        // Scale of the prior direction, fitted first, so the penalty centre has the right
        // magnitude: pulling toward prior * 1.0 would pull toward a near-flat logit.
        var s = 1.0
        var b = 0.0
        repeat(400) {
            var gs = 0.0; var gb = 0.0
            for (x in presence) {
                val z = score(x, prior)
                val p = sigmoid(s * z + b)
                gs += wPos * (p - 1.0) * z; gb += wPos * (p - 1.0)
            }
            for (x in background) {
                val z = score(x, prior)
                val p = sigmoid(s * z + b)
                gs += wNeg * p * z; gb += wNeg * p
            }
            s -= 2.0 * gs / total
            b -= 2.0 * gb / total
        }
        s = s.coerceAtLeast(1.0)
        val centre = DoubleArray(FACTORS) { prior[it] * s }

        val lambda = LAMBDA0 / np
        val beta = centre.copyOf()
        val lr = 1.0
        repeat(1500) {
            val g = DoubleArray(FACTORS)
            var gb = 0.0
            for (x in presence) {
                val p = sigmoid(dot(beta, x) + b)
                val e = wPos * (p - 1.0)
                for (i in 0 until FACTORS) g[i] += e * x[i]
                gb += e
            }
            for (x in background) {
                val p = sigmoid(dot(beta, x) + b)
                val e = wNeg * p
                for (i in 0 until FACTORS) g[i] += e * x[i]
                gb += e
            }
            for (i in 0 until FACTORS) {
                beta[i] -= lr * (g[i] / total + 2.0 * lambda * (beta[i] - centre[i]))
            }
            b -= lr * gb / total
        }
        val clipped = DoubleArray(FACTORS) { beta[it].coerceAtLeast(0.0) }
        val sum = clipped.sum()
        return if (sum <= 1e-9) prior.copyOf() else DoubleArray(FACTORS) { clipped[it] / sum }
    }

    /** Single-linkage clusters of the finds, [CLUSTER_M] apart. Returns a cluster id per find. */
    fun clusters(finds: List<Sample>, linkM: Double = CLUSTER_M): IntArray {
        val id = IntArray(finds.size) { it }
        fun root(i: Int): Int { var r = i; while (id[r] != r) r = id[r]; return r }
        for (i in finds.indices) for (j in i + 1 until finds.size) {
            val d = Prospects.distanceMetres(finds[i].lat, finds[i].lng, finds[j].lat, finds[j].lng)
            if (d <= linkM) {
                val a = root(i); val c = root(j)
                if (a != c) id[maxOf(a, c)] = minOf(a, c)
            }
        }
        val roots = IntArray(finds.size) { root(it) }
        val remap = HashMap<Int, Int>()
        return IntArray(finds.size) { remap.getOrPut(roots[it]) { remap.size } }
    }

    /**
     * @param linkM   clusters are formed at this distance: the terrain's own correlation
     *                range from the radius scan, so a held-out find is not in the same
     *                autocorrelated ground as a training find ([CLUSTER_M] is the floor)
     * @param fitter  injectable only so a test can prove held-out finds never reach it
     */
    fun evaluate(
        finds: List<Sample>,
        background: List<Sample>,
        prior: DoubleArray,
        seed: Long = 7L,
        linkM: Double = CLUSTER_M,
        fitter: (List<DoubleArray>, List<DoubleArray>, DoubleArray) -> DoubleArray = ::fit,
    ): Verdict {
        fun notAdopted(reason: String, learned: DoubleArray? = null, clusters: Int = 0,
                       aucP: Double? = null, aucL: Double? = null, gain: Double? = null,
                       p: Double? = null) =
            Verdict(false, prior.copyOf(), learned, prior.copyOf(), finds.size, clusters,
                aucP, aucL, gain, p, reason)

        if (finds.size < MIN_FINDS) {
            return notAdopted("Learning starts at $MIN_FINDS verified finds in this area " +
                    "(have ${finds.size}). The map is using the published weights.")
        }
        if (background.size < 40) return notAdopted("Not enough terrain sampled around you yet.")

        val cl = clusters(finds, linkM.coerceAtLeast(CLUSTER_M))
        val nClusters = (cl.maxOrNull() ?: -1) + 1

        // Background split: training half and a held-out half the fits never see.
        val shuffled = background.shuffled(Random(seed))
        val bgTrain = shuffled.subList(0, shuffled.size / 2).map { it.factors }
        val bgTest = shuffled.subList(shuffled.size / 2, shuffled.size).map { it.factors }

        val learnedAll = fitter(finds.map { it.factors }, bgTrain, prior)

        if (nClusters < MIN_CLUSTERS) {
            return notAdopted(
                "Your ${finds.size} finds sit in $nClusters separate spot(s). Learning has to " +
                        "prove itself on ground it has not seen, which needs $MIN_CLUSTERS spots " +
                        "at least ${linkM.coerceAtLeast(CLUSTER_M).toInt()} m apart (how far this " +
                        "terrain has to be before it stops resembling itself).",
                learned = learnedAll, clusters = nClusters,
            )
        }

        val gains = DoubleArray(nClusters)
        var sumP = 0.0; var sumL = 0.0; var nHeld = 0
        for (c in 0 until nClusters) {
            val train = finds.filterIndexed { i, _ -> cl[i] != c }.map { it.factors }
            val held = finds.filterIndexed { i, _ -> cl[i] == c }
            val w = fitter(train, bgTrain, prior)
            var g = 0.0
            for (h in held) {
                val pp = percentile(score(h.factors, prior), bgTest, prior)
                val pl = percentile(score(h.factors, w), bgTest, w)
                sumP += pp; sumL += pl; nHeld++
                g += pl - pp
            }
            gains[c] = g / held.size
        }
        val (adopted, p) = decide(gains)
        val meanGain = gains.average()
        val aucP = sumP / nHeld
        val aucL = sumL / nHeld

        val reason = if (adopted) {
            "Learned weights rank held-out finds better: %.0f%% vs %.0f%% (p = %.3f, %d spots)."
                .format(aucL * 100, aucP * 100, p, nClusters)
        } else {
            "Learned weights did not beat the published ones on held-out finds " +
                    "(%.0f%% vs %.0f%%, p = %.2f). Keeping the published weights."
                        .format(aucL * 100, aucP * 100, p)
        }
        return Verdict(
            adopted = adopted,
            active = if (adopted) learnedAll else prior.copyOf(),
            learned = learnedAll,
            prior = prior.copyOf(),
            findsUsed = finds.size,
            clusters = nClusters,
            heldOutAucPrior = aucP,
            heldOutAucLearned = aucL,
            meanGain = meanGain,
            pValue = p,
            reason = reason,
        )
    }

    /**
     * The gate, pure: adopt only when the mean held-out gain clears [MARGIN] AND the gains
     * are consistent across clusters (exact sign-flip p < [ALPHA]). One lucky cluster can
     * carry the mean over the margin; it cannot carry the sign test.
     */
    fun decide(clusterGains: DoubleArray): Pair<Boolean, Double> {
        val p = signFlipP(clusterGains)
        return (clusterGains.average() >= MARGIN && p < ALPHA) to p
    }

    /** Fraction of [others] scoring below [s], ties counted half. This is a one-point AUC. */
    fun percentile(s: Double, others: List<DoubleArray>, w: DoubleArray): Double {
        var below = 0.0
        for (o in others) {
            val so = score(o, w)
            if (so < s) below += 1.0 else if (so == s) below += 0.5
        }
        return below / others.size
    }

    /**
     * One-sided exact sign-flip test: the share of the 2^n sign assignments whose mean is at
     * least the observed mean. Exact up to 20 clusters, Monte Carlo (fixed seed) beyond.
     */
    fun signFlipP(d: DoubleArray): Double {
        val n = d.size
        val obs = d.sum()
        if (n <= 20) {
            var hit = 0L
            val combos = 1L shl n
            for (mask in 0 until combos) {
                var s = 0.0
                for (i in 0 until n) s += if (mask and (1L shl i) != 0L) -d[i] else d[i]
                if (s >= obs - 1e-12) hit++
            }
            return hit.toDouble() / combos
        }
        val r = Random(11)
        var hit = 0
        val trials = 20_000
        repeat(trials) {
            var s = 0.0
            for (i in 0 until n) s += if (r.nextBoolean()) -d[i] else d[i]
            if (s >= obs - 1e-12) hit++
        }
        return (hit + 1.0) / (trials + 1.0)
    }

    private const val LAMBDA0 = 2.0

    private fun dot(a: DoubleArray, b: DoubleArray): Double {
        var s = 0.0
        for (i in 0 until FACTORS) s += a[i] * b[i]
        return s
    }

    private fun sigmoid(z: Double): Double =
        if (z >= 0) 1.0 / (1.0 + exp(-z)) else exp(z).let { it / (1.0 + it) }
}
