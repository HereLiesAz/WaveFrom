package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.model.Identity
import com.hereliesaz.wavefrom.signal.model.SignalBand
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.record.ReplaySource
import com.hereliesaz.wavefrom.signal.record.TimedDetection
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplaySourceTest {

    private fun det(key: String) = Detection(
        sourceType = SourceType.WIFI,
        band = SignalBand.WIFI_5,
        frequencyHz = null,
        powerDbm = -60f,
        direction = Direction.RssiOnly(estimatedDistanceM = 5f, confidence = 0.5f),
        identity = Identity(key = key, label = key),
        timestampMs = 0L,
    )

    @Test
    fun replayHonorsInterArrivalTiming() = runTest {
        val records = listOf(
            TimedDetection(0L, det("a")),
            TimedDetection(100L, det("b")),
            TimedDetection(250L, det("c")),
        )
        val sched = testScheduler
        val emitted = mutableListOf<Pair<Long, String>>()
        val job = launch {
            ReplaySource.replay(records, now = { 0L }).collect {
                emitted += sched.currentTime to it.identity.key
            }
        }
        job.join()
        assertEquals(listOf(0L to "a", 100L to "b", 250L to "c"), emitted)
    }

    @Test
    fun speedScalesDelays() = runTest {
        val records = listOf(TimedDetection(0L, det("a")), TimedDetection(200L, det("b")))
        val sched = testScheduler
        var lastTime = -1L
        val job = launch {
            ReplaySource.replay(records, speed = 2f, now = { 0L }).collect { lastTime = sched.currentTime }
        }
        job.join()
        assertEquals(100L, lastTime) // 200ms / 2x
    }

    @Test
    fun loopRestartsFromTheTop() = runTest {
        val records = (0..2).map { TimedDetection(it * 100L, det("k$it")) }
        val keys = ReplaySource.replay(records, loop = true, now = { 0L })
            .take(4).toList().map { it.identity.key }
        assertEquals(listOf("k0", "k1", "k2", "k0"), keys)
    }

    @Test
    fun detectionsAreReStampedToNow() = runTest {
        val records = listOf(TimedDetection(0L, det("a")))
        val out = ReplaySource.replay(records, now = { 999L }).take(1).toList()
        assertEquals(999L, out.single().timestampMs)
    }
}
