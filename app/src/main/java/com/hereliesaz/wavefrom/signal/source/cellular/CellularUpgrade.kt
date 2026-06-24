package com.hereliesaz.wavefrom.signal.source.cellular

import androidx.annotation.VisibleForTesting
import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.physics.GeoBearing

/** Resolves a cell tower's position. Implemented by [CellLocationResolver]; faked in tests. */
interface TowerResolver {
    /** True when lookups are possible (an API key is configured). */
    val enabled: Boolean

    /** The tower's database position, or null when disabled / not found / on error. */
    suspend fun resolve(key: CellKey): LatLon?
}

/** The phone's own last-known position, or null without permission / a fix. */
fun interface LocationProvider {
    fun lastKnownLatLon(): LatLon?
}

/** The most recent successful tower resolve, for the debug diagnostics overlay. */
data class CellResolveSnapshot(val cellKey: String, val tower: LatLon, val bearingDeg: Float)

/** Debug-only breadcrumb of the last cell→tower resolve. Never read in release builds. */
object CellDiagnostics {
    @Volatile
    var lastResolve: CellResolveSnapshot? = null
}

/**
 * Pure upgrade step: turn a resolved tower position plus our own fix into the
 * [Direction.TrueBearing] to re-emit, or null when the resolver is disabled, the tower
 * isn't in the database, or we have no fix of our own. Reuses [GeoBearing] and takes no
 * Android types, so it is unit-testable with injected fakes.
 */
@VisibleForTesting
internal suspend fun upgradeToTrueBearing(
    resolver: TowerResolver,
    locationProvider: LocationProvider,
    key: CellKey,
    confidence: Float,
): Direction.TrueBearing? {
    if (!resolver.enabled) return null
    val tower = resolver.resolve(key) ?: return null
    val me = locationProvider.lastKnownLatLon() ?: return null
    val az = GeoBearing.azimuthDeg(me.lat, me.lon, tower.lat, tower.lon)
    // Debug breadcrumb; the return value stays a pure function of the inputs.
    CellDiagnostics.lastResolve = CellResolveSnapshot(key.cacheKey, tower, az)
    return Direction.TrueBearing(azimuthDeg = az, elevationDeg = null, confidence = confidence)
}
