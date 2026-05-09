package ru.shlyahten.cvt.obd

/**
 * Parser for CVT temperature data from Mitsubishi Lancer X CVT ECU (PID 2103).
 * Handles ISO-TP multi-frame assembly and extracts the raw N value for temperature calculations.
 */
object CvtTempParser {

    /**
     * Parses CVT temp count (N value) from raw ELM327 response lines.
     *
     * Expected response format for PID 2103:
     *   First line: 7E9 10 XX 61 03 YY ...
     *   Following lines: 7E9 21 ZZ ... , 7E9 22 ZZ ...
     *
     * Where:
     *   - 7E9: CVT ECU response header
     *   - 10/21/22: ISO-TP PCI bytes (First frame, Consecutive frame)
     *   - XX: DLC (Data Length Code) for first frame
     *   - 61 03: Response mode (0x40 + 0x01) and PID
     *   - YY: First data byte (this is our N value for CVT temp count)
     *   - ZZ: Additional data bytes
     *
     * @param rawLines Raw response lines from ELM327 (e.g., ["7E9 10 12 61 03 02 02 00 B4", "7E9 21 EA 00 00 FA FA F3 40", ...])
     * @return CVT temp count as unsigned byte (0-255), or null if parsing fails
     */
    fun parseCvtTempCount(rawLines: List<String>): Int? {
        // Validate input
        if (rawLines.isEmpty()) return null

        // Step 1: Reassemble ISO-TP multi-frame payload
        val payload = ObdPayloadDecoder.parseIsoTpMultiFrame(rawLines)
        if (payload == null) return null

        // Step 2: Find the response header "61 03" in the payload
        val headerIndex = indexOfSequence(payload, byteArrayOf(0x61, 0x03))
        if (headerIndex == -1) return null

        // Step 3: Extract the N value (first byte after 61 03)
        val nValueIndex = headerIndex + 2
        if (nValueIndex >= payload.size) return null

        // Return as unsigned byte (0-255)
        return payload[nValueIndex].toInt() and 0xFF
    }

    /**
     * Converts CVT temp count to temperature using formula 1.
     *
     * Formula: (0.000000002344*(N^5))+(-0.000001387*(N^4))+(0.0003193*(N^3))+(-0.03501*(N^2))+(2.302*N)+(-36.6)
     *
     * @param count CVT temp count (N value, 0-255)
     * @return Temperature in Celsius
     */
    fun convertCountToTemp1(count: Int): Double {
        val n = count.toDouble()
        return (0.000000002344 * Math.pow(n, 5.0))
                + (-0.000001387 * Math.pow(n, 4.0))
                + (0.0003193 * Math.pow(n, 3.0))
                + (-0.03501 * Math.pow(n, 2.0))
                + (2.302 * n)
                + (-36.6)
    }

    /**
     * Converts CVT temp count to temperature using formula 2.
     *
     * Formula: (0.0000286*N*N*N)+(-0.00951*N*N)+(1.46*N)+(-30.1)
     *
     * @param count CVT temp count (N value, 0-255)
     * @return Temperature in Celsius
     */
    fun convertCountToTemp2(count: Int): Double {
        val n = count.toDouble()
        return (0.0000286 * n * n * n)
                + (-0.00951 * n * n)
                + (1.46 * n)
                + (-30.1)
    }

    /**
     * Helper method to find a byte sequence within a byte array.
     *
     * @param data   The data to search in
     * @param pattern The pattern to search for
     * @return Index of first occurrence, or -1 if not found
     */
    private fun indexOfSequence(data: ByteArray, pattern: ByteArray): Int {
        if (pattern.isEmpty() || data.size < pattern.size) return -1

        outer@
        for (i in 0 until data.size - pattern.size + 1) {
            for (j in 0 until pattern.size) {
                if (data[i + j] != pattern[j]) {
                    continue@outer
                }
            }
            return i
        }
        return -1
    }
}