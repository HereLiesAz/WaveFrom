package com.hereliesaz.wavefrom.signal.record

import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.source.SignalSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/** A loaded recording ready to replay: its detections plus playback options. */
data class ReplaySession(
    val records: List<TimedDetection>,
    val speed: Float = 1f,
    val loop: Boolean = true,
    val label: String = "recording",
)

/**
 * Singleton control surface for replay, mirroring the project's bus pattern
 * (SpectrumBus/WaveformBus): the UI loads a recording and calls [play]; the
 * always-present [ReplaySource] reacts. [stop] returns to live capture.
 */
object ReplayController {
    private val _active = MutableStateFlow<ReplaySession?>(null)
    val active: StateFlow<ReplaySession?> = _active.asStateFlow()
    val isReplaying: Boolean get() = _active.value != null

    fun play(session: ReplaySession) { _active.value = session }
    fun stop() { _active.value = null }
}

/**
 * A [SignalSource] that re-emits a recorded detection stream, honoring the original
 * inter-arrival timing (scaled by [ReplaySession.speed]) so the overlay/localization
 * play back exactly as captured. Idle (emits nothing) until [ReplayController] holds
 * a session, so it is safe to keep in the default source set permanently.
 *
 * Replayed detections are re-stamped to the current time on emit, otherwise the
 * aggregator would expire them instantly as stale.
 */
class ReplaySource(
    private val sessions: StateFlow<ReplaySession?> = ReplayController.active,
    private val now: () -> Long = System::currentTimeMillis,
) : SignalSource {

    // The replayed detections carry their own original source types; this is just
    // the replay *channel's* label, so SIMULATED keeps it out of the enum churn.
    override val sourceType = SourceType.SIMULATED
    override fun isAvailable() = true

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun detections(): Flow<Detection> = sessions.flatMapLatest { s ->
        if (s == null) emptyFlow() else replay(s.records, s.speed, s.loop, now)
    }

    companion object {
        /**
         * Pure replay flow: emit each record after its scaled inter-arrival delay,
         * re-stamped to "now". Looping restarts from the top. Exposed for tests
         * (drive it with virtual time).
         */
        fun replay(
            records: List<TimedDetection>,
            speed: Float = 1f,
            loop: Boolean = false,
            now: () -> Long = System::currentTimeMillis,
        ): Flow<Detection> = flow {
            if (records.isEmpty()) return@flow
            val s = if (speed <= 0f) 1f else speed
            do {
                var prev = 0L
                for (r in records) {
                    val dt = ((r.offsetMs - prev) / s).toLong().coerceAtLeast(0L)
                    if (dt > 0L) delay(dt)
                    emit(r.detection.copy(timestampMs = now()))
                    prev = r.offsetMs
                }
            } while (loop)
        }
    }
}
