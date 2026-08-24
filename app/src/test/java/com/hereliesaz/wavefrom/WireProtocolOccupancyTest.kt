package com.hereliesaz.wavefrom

import com.hereliesaz.wavefrom.signal.source.sdr.SdrMessage
import com.hereliesaz.wavefrom.signal.source.sdr.WireProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WireProtocolOccupancyTest {

    @Test
    fun decodesOccupancyMessage() {
        val line = """{"type":"occupancy","zoneId":"pi-roof","present":true,""" +
            """"presenceConfidence":0.71,"breathingBpm":14.2,"breathingConfidence":0.58,""" +
            """"heartBpm":null,"heartConfidence":0.0,"synthetic":false,"ts":1719100000000}"""
        val msg = WireProtocol.decode(line)
        assertTrue(msg is SdrMessage.Occupancy)
        msg as SdrMessage.Occupancy
        assertEquals("pi-roof", msg.zoneId)
        assertTrue(msg.present)
        assertEquals(0.71f, msg.presenceConfidence, 1e-6f)
        assertEquals(14.2f, msg.breathingBpm!!, 1e-6f)
        assertEquals(0.58f, msg.breathingConfidence, 1e-6f)
        assertNull(msg.heartBpm)
        assertFalse(msg.synthetic)
        assertEquals(1_719_100_000_000L, msg.timestampMs)
    }

    @Test
    fun missingFieldsFallBackToSafeDefaults() {
        val msg = WireProtocol.decode("""{"type":"occupancy","zoneId":"z"}""")
        assertTrue(msg is SdrMessage.Occupancy)
        msg as SdrMessage.Occupancy
        assertFalse(msg.present)
        assertNull(msg.breathingBpm)
        assertNull(msg.heartBpm)
        assertFalse(msg.synthetic)
    }

    @Test
    fun syntheticFlagIsDecoded() {
        val line = """{"type":"occupancy","zoneId":"sim","present":false,""" +
            """"presenceConfidence":0.0,"synthetic":true}"""
        val msg = WireProtocol.decode(line) as SdrMessage.Occupancy
        assertTrue(msg.synthetic)
    }
}
