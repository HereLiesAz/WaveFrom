package com.hereliesaz.wavefrom.signal.source.wifi

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.net.wifi.rtt.WifiRttManager
import android.os.Build
import android.util.Log
import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.model.Identity
import com.hereliesaz.wavefrom.signal.model.SignalBand
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.source.SignalSource
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Ranges Wi-Fi access points that support 802.11mc Fine Time Measurement via
 * [WifiRttManager] (API 28+). Unlike RSSI, FTM gives a *true* time-of-flight
 * distance (sub-metre to ~1-2 m), so detections carry a high-confidence
 * [Direction.RssiOnly] distance. Sharing the Wi-Fi BSSID key, these refine the
 * same AP track and become the strongest input to the motion-aided localizer's
 * multilateration.
 */
class WifiRttSource(private val context: Context) : SignalSource {

    override val sourceType = SourceType.WIFI

    private val rtt: WifiRttManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            context.applicationContext.getSystemService(Context.WIFI_RTT_RANGING_SERVICE) as? WifiRttManager
        else null

    private val wifi: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    override fun isAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT) &&
            rtt?.isAvailable == true

    override fun detections(): Flow<Detection> = callbackFlow {
        if (!isAvailable()) { close(); return@callbackFlow }
        val manager = rtt!!
        val executor = ContextCompat.getMainExecutor(context)

        val pump = launch {
            while (isActive) {
                val responders = try {
                    @Suppress("DEPRECATION", "MissingPermission")
                    wifi?.scanResults?.filter { it.is80211mcResponder } ?: emptyList()
                } catch (e: SecurityException) {
                    emptyList()
                }
                if (responders.isNotEmpty()) {
                    val request = RangingRequest.Builder()
                        .addAccessPoints(responders.take(RangingRequest.getMaxPeers()))
                        .build()
                    try {
                        manager.startRanging(request, executor, object : RangingResultCallback() {
                            override fun onRangingFailure(code: Int) {
                                Log.w(TAG, "RTT ranging failed: $code")
                            }

                            override fun onRangingResults(results: List<RangingResult>) {
                                val now = System.currentTimeMillis()
                                for (r in results) toDetection(r, responders, now)?.let { trySend(it) }
                            }
                        })
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Missing permission for RTT ranging", e)
                    }
                }
                delay(RANGE_INTERVAL_MS)
            }
        }

        awaitClose { pump.cancel() }
    }

    private fun toDetection(
        r: RangingResult,
        responders: List<android.net.wifi.ScanResult>,
        now: Long,
    ): Detection? {
        if (r.status != RangingResult.STATUS_SUCCESS) return null
        val mac = r.macAddress?.toString() ?: return null
        val distanceM = r.distanceMm / 1000f
        val sr = responders.firstOrNull { it.BSSID.equals(mac, ignoreCase = true) }
        val band = sr?.let { SignalBand.fromWifiFrequencyMhz(it.frequency) } ?: SignalBand.WIFI_5
        return Detection(
            sourceType = SourceType.WIFI,
            band = band,
            frequencyHz = sr?.frequency?.toLong()?.times(1_000_000L),
            powerDbm = r.rssi.toFloat(),
            direction = Direction.RssiOnly(
                estimatedDistanceM = distanceM,
                distanceConfidence = 0.9f, // FTM range is far more trustworthy than RSSI
            ),
            identity = Identity(
                key = mac,
                label = sr?.SSID?.takeIf { it.isNotBlank() } ?: "RTT AP",
            ),
            timestampMs = now,
        )
    }

    private companion object {
        const val TAG = "WifiRttSource"
        const val RANGE_INTERVAL_MS = 2_000L
    }
}
