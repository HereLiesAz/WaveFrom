package com.hereliesaz.wavefrom

import android.hardware.SensorManager
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hereliesaz.wavefrom.ar.frame.BearingFrame
import com.hereliesaz.wavefrom.ar.frame.CalibrationConfig
import com.hereliesaz.wavefrom.ar.sensor.DeviceOrientation
import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.model.Identity
import com.hereliesaz.wavefrom.signal.model.SignalBand
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.model.Track
import com.hereliesaz.wavefrom.ui.arview.CalibrationPanel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Drives the real [CalibrationPanel] Compose UI under Robolectric (JVM, no emulator) to
 * verify the wiring the pure tests can't: tapping "Align" writes the solved offset, the
 * compass warning shows/hides on accuracy, and the button disables with no centred track.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HudControlsComposeTest {

    @get:Rule val rule = createComposeRule()

    @Before fun reset() = CalibrationConfig.reset()

    private fun sdrTrack(azimuthDeg: Float): Track = Track(
        id = "sdr",
        sourceType = SourceType.EXTERNAL_SDR,
        band = SignalBand.UNKNOWN,
        frequencyHz = null,
        identity = Identity(key = "sdr", label = "FPV"),
        smoothedPowerDbm = -50f,
        direction = Direction.TrueBearing(azimuthDeg, elevationDeg = null, confidence = 0.9f),
        firstSeenMs = 0L,
        lastSeenMs = 0L,
        hitCount = 1,
    )

    private fun orientation(accuracy: Int, azimuthDeg: Float = 100f) =
        DeviceOrientation(azimuthDeg = azimuthDeg, pitchDeg = 0f, rollDeg = 0f, accuracy = accuracy)

    @Test
    fun tappingAlignWritesSolvedOffset() {
        rule.setContent {
            CalibrationPanel(
                orientation = orientation(SensorManager.SENSOR_STATUS_ACCURACY_HIGH, azimuthDeg = 100f),
                tracks = listOf(sdrTrack(azimuthDeg = 80f)),
                headingFrame = BearingFrame.TRUE_NORTH,
            )
        }
        rule.onNodeWithText("Align", substring = true).performClick()
        // heading 100° true, raw azimuth 80° → offset that pins it = 20°.
        assertEquals(20f, CalibrationConfig.sdrArrayOffsetDeg, 0.5f)
    }

    @Test
    fun compassWarningShowsOnlyWhenUnreliable() {
        rule.setContent {
            CalibrationPanel(
                orientation = orientation(SensorManager.SENSOR_STATUS_ACCURACY_LOW),
                tracks = emptyList(),
                headingFrame = BearingFrame.TRUE_NORTH,
            )
        }
        rule.onNodeWithText("Compass unreliable", substring = true).assertExists()
    }

    @Test
    fun compassWarningHiddenWhenAccurate() {
        rule.setContent {
            CalibrationPanel(
                orientation = orientation(SensorManager.SENSOR_STATUS_ACCURACY_HIGH),
                tracks = emptyList(),
                headingFrame = BearingFrame.TRUE_NORTH,
            )
        }
        rule.onNodeWithText("Compass unreliable", substring = true).assertDoesNotExist()
    }

    @Test
    fun alignDisabledWithNoCenteredTrack() {
        rule.setContent {
            CalibrationPanel(
                orientation = orientation(SensorManager.SENSOR_STATUS_ACCURACY_HIGH),
                tracks = emptyList(),
                headingFrame = BearingFrame.TRUE_NORTH,
            )
        }
        rule.onNodeWithText("center a marker", substring = true).assertIsNotEnabled()
    }
}
