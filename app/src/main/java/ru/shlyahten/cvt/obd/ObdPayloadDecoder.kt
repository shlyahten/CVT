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

        // Find "... 61 03 ..." pattern (or generally responseMode pidHex)
        for (i in 0 until tokens.size - 1) {
            if (tokens[i] == responseMode && tokens[i + 1] == pidHex) {
                val bytes = mutableListOf<Byte>()
                var j = i + 2
                
                // After finding responseMode + pidHex, all remaining tokens are payload data.
                // PCI bytes (0x10, 0x21+, 0x02-0x07) appear BEFORE the service/PID in ISO-TP,
                // so we should NOT filter bytes after "61 03".
                // This preserves valid data bytes that may coincidentally match PCI patterns.
                
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

