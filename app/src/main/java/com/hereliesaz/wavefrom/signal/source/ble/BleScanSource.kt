package com.hereliesaz.wavefrom.signal.source.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.model.Identity
import com.hereliesaz.wavefrom.signal.model.SignalBand
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.physics.PathLoss
import com.hereliesaz.wavefrom.signal.source.SignalSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Discovers nearby Bluetooth LE devices via the platform [android.bluetooth.le.BluetoothLeScanner].
 * Like Wi-Fi these are [Direction.RssiOnly] — strength, not bearing. BLE
 * advertises continuously, so it gives a far livelier feed than throttled
 * Wi-Fi scanning.
 */
class BleScanSource(private val context: Context) : SignalSource {

    override val sourceType = SourceType.BLE

    private val adapter = (context.applicationContext
        .getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    override fun isAvailable(): Boolean = adapter?.isEnabled == true

    override fun detections(): Flow<Detection> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            close(); return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val now = System.currentTimeMillis()
                val name = try {
                    result.device.name
                } catch (e: SecurityException) {
                    null
                }
                trySend(
                    Detection(
                        sourceType = SourceType.BLE,
                        band = SignalBand.BLUETOOTH,
                        frequencyHz = null,
                        powerDbm = result.rssi.toFloat(),
                        direction = Direction.RssiOnly(
                            estimatedDistanceM = PathLoss.estimateDistanceM(
                                result.rssi.toFloat(), SignalBand.BLUETOOTH,
                            ),
                            confidence = PathLoss.confidenceFor(result.rssi.toFloat()),
                        ),
                        identity = Identity(
                            key = result.device.address,
                            label = name?.takeIf { it.isNotBlank() } ?: "BLE device",
                        ),
                        timestampMs = now,
                    )
                )
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "BLE scan failed: $errorCode")
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, callback)
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing BLUETOOTH_SCAN permission", e)
            close(e); return@callbackFlow
        }

        awaitClose {
            runCatching { scanner.stopScan(callback) }
        }
    }

    private companion object {
        const val TAG = "BleScanSource"
    }
}
