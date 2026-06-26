package com.hereliesaz.wavefrom.signal.source.sdr

import android.content.Context
import android.util.Log
import com.hereliesaz.wavefrom.signal.dsp.PowerSpectrum
import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.source.SignalSource
import com.mantz_it.hackrf_android.Hackrf
import com.mantz_it.hackrf_android.HackrfCallbackInterface
import com.mantz_it.hackrf_android.HackrfUsbException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * A HackRF One attached to the phone over USB-OTG. Unlike the RTL-SDR path
 * ([UsbSdrSource]) which offloads to the rtl_tcp_andro driver app, HackRF has no such
 * server, so we drive the device directly via the vendored `hackrf_android` library
 * (Dennis Mantz, GPL) — pure Java over Android's USB-host API, no root. A single
 * antenna can't resolve direction, so this emits no bearings: it streams a power
 * [SpectrumBus] spectrum for the waterfall and a decimated real IQ window
 * ([WaveformBus]) for the 3D helix viewer (the "⌁ HackRF" Live-IQ button).
 */
class HackRfSource(
    private val context: Context,
    private val centerFreqHz: Long = 433_000_000L,
    private val sampleRateHz: Int = 2_000_000,
    private val vgaGainDb: Int = 20,
    private val lnaGainDb: Int = 16,
    private val nfft: Int = 1024,
) : SignalSource {

    private val appContext = context.applicationContext

    override val sourceType = SourceType.EXTERNAL_SDR

    /** True when a HackRF is plugged in over OTG (RTL dongles go to [UsbSdrSource]). */
    override fun isAvailable(): Boolean = UsbDeviceCatalog.attachedHackRfs(appContext).isNotEmpty()

    // Explicit type arg: the builder emits no Detection (single antenna = no bearing).
    override fun detections(): Flow<Detection> = flow<Detection> {
        if (!isAvailable()) return@flow
        val hackrf = openHackrf() ?: return@flow
        try {
            hackrf.setSampleRate(sampleRateHz, 1)
            hackrf.setFrequency(centerFreqHz)
            hackrf.setRxLNAGain(lnaGainDb)
            hackrf.setRxVGAGain(vgaGainDb)
            hackrf.setAmp(false)
            val queue = hackrf.startRX()
            captureLoop(hackrf, queue)
        } catch (e: HackrfUsbException) {
            // Driver app missing / device unplugged / permission denied — fail soft.
            Log.w(TAG, "HackRF capture ended", e)
        } finally {
            runCatching { hackrf.stop() }
        }
        // Intentionally emits no Detection — single antenna has no bearing.
    }.flowOn(Dispatchers.IO)

    /** Bridge the async, permission-gated initHackrf callback into a suspend point. */
    private suspend fun openHackrf(): Hackrf? = suspendCancellableCoroutine { cont ->
        val started = Hackrf.initHackrf(
            appContext,
            object : HackrfCallbackInterface {
                override fun onHackrfReady(hackrf: Hackrf) {
                    if (cont.isActive) cont.resume(hackrf)
                }

                override fun onHackrfError(message: String?) {
                    Log.w(TAG, "HackRF init error: $message")
                    if (cont.isActive) cont.resume(null)
                }
            },
            QUEUE_SIZE,
        )
        if (!started && cont.isActive) cont.resume(null)
    }

    private suspend fun captureLoop(hackrf: Hackrf, queue: ArrayBlockingQueue<ByteArray>) {
        val re = FloatArray(nfft)
        val im = FloatArray(nfft)
        while (currentCoroutineContext().isActive) {
            val buf = queue.poll(1, TimeUnit.SECONDS) ?: continue
            try {
                if (HackRfIq.sampleCount(buf) >= nfft) {
                    HackRfIq.toFloats(buf, nfft, re, im)
                    val powers = PowerSpectrum.computeDb(re.copyOf(), im.copyOf())
                    val now = System.currentTimeMillis()
                    SpectrumBus.publish(
                        SdrMessage.Spectrum(
                            startHz = centerFreqHz - sampleRateHz / 2,
                            binHz = (sampleRateHz / nfft).toLong(),
                            powersDbm = powers,
                            timestampMs = now,
                        ),
                    )
                    WaveformBus.publish(
                        IqFrame(
                            sourceId = SELF_SOURCE_ID,
                            label = "HackRF",
                            frequencyHz = centerFreqHz,
                            window = HackRfIq.decimate(re, im, WAVEFORM_SAMPLES),
                            timestampMs = now,
                        ),
                    )
                }
            } finally {
                // Always recycle the buffer so the driver's pool doesn't starve.
                hackrf.returnBufferToBufferPool(buf)
            }
        }
    }

    private companion object {
        const val TAG = "HackRfSource"

        /** Stable id used to match this single-antenna source's helix in the viewer. */
        const val SELF_SOURCE_ID = "usb-hackrf"

        /** Helix sample count — enough to read the modulation, cheap to draw. */
        const val WAVEFORM_SAMPLES = 256

        /** Receive-queue depth (driver buffers of packetSize bytes each). */
        const val QUEUE_SIZE = 16
    }
}
