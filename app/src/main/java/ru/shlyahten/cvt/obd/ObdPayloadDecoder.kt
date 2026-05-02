package ru.shlyahten.cvt.obd

import android.util.Log

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
    private const val TAG = "ObdPayloadDecoder"

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
                        // First frame: skip PCI (0x10) and length byte
                        i += 2
                        // Add remaining data bytes from this frame until next CAN ID or PCI
                        while (i < allTokens.size) {
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

        Log.d(TAG, "extractDataBytes: modeAndPid=$modeAndPid, tokens=${tokens.joinToString(" ")}")

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

                Log.d(TAG, "extractDataBytes: extracted ${bytes.size} bytes: ${bytes.joinToString(" ") { "%02X".format(it) }}")
                return bytes.toByteArray()
            }
        }

        Log.w(TAG, "extractDataBytes: pattern '$responseMode $pidHex' not found in response")
        return null
    }

    private fun String.toHexByteOrNull(): Byte? {
        if (length != 2) return null
        return runCatching { toInt(16).toByte() }.getOrNull()
    }
}
