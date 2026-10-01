package ru.shlyahten.cvt.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for ObdPayloadDecoder.
 */
class ObdPayloadDecoderTest {
    
    @Test
    fun `extractDataBytes parses standard 2103 response correctly`() {
        val modeAndPid = "2103"
        // Standard ELM327 response: "61 03 AA BB CC DD ..."
        val normalized = "61 03 AA BB CC DD EE FF"
        
        val data = ObdPayloadDecoder.extractDataBytes(modeAndPid, normalized)
        
        assertNotNull(data)
        assertEquals(6, data!!.size)
        assertEquals(0xAA.toByte(), data[0])
        assertEquals(0xBB.toByte(), data[1])
        assertEquals(0xCC.toByte(), data[2])
        assertEquals(0xDD.toByte(), data[3])
        assertEquals(0xEE.toByte(), data[4])
        assertEquals(0xFF.toByte(), data[5])
    }
    
    @Test
    fun `extractDataBytes parses response with header correctly`() {
        val modeAndPid = "2103"
        // Response with ECU header: "7E9 06 61 03 AA BB CC DD"
        val normalized = "7E9 06 61 03 AA BB CC DD"
        
        val data = ObdPayloadDecoder.extractDataBytes(modeAndPid, normalized)
        
        assertNotNull(data)
        assertEquals(4, data!!.size)
        assertEquals(0xAA.toByte(), data[0])
        assertEquals(0xBB.toByte(), data[1])
    }
    
    @Test
    fun `extractDataBytes handles multiline response`() {
        val modeAndPid = "2103"
        val normalized = "7E9 06\n61 03\nAA BB CC DD"
        
        val data = ObdPayloadDecoder.extractDataBytes(modeAndPid, normalized)
        
        assertNotNull(data)
        assertEquals(4, data!!.size)
    }
    
    @Test
    fun `extractDataBytes returns null for NO DATA`() {
        val modeAndPid = "2103"
        val normalized = "NO DATA"
        
        val data = ObdPayloadDecoder.extractDataBytes(modeAndPid, normalized)
        
        assertNull(data)
    }
    
    @Test
    fun `extractDataBytes returns null when pattern not found`() {
        val modeAndPid = "2103"
        val normalized = "61 04 AA BB CC" // Wrong PID (04 instead of 03)
        
        val data = ObdPayloadDecoder.extractDataBytes(modeAndPid, normalized)
        
        assertNull(data)
    }
    
    @Test
    fun `extractDataBytes parses oil degradation response 2110`() {
        val modeAndPid = "2110"
        // Response: "61 10 XX YY AC AD"
        val normalized = "61 10 00 00 00 0A 0B"
        
        val data = ObdPayloadDecoder.extractDataBytes(modeAndPid, normalized)
        
        assertNotNull(data)
        assertEquals(5, data!!.size)
        assertEquals(0x0A.toByte(), data[3]) // AC
        assertEquals(0x0B.toByte(), data[4]) // AD
    }
    
    @Test
    fun `extractDataBytes preserves all data bytes after 61 03`() {
        val modeAndPid = "2103"
        // All bytes after "61 03" are payload data - do not skip any.
        // Even if a byte looks like a PCI pattern (0x02-0x07, 0x10, 0x21), it's valid data.
        val normalized = "7E9 04 61 03 04 AA BB CC DD"
        
        val data = ObdPayloadDecoder.extractDataBytes(modeAndPid, normalized)
        
        assertNotNull(data)
        assertEquals(5, data!!.size)
        assertEquals(0x04.toByte(), data[0])  // First data byte, NOT a length indicator
        assertEquals(0xAA.toByte(), data[1])
        assertEquals(0xBB.toByte(), data[2])
        assertEquals(0xCC.toByte(), data[3])
        assertEquals(0xDD.toByte(), data[4])
    }
    
    @Test
    fun `extractDataBytes preserves data bytes even if they match 0x10 pattern`() {
        val modeAndPid = "2103"
        // Byte 0x10 after "61 03" is valid data, not a PCI byte
        val normalized = "7E9 10 61 03 10 AA BB CC DD"
        
        val data = ObdPayloadDecoder.extractDataBytes(modeAndPid, normalized)
        
        assertNotNull(data)
        assertEquals(5, data!!.size)
        assertEquals(0x10.toByte(), data[0])  // Valid data byte
        assertEquals(0xAA.toByte(), data[1])
        assertEquals(0xBB.toByte(), data[2])
    }
    
    @Test
    fun `extractDataBytes preserves data bytes even if they match 0x21 pattern`() {
        val modeAndPid = "2103"
        // Byte 0x21 after "61 03" is valid data, not a PCI byte
        val normalized = "7E9 21 61 03 21 AA BB CC DD"
        
        val data = ObdPayloadDecoder.extractDataBytes(modeAndPid, normalized)
        
        assertNotNull(data)
        assertEquals(5, data!!.size)
        assertEquals(0x21.toByte(), data[0])  // Valid data byte
        assertEquals(0xAA.toByte(), data[1])
        assertEquals(0xBB.toByte(), data[2])
    }
    
    @Test
    fun `extractDataBytes extracts correct bytes for CVT temp calculation`() {
        val modeAndPid = "2103"
        val normalized =
            "61 03 02 02 00 B4 EA 00 00 FA FA F3 40 00 00 21 00 00"

        val data = ObdPayloadDecoder.extractDataBytes(modeAndPid, normalized)

        assertNotNull(data)
        assertEquals(16, data!!.size)
        assertEquals(0x02.toByte(), data[0])
        assertEquals(0x02.toByte(), data[1])
        assertEquals(0x00.toByte(), data[2])
        assertEquals(0x21.toByte(), data[13])
    }

    @Test
    fun `parseIsoTpMultiFrame reassembles 2103 payload and preserves data index`() {
        val rawLines = listOf(
            "7E9 10 12 61 03 02 02 00 B4",
            "7E9 21 EA 00 00 FA FA F3 40",
            "7E9 22 00 00 21 00 00 05 AB",
        )

        val payload = ObdPayloadDecoder.parseIsoTpMultiFrame(rawLines)

        assertNotNull(payload)
        assertEquals(18, payload!!.size)
        assertEquals(0x61.toByte(), payload[0])
        assertEquals(0x03.toByte(), payload[1])

        val data = ObdPayloadDecoder.extractDataBytes("2103", payload.joinToString(" ") { "%02X".format(it) })

        assertNotNull(data)
        assertEquals(16, data!!.size)
        assertEquals(0x21.toByte(), data[13])
    }

    @Test
    fun `parseIsoTpMultiFrame reassembles payload without CAN headers ATH0`() {
        val rawLines = listOf(
            "10 12 61 03 02 02 00 B4",
            "21 EA 00 00 FA FA F3 40",
            "22 00 00 21 00 00 05 AB",
        )

        val payload = ObdPayloadDecoder.parseIsoTpMultiFrame(rawLines)

        assertNotNull(payload)
        assertEquals(18, payload!!.size)
        assertEquals(0x61.toByte(), payload[0])
        assertEquals(0x03.toByte(), payload[1])
        assertEquals(0x21.toByte(), payload[15])
    }

    @Test
    fun `parseIsoTpMultiFrame handles consecutive frame sequence wrapping 0x20`() {
        val lines = mutableListOf("7E9 10 74 61 03 01 02 03 04") // 6 bytes
        // Frames 21..2F (15 frames * 7 bytes = 105 bytes)
        for (sn in 1..15) {
            val hexSn = sn.toString(16).uppercase()
            lines.add("7E9 2$hexSn 00 01 02 03 04 05 06")
        }
        // Frame 16 wraps sequence to 0 (PCI 0x20), remaining bytes to reach 0x74 = 116 bytes (116 - 6 - 105 = 5 bytes)
        lines.add("7E9 20 AA BB CC DD EE")

        val payload = ObdPayloadDecoder.parseIsoTpMultiFrame(lines)
        assertNotNull(payload)
        assertEquals(116, payload!!.size)
        assertEquals(0xAA.toByte(), payload[111])
        assertEquals(0xEE.toByte(), payload[115])
    }
}
