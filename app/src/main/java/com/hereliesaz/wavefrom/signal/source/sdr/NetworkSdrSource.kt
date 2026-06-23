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
            DatagramSocket(port)
        } catch (e: Exception) {
            Log.w(TAG, "Could not bind UDP $port", e)
            close(e); return@callbackFlow
        }
        socket.soTimeout = SOCKET_TIMEOUT_MS

        val pump = launch {
            val buf = ByteArray(64 * 1024)
            while (isActive) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                } catch (e: Exception) {
                    if (isActive) continue else break
                }
                val text = String(packet.data, packet.offset, packet.length)
                for (line in text.lineSequence()) {
                    if (line.isBlank()) continue
                    val msg = WireProtocol.decode(line) ?: continue
                    if (msg is SdrMessage.Bearing) trySend(WireProtocol.toDetection(msg))
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
        private const val SOCKET_TIMEOUT_MS = 1_000
    }
}
