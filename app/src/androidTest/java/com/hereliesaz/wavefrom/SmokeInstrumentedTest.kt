package com.hereliesaz.wavefrom

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hereliesaz.wavefrom.ar.frame.BearingFrame
import com.hereliesaz.wavefrom.ar.sensor.DeviceOrientation
import com.hereliesaz.wavefrom.ui.arview.CalibrationPanel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emulator smoke test: render the real [CalibrationPanel] on an actual Android/Compose
 * runtime with injected state only (no ARCore, sensors, or telephony — all absent on the
 * emulator). The detailed assertions live in the JVM/Robolectric suite; this just proves
 * the panel composes and lays out on a device.
 */
@RunWith(AndroidJUnit4::class)
class SmokeInstrumentedTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun calibrationPanelRenders() {
        rule.setContent {
            CalibrationPanel(
                orientation = DeviceOrientation(azimuthDeg = 0f, pitchDeg = 0f, rollDeg = 0f),
                tracks = emptyList(),
                headingFrame = BearingFrame.TRUE_NORTH,
            )
        }
        rule.onNodeWithText("Align", substring = true).assertExists()
    }
}
