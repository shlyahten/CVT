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
        // Simulated Mitsubishi Lancer X response: 61 03 followed by two bytes for temperature
        // Example: N = 0x03E8 (1000 decimal) should give reasonable temperature
        val normalized = "7E9 04 61 03 03 E8"
        
        val data = ObdPayloadDecoder.extractDataBytes(modeAndPid, normalized)
        
        assertNotNull(data)
        assertEquals(2, data!!.size)
        assertEquals(0x03.toByte(), data[0])
        assertEquals(0xE8.toByte(), data[1])
        
        // Verify N calculation: (0x03 << 8) | 0xE8 = 0x03E8 = 1000
        val n = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        assertEquals(1000, n)
    }
}
