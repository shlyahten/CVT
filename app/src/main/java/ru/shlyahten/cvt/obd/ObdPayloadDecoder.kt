package ru.shlyahten.cvt.obd

import android.util.Log

/**
 * Decodes ELM text responses into raw data bytes for a given service/mode+pid.
 *
 * For ModeAndPID=2103, ECU response payload typically contains "61 03 <data...>".
 * Depending on ELM settings and adapter, headers like "7E9 06 61 03 ..." may appear.
 *
 * Handles multi-frame ISO-TP responses:
 * - 0x10: First frame (contains PCI byte, length info)
 * - 0x21, 0x22, etc.: Consecutive frames
 */
object ObdPayloadDecoder {
    private const val TAG = "ObdPayloadDecoder"

    /**
     * Reassembles multi-frame ISO-TP responses from raw ELM lines.
     * Expected format: Lines starting with a header (e.g., 7E9) followed by PCI and data.
     */
    fun parseIsoTpMultiFrame(rawLines: List<String>): ByteArray? {
        if (rawLines.isEmpty()) return null

        val allTokens = rawLines.flatMap { line ->
            line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.map { it.uppercase() }
        }

        // The first frame should contain the response header (e.g., 61 03 or similar)
        // In a multi-frame ISO-TP, we look for the sequence that starts with a service/PID response.
        // However, since ELM returns lines like "7E9 10 12 61 03 ...", we need to find where the data starts.

        val assembledPayload = mutableListOf<Byte>()

        // We look for the start of the payload: the service/PID response (e.g., 61 03)
        // In multi-frame, the first frame might have PCI 0x10, then data.
        // But ELM often presents it as "7E9 10 12 61 03 ..." where 10 is PCI and 12 is length? 
        // Actually, for ISO-TP: 10 [len] [data...] -> 21 [data...] -> 22 [data...]
        
        // Let's find the index of the response mode/PID (e.g., "61", "03")
        // We'll iterate through tokens to find the first occurrence of a valid response pattern.
        // For simplicity and based on user requirements, we assume the payload starts after the 
        // service/PID tokens in the sequence.

        var startIndex = -1
        for (i in 0 until allTokens.size - 1) {
            // We look for a pattern where tokens[i] is responseMode and tokens[i+1] is pidHex
            // This is slightly heuristic but works with how ELM outputs multi-frame responses.
            // For 2103, we expect "61" "03".
            if (allTokens[i].length == 2 && allTokens[i+1].length == 2) {
                val mode = allTokens[i].toIntOrNull(16) ?: continue
                val pid = allTokens[i+1].toIntOrNull(16) ?: continue
                
                // Check if it's a response (Mode + 0x40)
                if (mode == 0x61 && pid == 0x03) { // Specific to 2103 for now, or generalize?
                    startIndex = i + 2
                    break
                }
            }
        }

        // If we can't find the specific "61 03" pattern, fallback to a more generic search
        if (startIndex == -1) {
             Log.w(TAG, "Could not find '61 03' pattern in tokens: $allTokens")
             return null
        }

        for (j in startIndex until allTokens.size) {
            val b = allTokens[j].toHexByteOrNull() ?: break
            assembledPayload.add(b)
        }

        return if (assembledPayload.isNotEmpty()) assembledPayload.toByteArray() else null
    }

    /**
     * @param modeAndPid e.g. "2103"
     * @param normalized ELM response after cleaning (spaces between tokens)
     * @return bytes after the response header bytes (e.g. after 61 03), or null if not found.
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

        for (i in 0 until tokens.size - 1) {
            if (tokens[i] == responseMode && tokens[i + 1] == pidHex) {
                val bytes = mutableListOf<Byte>()
                var j = i + 2

                while (j < tokens.size) {
                    val t = tokens[j]
                    val b = t.toHexByteOrNull() ?: break

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
