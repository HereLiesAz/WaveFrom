package com.hereliesaz.wavefrom.signal.model

import kotlin.math.sqrt

/**
 * A point or vector in the AR world frame (metres). Used for motion-aided
 * localization where an emitter is resolved to an actual 3D position.
 */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)

    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z
    fun distanceTo(o: Vec3): Float = (this - o).length()

    /** Cross product (right-handed). */
    fun cross(o: Vec3): Vec3 = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x,
    )

    /** Unit vector in the same direction; returns [ZERO] for a zero-length vector. */
    fun normalize(): Vec3 {
        val len = length()
        return if (len < 1e-6f) ZERO else this * (1f / len)
    }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
    }
}
