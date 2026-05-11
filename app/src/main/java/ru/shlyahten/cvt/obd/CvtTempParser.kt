package ru.shlyahten.cvt.obd

import android.util.Log

/**
 * Parser for CVT temperature data from Mitsubishi Lancer X CVT ECU (PID 2103).
 * Handles ISO-TP multi-frame assembly and extracts the raw N value for temperature calculations.
 */
object CvtTempParser {

    /**
     * Parses CVT temp count (N value) from raw ELM327 response lines.
     *
     * Expected response format for PID 2103:
     *   First line: 7E9 10 XX 61 03 followed by payload data bytes
     *   Following lines: 7E9 21 ... , 7E9 22 ...
     *
     * After `61 03`, the count **N** is the byte at index [CVT_2103_TEMP_COUNT_BYTE_INDEX] in the
     * service data (same layout as Torque/PIDs.csv for this vehicle).
     *
     * @param rawLines Raw response lines from ELM327 (e.g., ["7E9 10 12 61 03 02 02 00 B4", "7E9 21 EA 00 00 FA FA F3 40", ...])
     * @return CVT temp count as unsigned byte (0-255), or null if parsing fails
     */
    fun parseCvtTempCount(rawLines: List<String>): Int? {
        // Validate input
        if (rawLines.isEmpty()) return null

        val firstLine = rawLines[0]
        val bytes = firstLine.trim().split("\\s+".toRegex())

        // Already-merged service data: "61 03" + hex bytes (e.g. after ISO-TP assembly elsewhere)
        if (bytes.size >= 3 &&
            bytes[0].equals("61", ignoreCase = true) &&
            bytes[1].equals("03", ignoreCase = true)
        ) {
            val rest = bytes.drop(2).mapNotNull { it.toIntOrNull(16)?.toByte() }
            val payload = byteArrayOf(0x61, 0x03) + rest.toByteArray()
            return extractNFrom2103Payload(payload)
        }

        // Need at least 3 bytes: header (7E9), PCI, and data
        if (bytes.size < 3) return null

        // Parse PCI byte (second byte after header)
        val pciStr = bytes.getOrNull(1) ?: return null
        val pci = pciStr.toIntOrNull(16) ?: return null

        // Check if it's a single frame (PCI upper nibble = 0)
        val isSingleFrame = (pci and 0xF0) == 0x00

        val payload: ByteArray? = if (isSingleFrame) {
            // Single frame: data starts after PCI byte
            // Remove header (7E9) and PCI byte, then convert hex strings to bytes
            val dataBytes = bytes.drop(2).mapNotNull { it.toIntOrNull(16)?.toByte() }
            dataBytes.toByteArray()
        } else {
            // Multi-frame: use existing decoder
            ObdPayloadDecoder.parseIsoTpMultiFrame(rawLines)
        }
        
        if (payload == null) return null
        return extractNFrom2103Payload(payload)
    }

    private fun extractNFrom2103Payload(payload: ByteArray): Int? {
        val headerIndex = indexOfSequence(payload, byteArrayOf(0x61, 0x03))
        if (headerIndex == -1) return null

        val nValueIndex = headerIndex + 2 + CVT_2103_TEMP_COUNT_BYTE_INDEX
        if (nValueIndex >= payload.size) return null

        val n = payload[nValueIndex].toInt() and 0xFF
        if (n < 0 || n > 250) {
            Log.e("CvtTempParser", "N value $n out of range 0-250")
            return null
        }
        return n
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
        return (0.000000002344 * Math.pow(n, 5.0)) +
                (-0.000001387 * Math.pow(n, 4.0)) +
                (0.0003193 * Math.pow(n, 3.0)) +
                (-0.03501 * Math.pow(n, 2.0)) +
                (2.302 * n) +
                (-36.6)
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
        return (0.0000286 * n * n * n) +
                (-0.00951 * n * n) +
                (1.46 * n) +
                (-30.1)
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