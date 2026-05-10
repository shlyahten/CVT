package ru.shlyahten.cvt.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for CvtTempParser.
 * Tests the parsing of CVT temp count and temperature conversion formulas.
 */
class CvtTempParserTest {

    /**
     * Test parsing CVT temp count from the provided example response.
     * Expected: hex 21 -> decimal 33
     */
    @Test
    fun `test parseCvtTempCount from example response`() {
        // Example from the issue description
        val rawLines = listOf(
                "7E9 10 12 61 03 02 02 00 B4",
                "7E9 21 EA 00 00 FA FA F3 40",
                "7E9 22 00 00 21 00 00 05 AB"
        )

        val result = CvtTempParser.parseCvtTempCount(rawLines)
        assertEquals("Should parse hex 21 as decimal 33", 33, result)
    }

    /**
     * Test parsing CVT temp count from single-frame response.
     */
    @Test
    fun `test parseCvtTempCount single frame`() {
        // Single frame response (simplified)
        val rawLines = listOf("7E9 06 61 03 21 00 00")

        val result = CvtTempParser.parseCvtTempCount(rawLines)
        assertEquals("Should parse hex 21 as decimal 33 from single frame", 33, result)
    }

    /**
     * Test that invalid responses return null.
     */
    @Test
    fun `test parseCvtTempCount invalid response returns null`() {
        // Wrong header
        val rawLines1 = listOf("7E9 10 12 61 04 02 02 00 B4")
        assertEquals("Should return null for wrong PID", null, CvtTempParser.parseCvtTempCount(rawLines1))

        // Too short
        val rawLines2 = listOf("7E9 10 12")
        assertEquals("Should return null for too short response", null, CvtTempParser.parseCvtTempCount(rawLines2))

        // Empty
        val rawLines3: List<String> = emptyList()
        assertEquals("Should return null for empty response", null, CvtTempParser.parseCvtTempCount(rawLines3))
    }

    /**
     * Test temperature conversion formula 1 with known value.
     * Expected: N=33 -> ~11.16°C
     */
    @Test
    fun `test convertCountToTemp1 with N=33`() {
        val count = 33
        val temp = CvtTempParser.convertCountToTemp1(count)
        // Expected: approximately 11.16
        assertEquals("Temp1 for N=33 should be approximately 11.16°C", 11.16, temp, 0.01)
    }

    /**
     * Test temperature conversion formula 2 with known value.
     * Expected: N=33 -> ~8.75°C
     */
    @Test
    fun `test convertCountToTemp2 with N=33`() {
        val count = 33
        val temp = CvtTempParser.convertCountToTemp2(count)
        // Expected: approximately 8.75
        assertEquals("Temp2 for N=33 should be approximately 8.75°C", 8.75, temp, 0.01)
    }

    /**
     * Test edge cases for temperature conversion.
     */
    @Test
    fun `test temperature conversion edge cases`() {
        // Test N=0
        assertEquals("Temp1 for N=0 should be -36.6", -36.6, CvtTempParser.convertCountToTemp1(0), 0.001)
        assertEquals("Temp2 for N=0 should be -30.1", -30.1, CvtTempParser.convertCountToTemp2(0), 0.001)

        // Test N=255
        val temp1Max = CvtTempParser.convertCountToTemp1(255)
        val temp2Max = CvtTempParser.convertCountToTemp2(255)
        // Just verify they produce reasonable values (not NaN or infinity)
        assertTrue("Temp1 for N=255 should be a valid number", !temp1Max.isNaN() && !temp1Max.isInfinite())
        assertTrue("Temp2 for N=255 should be a valid number", !temp2Max.isNaN() && !temp2Max.isInfinite())
    }
}