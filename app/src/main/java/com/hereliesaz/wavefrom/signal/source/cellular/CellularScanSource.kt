package com.hereliesaz.wavefrom.signal.source.cellular

import android.content.Context
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
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
 * Reports serving + neighbor cells via [TelephonyManager.getAllCellInfo].
 * Cellular is single-antenna and the tower can be kilometres away, so these are
 * [Direction.RssiOnly] with a deliberately loose distance estimate; they become
 * useful once the motion-aided localizer (Phase 3) can triangulate them.
 */
class CellularScanSource(private val context: Context) : SignalSource {

    override val sourceType = SourceType.CELLULAR

    private val telephony: TelephonyManager? =
        context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    override fun isAvailable(): Boolean =
        telephony?.phoneType != null && telephony.phoneType != TelephonyManager.PHONE_TYPE_NONE

    override fun detections(): Flow<Detection> = callbackFlow {
        val tm = telephony ?: run { close(); return@callbackFlow }

        val pump = launch {
            while (isActive) {
                val cells: List<CellInfo> = try {
                    @Suppress("DEPRECATION", "MissingPermission")
                    tm.allCellInfo ?: emptyList()
                } catch (e: SecurityException) {
                    Log.w(TAG, "Missing READ_PHONE_STATE / location for cell info", e)
                    emptyList()
                }
                val now = System.currentTimeMillis()
                cells.forEach { info -> toDetection(info, now)?.let { trySend(it) } }
                delay(POLL_INTERVAL_MS)
            }
        }

        awaitClose { pump.cancel() }
    }

    private fun toDetection(info: CellInfo, now: Long): Detection? {
        val (band, dbm, key, label) = when (info) {
            is CellInfoLte -> Quad(
                SignalBand.CELL_MID,
                info.cellSignalStrength.dbm,
                "lte-${info.cellIdentity.ci}-${info.cellIdentity.pci}",
                "LTE pci ${info.cellIdentity.pci}",
            )
            is CellInfoGsm -> Quad(
                SignalBand.CELL_LOW,
                info.cellSignalStrength.dbm,
                "gsm-${info.cellIdentity.cid}",
                "GSM cid ${info.cellIdentity.cid}",
            )
            is CellInfoWcdma -> Quad(
                SignalBand.CELL_MID,
                info.cellSignalStrength.dbm,
                "wcdma-${info.cellIdentity.cid}",
                "WCDMA cid ${info.cellIdentity.cid}",
            )
            else -> nrDetection(info)
        } ?: return null

        if (dbm == CellInfo.UNAVAILABLE || dbm >= 0) return null
        return Detection(
            sourceType = SourceType.CELLULAR,
            band = band,
            frequencyHz = band.approxCenterHz,
            powerDbm = dbm.toFloat(),
            direction = Direction.RssiOnly(
                estimatedDistanceM = PathLoss.estimateDistanceM(dbm.toFloat(), band),
                confidence = PathLoss.confidenceFor(dbm.toFloat()),
            ),
            identity = Identity(key = key, label = label),
            timestampMs = now,
        )
    }

    private fun nrDetection(info: CellInfo): Quad? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || info !is CellInfoNr) return null
        val ss = info.cellSignalStrength as? CellSignalStrengthNr ?: return null
        return Quad(SignalBand.CELL_HIGH, ss.dbm, "nr-${info.cellIdentity}", "5G NR")
    }

    /** Small carrier so the `when` can return four fields without a heap type. */
    private data class Quad(val band: SignalBand, val dbm: Int, val key: String, val label: String)

    private companion object {
        const val TAG = "CellularScanSource"
        const val POLL_INTERVAL_MS = 5_000L
    }
}
