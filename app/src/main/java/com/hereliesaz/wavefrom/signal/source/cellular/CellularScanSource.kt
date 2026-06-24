package com.hereliesaz.wavefrom.signal.source.cellular

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.hereliesaz.wavefrom.signal.model.Detection
import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.model.Identity
import com.hereliesaz.wavefrom.signal.model.SignalBand
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.physics.GeoBearing
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
 * Cellular is single-antenna and the tower can be kilometres away, so a cell starts
 * as [Direction.RssiOnly] with a deliberately loose distance estimate. When an
 * OpenCellID key is configured ([CellLocationResolver]) and the phone has its own
 * fix, the cell is upgraded to a [Direction.TrueBearing] pointing at the tower's
 * database position; otherwise it stays RssiOnly.
 */
class CellularScanSource(private val context: Context) : SignalSource {

    override val sourceType = SourceType.CELLULAR

    private val telephony: TelephonyManager? =
        context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val towerResolver = CellLocationResolver()

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
                cells.forEach { info ->
                    val base = toDetection(info, now) ?: return@forEach
                    trySend(base)
                    // Best-effort upgrade: if the tower is in OpenCellID and we have
                    // our own fix, re-emit the same track (same identity key) with a
                    // true bearing toward the tower. The aggregator merges it over
                    // the RssiOnly emission above.
                    val key = cellKeyOf(info)
                    if (towerResolver.enabled && key != null) {
                        launch {
                            val tower = towerResolver.resolve(key) ?: return@launch
                            val me = lastKnownLatLon() ?: return@launch
                            val az = GeoBearing.azimuthDeg(me.lat, me.lon, tower.lat, tower.lon)
                            trySend(
                                base.copy(
                                    direction = Direction.TrueBearing(
                                        azimuthDeg = az,
                                        elevationDeg = null,
                                        confidence = TOWER_CONFIDENCE,
                                    ),
                                ),
                            )
                        }
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }

        awaitClose { pump.cancel() }
    }

    private fun toDetection(info: CellInfo, now: Long): Detection? {
        // Cell identity fields return CellInfo.UNAVAILABLE (Int.MAX_VALUE) when
        // out of service or location is denied; drop those so they don't all
        // merge into one junk track.
        val (band, dbm, key, label) = when (info) {
            is CellInfoLte -> {
                val ci = info.cellIdentity.ci
                val pci = info.cellIdentity.pci
                if (ci == CellInfo.UNAVAILABLE || pci == CellInfo.UNAVAILABLE) return null
                Quad(SignalBand.CELL_MID, info.cellSignalStrength.dbm, "lte-$ci-$pci", "LTE pci $pci")
            }
            is CellInfoGsm -> {
                val cid = info.cellIdentity.cid
                if (cid == CellInfo.UNAVAILABLE) return null
                Quad(SignalBand.CELL_LOW, info.cellSignalStrength.dbm, "gsm-$cid", "GSM cid $cid")
            }
            is CellInfoWcdma -> {
                val cid = info.cellIdentity.cid
                if (cid == CellInfo.UNAVAILABLE) return null
                Quad(SignalBand.CELL_MID, info.cellSignalStrength.dbm, "wcdma-$cid", "WCDMA cid $cid")
            }
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

    /**
     * The OpenCellID lookup key for a cell, or null when the identity is incomplete
     * or the radio type isn't supported. Needs the string MCC/MNC accessors (API 28+).
     */
    private fun cellKeyOf(info: CellInfo): CellKey? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return when (info) {
            is CellInfoLte -> {
                val id = info.cellIdentity
                val mcc = id.mccString?.toIntOrNull() ?: return null
                val mnc = id.mncString?.toIntOrNull() ?: return null
                if (id.tac == CellInfo.UNAVAILABLE || id.ci == CellInfo.UNAVAILABLE) return null
                CellKey("LTE", mcc, mnc, id.tac, id.ci.toLong())
            }
            is CellInfoGsm -> {
                val id = info.cellIdentity
                val mcc = id.mccString?.toIntOrNull() ?: return null
                val mnc = id.mncString?.toIntOrNull() ?: return null
                if (id.lac == CellInfo.UNAVAILABLE || id.cid == CellInfo.UNAVAILABLE) return null
                CellKey("GSM", mcc, mnc, id.lac, id.cid.toLong())
            }
            is CellInfoWcdma -> {
                val id = info.cellIdentity
                val mcc = id.mccString?.toIntOrNull() ?: return null
                val mnc = id.mncString?.toIntOrNull() ?: return null
                if (id.lac == CellInfo.UNAVAILABLE || id.cid == CellInfo.UNAVAILABLE) return null
                CellKey("UMTS", mcc, mnc, id.lac, id.cid.toLong())
            }
            else -> null
        }
    }

    /** The phone's own last-known position, or null without permission / a fix. */
    private fun lastKnownLatLon(): LatLon? {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val loc = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (e: SecurityException) {
            null
        } ?: return null
        return LatLon(loc.latitude, loc.longitude)
    }

    private companion object {
        const val TAG = "CellularScanSource"
        const val POLL_INTERVAL_MS = 5_000L
        // Tower-DB position is real but coarse (cell centroid, not the antenna).
        const val TOWER_CONFIDENCE = 0.6f
    }
}
