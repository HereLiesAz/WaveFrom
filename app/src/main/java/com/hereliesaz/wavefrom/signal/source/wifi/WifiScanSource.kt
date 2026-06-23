package com.hereliesaz.wavefrom.signal.source.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.util.Log
import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.model.Identity
import com.hereliesaz.wavefrom.signal.model.SignalBand
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.physics.PathLoss
import com.hereliesaz.wavefrom.signal.source.SignalSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Discovers nearby Wi-Fi access points via [WifiManager]. Each AP is an
 * [Direction.RssiOnly] detection — a single phone antenna cannot resolve
 * bearing, only strength. Note Android throttles `startScan()` to a handful of
 * calls per couple of minutes (API 28+), so results are bursty.
 */
class WifiScanSource(private val context: Context) : SignalSource {

    override val sourceType = SourceType.WIFI

    private val wifi: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    override fun isAvailable(): Boolean = wifi != null

    override fun detections(): Flow<Detection> = callbackFlow {
        val manager = wifi ?: run { close(); return@callbackFlow }

        fun emitResults() {
            val results = try {
                manager.scanResults
            } catch (e: SecurityException) {
                Log.w(TAG, "Missing permission for Wi-Fi scan results", e)
                emptyList()
            }
            val now = System.currentTimeMillis()
            for (r in results) {
                val band = SignalBand.fromWifiFrequencyMhz(r.frequency)
                val ssid = r.SSID?.takeIf { it.isNotBlank() } ?: "<hidden>"
                trySend(
                    Detection(
                        sourceType = SourceType.WIFI,
                        band = band,
                        frequencyHz = r.frequency.toLong() * 1_000_000L,
                        powerDbm = r.level.toFloat(),
                        direction = Direction.RssiOnly(
                            estimatedDistanceM = PathLoss.estimateDistanceM(r.level.toFloat(), band),
                            confidence = PathLoss.confidenceFor(r.level.toFloat()),
                        ),
                        identity = Identity(key = r.BSSID ?: ssid, label = ssid),
                        timestampMs = now,
                    )
                )
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) = emitResults()
        }
        context.registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))

        // Emit whatever is already cached, then re-trigger scans on an interval
        // long enough to stay under the throttle.
        emitResults()
        val pump = launch {
            while (isActive) {
                try {
                    @Suppress("DEPRECATION")
                    manager.startScan()
                } catch (e: Exception) {
                    Log.w(TAG, "startScan failed", e)
                }
                delay(SCAN_INTERVAL_MS)
            }
        }

        awaitClose {
            pump.cancel()
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private companion object {
        const val TAG = "WifiScanSource"
        const val SCAN_INTERVAL_MS = 30_000L
    }
}
