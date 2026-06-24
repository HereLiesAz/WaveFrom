package com.hereliesaz.wavefrom.signal.source.sdr

import android.util.Log
import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.source.SignalSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/**
 * Receives bearings from an external phased-array SDR or the Raspberry Pi
 * companion pod over UDP (newline-delimited JSON, see [WireProtocol]). Bearings
 * arrive as true direction-of-arrival, so these become [com.hereliesaz.wavefrom.signal.model.Direction.TrueBearing]
 * detections — the highest localization tier.
 *
 * Functional but not wired into the Phase 1 default source set; activate by
 * registering it with a [port] once a pod is on the network.
 */
class NetworkSdrSource(private val port: Int = DEFAULT_PORT) : SignalSource {

    override val sourceType = SourceType.EXTERNAL_SDR

    /** Availability is decided at connect time; assume reachable. */
    override fun isAvailable(): Boolean = true

    override fun detections(): Flow<Detection> = callbackFlow {
        val socket = try {
            // reuseAddress avoids "Address already in use" if the flow is
            // cancelled and restarted while the port is still in TIME_WAIT.
            DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not bind UDP $port", e)
            close(e); return@callbackFlow
        }

        val pump = launch {
            val buf = ByteArray(64 * 1024)
            while (isActive) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    // Blocks until data or socket close (awaitClose closes it,
                    // which interrupts receive immediately — no poll timeout needed).
                    socket.receive(packet)
                } catch (e: Exception) {
                    break
                }
                val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                for (line in text.lineSequence()) {
                    if (line.isBlank()) continue
                    when (val msg = WireProtocol.decode(line)) {
                        is SdrMessage.Bearing -> trySend(WireProtocol.toDetection(msg))
                        is SdrMessage.Spectrum -> SpectrumBus.publish(msg)
                        else -> {} // heartbeat / unknown: ignore
                    }
                }
            }
        }

        awaitClose {
            pump.cancel()
            runCatching { socket.close() }
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val TAG = "NetworkSdrSource"
        const val DEFAULT_PORT = 50505
    }
}
