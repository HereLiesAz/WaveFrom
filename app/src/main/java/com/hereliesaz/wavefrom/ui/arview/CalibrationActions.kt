package com.hereliesaz.wavefrom.ui.arview

import androidx.annotation.VisibleForTesting
import com.hereliesaz.wavefrom.ar.frame.FrameMath
import com.hereliesaz.wavefrom.ar.sensor.ScreenProjection
import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.model.Track
import kotlin.math.abs

/**
 * Pure logic behind the one-tap "Align to crosshair" control, extracted from
 * [HudControls] so the track selection and offset solve can be unit-tested without
 * Compose or hardware. The actual [com.hereliesaz.wavefrom.ar.frame.CalibrationConfig]
 * write stays in the Composable; these functions only compute values.
 */
internal object CalibrationActions {

    /**
     * The external-SDR track whose calibrated bearing is nearest the crosshair
     * (current true [headingTrue]), paired with its raw array azimuth. Null if none
     * is on screen.
     */
    @VisibleForTesting
    internal fun centeredSdrTrack(
        tracks: List<Track>,
        headingTrue: Float,
        arrayOffsetDeg: Float,
    ): Pair<Track, Float>? =
        tracks.asSequence()
            .filter { it.sourceType == SourceType.EXTERNAL_SDR }
            .mapNotNull { t -> (t.direction as? Direction.TrueBearing)?.let { t to it.azimuthDeg } }
            .minByOrNull { (_, rawAz) ->
                abs(ScreenProjection.normalizeDeg(FrameMath.sdrArrayToTrue(rawAz, arrayOffsetDeg) - headingTrue))
            }

    /**
     * The array offset (degrees) that pins [centered]'s emitter to the crosshair given
     * the current true [headingTrue], or null when nothing is centred. Pure — does not
     * mutate global calibration state.
     */
    @VisibleForTesting
    internal fun solveAlignOffset(centered: Pair<Track, Float>?, headingTrue: Float): Float? =
        centered?.let { (_, rawAz) -> FrameMath.solveArrayOffset(rawAz, headingTrue) }
}
