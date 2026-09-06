package ru.shlyahten.cvt.obd

object ObdVariableMapping {
    /**
     * Builds a variable map compatible with the provided Torque-like formulas.
     *
     * - AA, AB, AC, AD ... represent bytes 1..n after the response header (61 xx)
     * - A, B, C, D ... are aliases to AA, AB, AC, AD ...
     * - N is a special single-byte variable used in formulas, selected by valueIndex.
     *   For Mitsubishi Lancer X CVT temperature PID 2103, use [CVT_2103_TEMP_COUNT_BYTE_INDEX].
     */
    /**
     * Builds a variable map compatible with standard Torque OBD-II formulas.
     *
     * - Bytes 1..26 map to A..Z
     * - Bytes 27..52 map to AA..AZ
     * - Bytes 53..78 map to BA..BZ
     * - For backward compatibility with 4-byte mocks, AA..AD also alias to bytes 1..4 if not overridden
     * - N is the single-byte variable at valueIndex (defaults to CVT_2103_TEMP_COUNT_BYTE_INDEX for PID 2103)
     */
    fun fromDataBytes(data: ByteArray, valueIndex: Int = 0): Map<String, Double> {
        val baseMap = HashMap<String, Double>(64)

        // 1. Standard Torque variable naming: A..Z, AA..AZ, BA..BZ...
        for (i in data.indices) {
            val v = (data[i].toInt() and 0xFF).toDouble()
            val varName = toTorqueVariableName(i)
            baseMap[varName] = v
        }

        // 2. Backward compatibility: if data has <= 26 bytes, provide AA, AB, AC, AD aliases for bytes 1..4
        // to support existing test cases and shorthand formulas
        for (i in 0 until minOf(4, data.size)) {
            val alias = "A" + ('A'.code + i).toChar() // AA, AB, AC, AD
            if (!baseMap.containsKey(alias)) {
                baseMap[alias] = (data[i].toInt() and 0xFF).toDouble()
            }
        }

        // 3. N is the designated count byte (or from valueIndex)
        val nVal = if (valueIndex in data.indices) {
            (data[valueIndex].toInt() and 0xFF).toDouble()
        } else {
            baseMap["N"] ?: baseMap["A"] ?: 0.0
        }
        baseMap["N"] = nVal

        return baseMap.withDefault { 0.0 }
    }

    /**
     * 0 -> A, 1 -> B, ..., 25 -> Z, 26 -> AA, 27 -> AB, ..., 28 -> AC, 29 -> AD
     */
    fun toTorqueVariableName(index0Based: Int): String {
        if (index0Based < 26) {
            return ('A'.code + index0Based).toChar().toString()
        }
        val adjusted = index0Based - 26
        val first = adjusted / 26
        val second = adjusted % 26
        return "" + ('A'.code + first).toChar() + ('A'.code + second).toChar()
    }
}
