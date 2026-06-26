package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.ar.map.TrackTrails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackTrailsTest {

    @Test
    fun accumulatesBreadcrumbsInOrder() {
        val trails = TrackTrails()
        trails.add("a", 0f, 0f)
        trails.add("a", 0f, 2f)
        trails.add("a", 0f, 4f)
        val t = trails.trail("a")
        assertEquals(3, t.size)
        assertEquals(0f, t.first().y, 1e-3f)
        assertEquals(4f, t.last().y, 1e-3f)
    }

    @Test
    fun dropsSubMetreJitter() {
        val trails = TrackTrails()
        trails.add("a", 0f, 0f)
        trails.add("a", 0.1f, 0.1f) // within 0.5 m → ignored
        assertEquals(1, trails.trail("a").size)
    }

    @Test
    fun boundsPointsPerTrack() {
        val trails = TrackTrails(capacityPerTrack = 3)
        for (i in 0..9) trails.add("a", 0f, i.toFloat())
        val t = trails.trail("a")
        assertEquals(3, t.size)
        assertEquals(9f, t.last().y, 1e-3f) // newest kept
    }

    @Test
    fun evictsLeastRecentlyUpdatedTrack() {
        val trails = TrackTrails(maxTracks = 2)
        trails.add("a", 0f, 0f)
        trails.add("b", 0f, 0f)
        trails.add("a", 0f, 2f) // touches "a", so "b" is now oldest
        trails.add("c", 0f, 0f) // evicts "b"
        assertTrue(trails.trail("a").isNotEmpty())
        assertTrue(trails.trail("c").isNotEmpty())
        assertFalse(trails.trail("b").isNotEmpty())
    }
}
