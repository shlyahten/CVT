package ru.shlyahten.cvt.obd

/**
 * Decodes ELM text responses into raw data bytes for a given service/mode+pid.
 *
 * For ModeAndPID=2103, ECU response payload typically contains "61 03 <data...>".
 * Depending on ELM settings and adapter, headers like "7E9 06 61 03 ..." may appear.
 *
 * Handles multi-frame ISO-TP responses according to the algorithm:
 * - First frame (PCI 0x10): Remove first 2 bytes (PCI + length)
 * - Continuation frames (PCI 0x21, 0x22, etc.): Remove first byte (PCI)
 */
object ObdPayloadDecoder {
    /**
     * Reassembles multi-frame ISO-TP responses from raw ELM lines.
     * Expected format: Lines starting with CAN ID (e.g., 7E9) followed by PCI and data.
     *
     * Algorithm:
     * 1. Ignore CAN ID (7E9)
     * 2. Remove PCI bytes:
     *    - 0x10 (first frame): remove first 2 bytes (PCI + length info)
     *    - 0x21, 0x22, etc. (continuation): remove first byte (PCI)
     * 3. Assemble payload from remaining data bytes
     */
    fun parseIsoTpMultiFrame(rawLines: List<String>): ByteArray? {
        if (rawLines.isEmpty()) return null

        val allTokens = rawLines.flatMap { line ->
            line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.map { it.uppercase() }
        }

        val assembledPayload = mutableListOf<Byte>()
        var expectedPayloadLength: Int? = null
        var i = 0

        while (i < allTokens.size) {
            // Skip CAN ID (3 or 4 hex chars like "7E9" or "7EA")
            if (allTokens[i].length >= 3 && allTokens[i].all { c -> c.isDigit() || c in 'A'..'F' }) {
                i++
                continue
            }

            // Check for PCI byte
            if (allTokens[i].length == 2) {
                val pci = allTokens[i].toIntOrNull(16) ?: run { i++; continue }

                when (pci) {
                    0x10 -> {
                        expectedPayloadLength = allTokens.getOrNull(i + 1)?.toIntOrNull(16)
                        // First frame: skip PCI (0x10) and length byte
                        i += 2
                        // Add remaining data bytes from this frame until next CAN ID or PCI
                        while (i < allTokens.size) {
                            if (expectedPayloadLength != null && assembledPayload.size >= expectedPayloadLength) break
                            if (allTokens[i].length >= 3) break // Next CAN ID
                            val nextVal = allTokens[i].toIntOrNull(16) ?: break
                            if (nextVal in 0x21..0x2F) break // Next frame PCI
                            assembledPayload.add(nextVal.toByte())
                            i++
                        }
                    }
                    in 0x21..0x2F -> {
                        // Continuation frame: skip PCI byte only
                        i++
                        // Add data bytes from this frame
                        while (i < allTokens.size) {
                            if (expectedPayloadLength != null && assembledPayload.size >= expectedPayloadLength) break
                            if (allTokens[i].length >= 3) break // Next CAN ID
                            val nextVal = allTokens[i].toIntOrNull(16) ?: break
                            if (nextVal in 0x21..0x2F) break // Next frame PCI
                            assembledPayload.add(nextVal.toByte())
                            i++
                        }
                    }
                    else -> {
                        // Not a PCI byte, might be data or other token
                        i++
                    }
                }
            } else {
                i++
            }
        }

        return if (assembledPayload.isNotEmpty()) assembledPayload.toByteArray() else null
    }

    /**
     * @param modeAndPid e.g. "2103"
     * @param normalized ELM response after cleaning (spaces between tokens)
     * @return bytes after the response header bytes (e.g. after 61 03), or null if not found.
     *
     * This method handles both single-frame and multi-frame responses that have been
     * pre-processed by the ELM parser into a normalized string.
     */
    fun extractDataBytes(modeAndPid: String, normalized: String): ByteArray? {
        val req = modeAndPid.trim().uppercase()
        if (req.length < 4) return null
        val modeHex = req.substring(0, 2)
        val pidHex = req.substring(2, 4)

        val responseMode = (modeHex.toInt(16) + 0x40).toString(16).uppercase().padStart(2, '0')
        val tokens = normalized
            .replace("\n", " ")
            .trim()
            .split(Regex("\\s+"))
            .map { it.uppercase() }

        // First, try to find the pattern "responseMode pidHex" directly in the tokens
        // This handles cases where multi-frame has already been assembled or is single-frame
        for (i in 0 until tokens.size - 1) {
            if (tokens[i] == responseMode && tokens[i + 1] == pidHex) {
                val bytes = mutableListOf<Byte>()
                var j = i + 2

                while (j < tokens.size) {
                    val t = tokens[j]
                    // Skip potential PCI bytes that might appear in multi-frame raw output
                    // But keep them if they are actual data (after we've found 61 03)
                    val b = t.toHexByteOrNull() ?: break

                    // After finding 61 03, all subsequent valid hex bytes are data
                    bytes += b
                    j++
                }

                return bytes.toByteArray()
            }
        }

        return null
    }

    private fun String.toHexByteOrNull(): Byte? {
        if (length != 2) return null
        return runCatching { toInt(16).toByte() }.getOrNull()
    }
}

+++ app/src/main/java/ru/shlyahten/cvt/obd/ObdPayloadDecoder.kt (修改后)
package ru.shlyahten.cvt.obd

/**
 * Decodes ELM text responses into raw data bytes for a given service/mode+pid.
 *
 * For ModeAndPID=2103, ECU response payload typically contains "61 03 <data...>".
 * Depending on ELM settings and adapter, headers like "7E9 06 61 03 ..." may appear.
 *
 * Handles multi-frame ISO-TP responses according to the algorithm:
 * - First frame (PCI 0x10): Remove first 2 bytes (PCI + length)
 * - Continuation frames (PCI 0x21, 0x22, etc.): Remove first byte (PCI)
 */
object ObdPayloadDecoder {
    /**
     * Reassembles multi-frame ISO-TP responses from raw ELM lines.
     * Expected format: Lines with CAN ID followed by PCI and data bytes.
     * Handles both space-separated bytes (e.g., "7E9 10 12 61 03...")
     * and continuous hex strings (e.g., "7E91012610302...").
     *
     * Algorithm:
     * 1. Parse each line into bytes, handling both formats
     * 2. Skip CAN ID (first 3-4 hex chars or first token if it looks like a CAN ID)
     * 3. Remove PCI bytes:
     *    - 0x10 (first frame): remove first 2 bytes (PCI + length info)
     *    - 0x21, 0x22, etc. (continuation): remove first byte (PCI)
     * 4. Assemble payload from remaining data bytes
     */
    fun parseIsoTpMultiFrame(rawLines: List<String>): ByteArray? {
        if (rawLines.isEmpty()) return null

        val assembledPayload = mutableListOf<Byte>()
        var expectedPayloadLength: Int? = null

        for (line in rawLines) {
            val trimmedLine = line.trim().uppercase()
            if (trimmedLine.isEmpty()) continue

            // Parse line into bytes, handling both space-separated and continuous formats
            val bytes = parseLineToBytes(trimmedLine) ?: continue
            if (bytes.isEmpty()) continue

            // Find PCI byte (first byte after potential CAN ID)
            // CAN ID is typically 3 hex chars (11-bit) or 4 hex chars (29-bit) displayed as bytes
            var pciIndex = 0

            // Check if first token looks like a CAN ID (3 hex chars for 11-bit, or unusual single byte)
            // In continuous format, CAN ID is embedded in the hex string
            if (bytes.size < 2) continue

            val pci = bytes[pciIndex].toInt() and 0xFF

            when (pci) {
                0x10 -> {
                    // First frame: PCI + length byte
                    if (bytes.size < 2) continue
                    expectedPayloadLength = bytes[1].toInt() and 0xFF
                    // Skip PCI (0x10) and length byte, add remaining data
                    for (i in 2 until bytes.size) {
                        if (expectedPayloadLength != null && assembledPayload.size >= expectedPayloadLength) break
                        assembledPayload.add(bytes[i])
                    }
                }
                in 0x21..0x2F -> {
                    // Continuation frame: skip PCI byte only, add remaining data
                    for (i in 1 until bytes.size) {
                        if (expectedPayloadLength != null && assembledPayload.size >= expectedPayloadLength) break
                        assembledPayload.add(bytes[i])
                    }
                }
                else -> {
                    // Not a recognized PCI byte, might be single-frame response or already processed
                    // Try to find 61 XX pattern directly
                    continue
                }
            }
        }

        return if (assembledPayload.isNotEmpty()) assembledPayload.toByteArray() else null
    }

    /**
     * Parses a line from ELM327 output into a byte array.
     * Handles both space-separated format ("7E9 10 12...") and continuous hex format ("7E91012...").
     */
    private fun parseLineToBytes(line: String): ByteArray? {
        if (line.isEmpty()) return null

        // Check if line contains spaces (space-separated format)
        if (' ' in line || '\t' in line) {
            val tokens = line.split(Regex("\\s+")).filter { it.isNotBlank() }
            val bytes = mutableListOf<Byte>()
            for (token in tokens) {
                val cleanedToken = token.uppercase().trim()
                // Skip CAN ID tokens (3 hex chars for 11-bit IDs)
                if (cleanedToken.length == 3 && cleanedToken.all { c -> c.isDigit() || c in 'A'..'F' }) {
                    continue
                }
                // Parse 2-char hex tokens as bytes
                if (cleanedToken.length == 2) {
                    val byteValue = cleanedToken.toIntOrNull(16)
                    if (byteValue != null) {
                        bytes.add(byteValue.toByte())
                    }
                }
            }
            return if (bytes.isNotEmpty()) bytes.toByteArray() else null
        } else {
            // Continuous hex format - need to identify CAN ID and parse remaining bytes
            // Format: [CAN ID][PCI][data...]
            // For 11-bit IDs with ATH1, CAN ID is 3 hex chars, rest are 2-char bytes

            if (line.length < 3) return null

            // Assume first 3 hex chars are CAN ID (for 11-bit IDs)
            val canIdLength = 3
            if (line.length <= canIdLength) return null

            val dataPart = line.substring(canIdLength)

            // Parse remaining as 2-char hex bytes
            val bytes = mutableListOf<Byte>()
            var i = 0
            while (i + 1 < dataPart.length) {
                val hexByte = dataPart.substring(i, i + 2)
                val byteValue = hexByte.toIntOrNull(16)
                if (byteValue != null) {
                    bytes.add(byteValue.toByte())
                } else {
                    // Invalid hex, stop parsing
                    break
                }
                i += 2
            }

            return if (bytes.isNotEmpty()) bytes.toByteArray() else null
        }
    }

    /**
     * @param modeAndPid e.g. "2103"
     * @param normalized ELM response after cleaning (spaces between tokens)
     * @return bytes after the response header bytes (e.g. after 61 03), or null if not found.
     *
     * This method handles both single-frame and multi-frame responses that have been
     * pre-processed by the ELM parser into a normalized string.
     */
    fun extractDataBytes(modeAndPid: String, normalized: String): ByteArray? {
        val req = modeAndPid.trim().uppercase()
        if (req.length < 4) return null
        val modeHex = req.substring(0, 2)
        val pidHex = req.substring(2, 4)

        val responseMode = (modeHex.toInt(16) + 0x40).toString(16).uppercase().padStart(2, '0')
        val tokens = normalized
            .replace("\n", " ")
            .trim()
            .split(Regex("\\s+"))
            .map { it.uppercase() }

        // First, try to find the pattern "responseMode pidHex" directly in the tokens
        // This handles cases where multi-frame has already been assembled or is single-frame
        for (i in 0 until tokens.size - 1) {
            if (tokens[i] == responseMode && tokens[i + 1] == pidHex) {
                val bytes = mutableListOf<Byte>()
                var j = i + 2

                while (j < tokens.size) {
                    val t = tokens[j]
                    // Skip potential PCI bytes that might appear in multi-frame raw output
                    // But keep them if they are actual data (after we've found 61 03)
                    val b = t.toHexByteOrNull() ?: break

                    // After finding 61 03, all subsequent valid hex bytes are data
                    bytes += b
                    j++
                }

                return bytes.toByteArray()
            }
        }

        return null
    }

    private fun String.toHexByteOrNull(): Byte? {
        if (length != 2) return null
        return runCatching { toInt(16).toByte() }.getOrNull()
    }
}
