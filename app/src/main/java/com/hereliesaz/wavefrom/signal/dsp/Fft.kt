package com.hereliesaz.wavefrom.signal.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * In-place iterative radix-2 Cooley–Tukey FFT. Operates on parallel real/
 * imaginary arrays whose length must be a power of two. Pure Kotlin, no Android
 * types — unit-testable on the JVM.
 */
object Fft {

    fun transform(re: FloatArray, im: FloatArray) {
        val n = re.size
        require(n > 0 && (n and (n - 1)) == 0) { "size must be a power of two, was $n" }
        require(im.size == n) { "re/im length mismatch" }

        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curR = 1f
                var curI = 0f
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = a + len / 2
                    val tr = curR * re[b] - curI * im[b]
                    val ti = curR * im[b] + curI * re[b]
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                    val nextR = curR * wr - curI * wi
                    curI = curR * wi + curI * wr
                    curR = nextR
                }
                i += len
            }
            len = len shl 1
        }
    }
}
