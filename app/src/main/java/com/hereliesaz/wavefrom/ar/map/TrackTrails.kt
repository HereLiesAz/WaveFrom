package com.hereliesaz.wavefrom.ar.map

/**
 * Bounded per-track breadcrumb history of located positions, in metric (east,
 * north) metres relative to the user — scale-independent, so the map can re-project
 * the whole trail at any zoom. Oldest points and least-recently-updated tracks are
 * evicted so a long session can't grow without limit.
 *
 * Not thread-safe; drive it from the UI/update thread.
 */
class TrackTrails(
    private val capacityPerTrack: Int = 48,
    private val maxTracks: Int = 64,
) {
    private val trails = LinkedHashMap<String, ArrayDeque<RadarPoint>>()

    /** Append a located position for [trackId], de-duplicating tiny jitter. */
    fun add(trackId: String, east: Float, north: Float) {
        if (!trails.containsKey(trackId) && trails.size >= maxTracks) {
            trails.keys.firstOrNull()?.let { trails.remove(it) } // evict LRU-ish
        }
        // Re-insert to mark most-recently-updated (LinkedHashMap keeps insertion order).
        val buf = trails.remove(trackId) ?: ArrayDeque()
        val last = buf.lastOrNull()
        if (last == null || dist2(last.x, last.y, east, north) > MIN_STEP_M2) {
            buf.addLast(RadarPoint(east, north))
            while (buf.size > capacityPerTrack) buf.removeFirst()
        }
        trails[trackId] = buf
    }

    fun trail(trackId: String): List<RadarPoint> = trails[trackId]?.toList() ?: emptyList()

    fun clear() = trails.clear()

    private fun dist2(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx; val dy = ay - by
        return dx * dx + dy * dy
    }

    private companion object {
        const val MIN_STEP_M2 = 0.25f // 0.5 m minimum spacing between breadcrumbs
    }
}
