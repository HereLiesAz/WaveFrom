package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.signal.model.Direction
import com.hereliesaz.wavefrom.signal.physics.GeoBearing
import com.hereliesaz.wavefrom.signal.source.cellular.CellKey
import com.hereliesaz.wavefrom.signal.source.cellular.LatLon
import com.hereliesaz.wavefrom.signal.source.cellular.LocationProvider
import com.hereliesaz.wavefrom.signal.source.cellular.TowerResolver
import com.hereliesaz.wavefrom.signal.source.cellular.upgradeToTrueBearing
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The pure cellular RssiOnly→TrueBearing upgrade, with injected fakes (no SIM, no
 * network, no GPS).
 */
class CellularUpgradeTest {

    private val key = CellKey("LTE", mcc = 310, mnc = 410, lac = 7, cid = 42)
    private val tower = LatLon(52.0, 0.5)
    private val me = LatLon(51.5074, -0.1278)

    private fun resolver(enabled: Boolean, result: LatLon?) = object : TowerResolver {
        override val enabled = enabled
        override suspend fun resolve(key: CellKey): LatLon? = result
    }

    @Test
    fun upgradesToBearingTowardTower() = runTest {
        val dir = upgradeToTrueBearing(
            resolver(enabled = true, result = tower),
            LocationProvider { me },
            key,
            confidence = 0.6f,
        )
        assertEquals(GeoBearing.azimuthDeg(me.lat, me.lon, tower.lat, tower.lon), dir!!.azimuthDeg, 0.001f)
        assertEquals(0.6f, dir.confidence, 0.001f)
        assertNull(dir.elevationDeg)
        // Sanity: London → a point NE is a north-easterly bearing.
        assertEquals(true, dir.azimuthDeg in 0f..90f)
    }

    @Test
    fun disabledResolverIsNull() = runTest {
        assertNull(upgradeToTrueBearing(resolver(enabled = false, result = tower), LocationProvider { me }, key, 0.6f))
    }

    @Test
    fun unknownTowerIsNull() = runTest {
        assertNull(upgradeToTrueBearing(resolver(enabled = true, result = null), LocationProvider { me }, key, 0.6f))
    }

    @Test
    fun noOwnFixIsNull() = runTest {
        assertNull(upgradeToTrueBearing(resolver(enabled = true, result = tower), LocationProvider { null }, key, 0.6f))
    }
}
