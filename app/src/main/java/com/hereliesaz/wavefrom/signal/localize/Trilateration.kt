package com.hereliesaz.wavefrom.signal.localize

import com.hereliesaz.wavefrom.signal.model.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

/** One range observation: the device was at [anchor] and measured [rangeM] to the emitter. */
data class RangeSample(val anchor: Vec3, val rangeM: Float, val weight: Float = 1f)

/** Result of a solve: estimated emitter position and a fit quality in [0,1]. */
data class Localization(val position: Vec3, val confidence: Float, val residualM: Float)

/**
 * Least-squares trilateration: given range observations taken from several known
 * positions (a synthetic aperture formed by the moving device), estimate the
 * emitter's 3D position. Linearizes the sphere equations against a reference
 * sample and solves the 3x3 normal equations, then reports a confidence from the
 * residual and the geometric spread of the anchors.
 *
 * Pure math, no Android types — fully unit-testable.
 */
object Trilateration {

    /** Minimum aperture (max anchor separation) before a solve is meaningful, metres. */
    const val MIN_BASELINE_M = 0.5f

    fun solve(samples: List<RangeSample>): Localization? {
        if (samples.size < 4) return null
        val baseline = baseline(samples)
        if (baseline < MIN_BASELINE_M) return null

        // Reference equation (use the sample with the strongest weight as anchor 0).
        val ref = samples.maxByOrNull { it.weight } ?: samples.first()
        val a0 = ref.anchor
        val r0 = ref.rangeM

        // Build A p = b by subtracting the reference sphere from each other sphere:
        //   2 (a_i - a_0) . p = (r0^2 - ri^2) - (|a0|^2 - |ai|^2)
        val rows = ArrayList<FloatArray>(samples.size)
        val rhs = ArrayList<Float>(samples.size)
        val weights = ArrayList<Float>(samples.size)
        for (s in samples) {
            if (s === ref) continue
            val d = s.anchor - a0
            rows.add(floatArrayOf(2f * d.x, 2f * d.y, 2f * d.z))
            val bi = (r0 * r0 - s.rangeM * s.rangeM) -
                (a0.dot(a0) - s.anchor.dot(s.anchor))
            rhs.add(bi)
            weights.add(s.weight)
        }

        // Normal equations: (A^T A) p = A^T b.
        val ata = Array(3) { FloatArray(3) }
        val atb = FloatArray(3)
        for (i in rows.indices) {
            val row = rows[i]
            val w = weights[i]
            for (r in 0..2) {
                atb[r] += w * row[r] * rhs[i]
                for (c in 0..2) ata[r][c] += w * row[r] * row[c]
            }
        }

        val p = solve3x3(ata, atb) ?: solvePlanar(rows, rhs, weights, a0) ?: return null
        val pos = Vec3(p[0], p[1], p[2])

        val residual = rmsResidual(samples, pos)
        val confidence = confidence(residual, baseline)
        return Localization(pos, confidence, residual)
    }

    private fun baseline(samples: List<RangeSample>): Float {
        var max = 0f
        for (i in samples.indices) for (j in i + 1 until samples.size) {
            val d = samples[i].anchor.distanceTo(samples[j].anchor)
            if (d > max) max = d
        }
        return max
    }

    private fun rmsResidual(samples: List<RangeSample>, pos: Vec3): Float {
        var sum = 0f
        for (s in samples) {
            val e = pos.distanceTo(s.anchor) - s.rangeM
            sum += e * e
        }
        return sqrt(sum / samples.size)
    }

    /** Small residual + wide aperture => high confidence. */
    private fun confidence(residualM: Float, baselineM: Float): Float {
        val resScore = 1f / (1f + residualM)                 // 0..1, 1 at perfect fit
        val geoScore = (baselineM / 5f).coerceIn(0f, 1f)     // saturates at 5 m aperture
        return (resScore * geoScore).coerceIn(0f, 1f)
    }

    /** Solve a 3x3 system by Gaussian elimination; null if (near-)singular. */
    fun solve3x3(a: Array<FloatArray>, b: FloatArray): FloatArray? {
        val m = Array(3) { r -> floatArrayOf(a[r][0], a[r][1], a[r][2], b[r]) }
        for (col in 0..2) {
            var pivot = col
            for (r in col + 1..2) if (abs(m[r][col]) > abs(m[pivot][col])) pivot = r
            if (abs(m[pivot][col]) < 1e-6f) return null
            val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
            val pv = m[col][col]
            for (c in col..3) m[col][c] /= pv
            for (r in 0..2) if (r != col) {
                val f = m[r][col]
                for (c in col..3) m[r][c] -= f * m[col][c]
            }
        }
        return floatArrayOf(m[0][3], m[1][3], m[2][3])
    }

    /**
     * Fallback when anchors are coplanar (e.g. the phone moved in a horizontal
     * plane): solve x,y in 2D and fix z to the anchor mean height.
     */
    private fun solvePlanar(
        rows: List<FloatArray>,
        rhs: List<Float>,
        weights: List<Float>,
        a0: Vec3,
    ): FloatArray? {
        val ata = Array(2) { FloatArray(2) }
        val atb = FloatArray(2)
        for (i in rows.indices) {
            val row = rows[i]
            val w = weights[i]
            for (r in 0..1) {
                atb[r] += w * row[r] * rhs[i]
                for (c in 0..1) ata[r][c] += w * row[r] * row[c]
            }
        }
        val det = ata[0][0] * ata[1][1] - ata[0][1] * ata[1][0]
        if (abs(det) < 1e-6f) return null
        val x = (atb[0] * ata[1][1] - atb[1] * ata[0][1]) / det
        val y = (ata[0][0] * atb[1] - ata[1][0] * atb[0]) / det
        return floatArrayOf(x, y, a0.z)
    }
}
