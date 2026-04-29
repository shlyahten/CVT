package ru.shlyahten.cvt.obd

import org.junit.Assert.assertEquals
import org.junit.Test

class ObdVariableMappingTest {

    @Test
    fun `test AA and N mapping from first data byte`() {
        // Simulate response data bytes after header (e.g., 61 03 XX YY ZZ)
        val data = byteArrayOf(0x50.toByte()) // Single byte = 80 decimal
        
        val vars = ObdVariableMapping.fromDataBytes(data)
        
        assertEquals(80.0, vars["AA"], 0.001)
        assertEquals(80.0, vars["N"], 0.001)
        assertEquals(80.0, vars["A"], 0.001)
    }

    @Test
    fun `test multiple bytes mapping to AA AB AC AD`() {
        // Simulate multi-byte response: [0x40, 0x80, 0xC0, 0xFF]
        val data = byteArrayOf(
            0x40.toByte(), // 64
            0x80.toByte(), // 128
            0xC0.toByte(), // 192
            0xFF.toByte()  // 255
        )
        
        val vars = ObdVariableMapping.fromDataBytes(data)
        
        assertEquals(64.0, vars["AA"], 0.001)
        assertEquals(128.0, vars["AB"], 0.001)
        assertEquals(192.0, vars["AC"], 0.001)
        assertEquals(255.0, vars["AD"], 0.001)
        
        // Aliases
        assertEquals(64.0, vars["N"], 0.001)
        assertEquals(64.0, vars["A"], 0.001)
        assertEquals(128.0, vars["B"], 0.001)
        assertEquals(192.0, vars["C"], 0.001)
        assertEquals(255.0, vars["D"], 0.001)
    }

    @Test
    fun `test oil degradation formula AC*256+AD`() {
        // Test case: AC=0x03, AD=0xE8 => 3*256 + 232 = 1000
        val data = byteArrayOf(
            0x00.toByte(), // AA (unused)
            0x00.toByte(), // AB (unused)
            0x03.toByte(), // AC = 3
            0xE8.toByte()  // AD = 232
        )
        
        val vars = ObdVariableMapping.fromDataBytes(data)
        val result = vars["AC"]!! * 256 + vars["AD"]!!
        
        assertEquals(1000.0, result, 0.001)
    }

    @Test
    fun `test empty data returns zero values`() {
        val data = byteArrayOf()
        val vars = ObdVariableMapping.fromDataBytes(data)
        
        assertEquals(0.0, vars["AA"], 0.001)
        assertEquals(0.0, vars["N"], 0.001)
    }
}
