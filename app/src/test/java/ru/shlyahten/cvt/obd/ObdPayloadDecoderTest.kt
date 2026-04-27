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
}
