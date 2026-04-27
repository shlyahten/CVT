package ru.shlyahten.cvt.elm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ElmResponseParser.
 */
class ElmResponseParserTest {
    
    @Test
    fun `parse handles standard response correctly`() {
        val raw = "7E9 06 61 03 AA BB CC DD"
        val result = ElmResponseParser.parse(raw)
        
        assertFalse(result.isNoData)
        assertFalse(result.isError)
        assertTrue(result.normalized.contains("61 03"))
    }
    
    @Test
    fun `parse detects NO DATA response`() {
        val raw = "NO DATA"
        val result = ElmResponseParser.parse(raw)
        
        assertTrue(result.isNoData)
        assertFalse(result.isError)
    }
    
    @Test
    fun `parse detects UNABLE TO CONNECT`() {
        val raw = "UNABLE TO CONNECT"
        val result = ElmResponseParser.parse(raw)
        
        assertTrue(result.isNoData)
    }
    
    @Test
    fun `parse detects ERROR response`() {
        val raw = "ERROR"
        val result = ElmResponseParser.parse(raw)
        
        assertTrue(result.isError)
    }
    
    @Test
    fun `parse detects CAN ERROR`() {
        val raw = "CAN ERROR"
        val result = ElmResponseParser.parse(raw)
        
        assertTrue(result.isError)
    }
    
    @Test
    fun `parse handles response with prompt character`() {
        val raw = "7E9 06 61 03 AA BB>"
        val result = ElmResponseParser.parse(raw)
        
        assertFalse(result.isNoData)
        assertFalse(result.isError)
        assertTrue(result.normalized.contains("61 03"))
    }
    
    @Test
    fun `parse handles multiline response with carriage returns`() {
        val raw = "7E9\r06\r61 03\rAA BB\r>"
        val result = ElmResponseParser.parse(raw)
        
        assertFalse(result.isNoData)
        assertFalse(result.isError)
        assertNotNull(result.normalized)
    }
    
    @Test
    fun `parse filters out SEARCHING message`() {
        val raw = "SEARCHING...\n7E9 06 61 03 AA BB"
        val result = ElmResponseParser.parse(raw)
        
        assertFalse(result.normalized.contains("SEARCHING"))
        assertFalse(result.isNoData)
    }
    
    @Test
    fun `parse filters out STOPPED message`() {
        val raw = "STOPPED\n7E9 06 61 03 AA BB"
        val result = ElmResponseParser.parse(raw)
        
        assertFalse(result.normalized.contains("STOPPED"))
    }
    
    @Test
    fun `parse handles BUS INIT message as error`() {
        val raw = "BUS INIT..."
        val result = ElmResponseParser.parse(raw)
        
        assertTrue(result.isError)
    }
}
