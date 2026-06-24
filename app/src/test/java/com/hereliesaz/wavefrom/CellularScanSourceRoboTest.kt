package com.hereliesaz.wavefrom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hereliesaz.wavefrom.signal.model.SourceType
import com.hereliesaz.wavefrom.signal.source.cellular.CellKey
import com.hereliesaz.wavefrom.signal.source.cellular.CellularScanSource
import com.hereliesaz.wavefrom.signal.source.cellular.LatLon
import com.hereliesaz.wavefrom.signal.source.cellular.LocationProvider
import com.hereliesaz.wavefrom.signal.source.cellular.TowerResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Proves the injected [CellularScanSource] constructor wires under a real Android
 * [Context] — the one thing the pure tests can't show. The RssiOnly→TrueBearing logic
 * itself is covered purely in [CellularUpgradeTest]; the emulator smoke test exercises
 * the live `getAllCellInfo` path.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CellularScanSourceRoboTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val fakeResolver = object : TowerResolver {
        override val enabled = true
        override suspend fun resolve(key: CellKey): LatLon? = LatLon(52.0, 0.5)
    }

    @Test
    fun constructsWithInjectedDependencies() {
        val source = CellularScanSource(
            context = context,
            towerResolver = fakeResolver,
            locationProvider = LocationProvider { LatLon(51.5, -0.1) },
        )
        assertEquals(SourceType.CELLULAR, source.sourceType)
        // Robolectric's default TelephonyManager reports a phone type, so the source
        // advertises itself as available.
        assertTrue(source.isAvailable())
    }
}
