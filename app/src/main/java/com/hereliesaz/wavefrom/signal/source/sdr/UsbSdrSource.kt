package com.hereliesaz.wavefrom.signal.source.sdr

import android.content.Context
import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.source.SignalSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Phase 4 stub: an SDR (RTL-SDR / HackRF) attached directly to the phone over
 * USB-OTG via [android.hardware.usb.UsbManager]. The phone stays a display
 * client — the dongle/companion does the DSP — so decoded output reuses
 * [WireProtocol]/[SdrMessage] exactly like [NetworkSdrSource].
 *
 * Not yet implemented; reserved so the architecture and source registry already
 * account for it. See the `companion/` Raspberry Pi pod for the off-phone path
 * that sidesteps Android USB driver gaps entirely.
 */
class UsbSdrSource(@Suppress("unused") private val context: Context) : SignalSource {
    override val sourceType = SourceType.EXTERNAL_SDR
    override fun isAvailable(): Boolean = false // TODO(phase4): enumerate USB SDRs
    override fun detections(): Flow<Detection> = emptyFlow()
}
