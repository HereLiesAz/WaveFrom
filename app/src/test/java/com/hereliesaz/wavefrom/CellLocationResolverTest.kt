package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.signal.source.cellular.CellLocationResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CellLocationResolverTest {

    @Test
    fun parsesSuccessResponse() {
        val loc = CellLocationResolver.parse(
            """{"lat":51.5074,"lon":-0.1278,"range":1000,"samples":42}""",
        )
        assertEquals(51.5074, loc!!.lat, 1e-6)
        assertEquals(-0.1278, loc.lon, 1e-6)
    }

    @Test
    fun errorResponseIsNull() {
        assertNull(CellLocationResolver.parse("""{"error":"invalid key"}"""))
    }

    @Test
    fun missingFieldsAreNull() {
        assertNull(CellLocationResolver.parse("""{"range":1000}"""))
    }

    @Test
    fun zeroIslandIsNull() {
        // OpenCellID returns (0,0) as a not-found sentinel.
        assertNull(CellLocationResolver.parse("""{"lat":0,"lon":0}"""))
    }

    @Test
    fun malformedJsonIsNull() {
        assertNull(CellLocationResolver.parse("not json"))
    }
}
