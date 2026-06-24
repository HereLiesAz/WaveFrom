package com.hereliesaz.wavefrom.signal.source.sdr

import android.content.Context
import android.util.Log
import com.hereliesaz.wavefrom.signal.dsp.PowerSpectrum
import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.source.SignalSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * An RTL-SDR attached to the phone over USB-OTG. Android has no kernel driver for
 * RTL-SDR, so this reuses the no-root `rtl_tcp_andro` driver (F-Droid
 * `marto.rtl_tcp_andro`): the user launches it (it claims the USB device and
 * serves IQ on a local `rtl_tcp` port), and WaveFrom connects as an rtl_tcp
 * client. A single antenna can't resolve direction, so this produces no
 * bearings — it streams a power [SpectrumBus.publish] spectrum that the waterfall
 * renders. Coherent DoA stays on the KrakenSDR/Pi path.
 */
class UsbSdrSource(
    private val context: Context,
    private val host: String = "127.0.0.1",
    private val port: Int = 1234,
    private val centerFreqHz: Int = 100_000_000,
    private val sampleRateHz: Int = 2_048_000,
    private val nfft: Int = 1024,
) : SignalSource {

    override val sourceType = SourceType.EXTERNAL_SDR

    /** True when a recognized USB SDR is plugged in over OTG. */
    override fun isAvailable(): Boolean = UsbDeviceCatalog.attachedSdrs(context).isNotEmpty()

    override fun detections(): Flow<Detection> = flow {
        if (!isAvailable()) return@flow
        val client = RtlTcpClient(host, port)
        try {
            client.connect()
            client.setSampleRateHz(sampleRateHz)
            client.setCenterFreqHz(centerFreqHz)
            client.setGainModeManual(false)
            val re = FloatArray(nfft)
            val im = FloatArray(nfft)
            while (currentCoroutineContext().isActive) {
                client.readSamples(nfft, re, im)
                val powers = PowerSpectrum.computeDb(re.copyOf(), im.copyOf())
                SpectrumBus.publish(
                    SdrMessage.Spectrum(
                        startHz = (centerFreqHz - sampleRateHz / 2).toLong(),
                        binHz = (sampleRateHz / nfft).toLong(),
                        powersDbm = powers,
                        timestampMs = System.currentTimeMillis(),
                    ),
                )
            }
        } catch (e: Exception) {
            // No driver running / device unplugged — fail soft (no spectrum).
            Log.w(TAG, "rtl_tcp capture ended", e)
        } finally {
            client.close()
        }
        // Intentionally emits no Detection — single antenna has no bearing.
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val TAG = "UsbSdrSource"
    }
}
