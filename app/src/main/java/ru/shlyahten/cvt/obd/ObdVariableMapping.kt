package ru.shlyahten.cvt.obd

object ObdVariableMapping {
    /**
     * Builds a variable map compatible with the provided Torque-like formulas.
     *
     * - AA, AB, AC, AD ... represent bytes 1..n after the response header (61 xx)
     * - A, B, C, D ... are aliases to AA, AB, AC, AD ...
     * - N is a special variable used in formulas. For Mitsubishi Lancer X CVT temperature:
     *   N = (AA << 8) | AB  (16-bit value from first two data bytes after 61 03)
     */
    fun fromDataBytes(data: ByteArray, isLancerX: Boolean = false): Map<String, Double> {
        val baseMap = HashMap<String, Double>(64)

        for (i in data.indices) {
            val v = (data[i].toInt() and 0xFF).toDouble()
            val idx = i + 1
            val nameAA = toDoubleLetter(idx) // AA, AB, AC...
            baseMap[nameAA] = v
        }

        // A/B/C/D aliases
        val single = listOf("A", "B", "C", "D", "E", "F", "G", "H")
        for ((i, s) in single.withIndex()) {
            val idx = i + 1
            val aa = toDoubleLetter(idx)
            baseMap[s] = baseMap[aa] ?: 0.0
        }

        // N calculation per algorithm:
        // N = (A << 8) | B where A=data[0](AA), B=data[1](AB)
        // This forms a 16-bit value from the first two bytes after "61 03"
        if (data.size >= 2) {
            val a = data[0].toInt() and 0xFF
            val b = data[1].toInt() and 0xFF
            val n = (a shl 8) or b
            baseMap["N"] = n.toDouble()
        } else {
            // Fallback: N aliases AA if not enough data
            baseMap["N"] = baseMap["AA"] ?: 0.0
        }

        return baseMap.withDefault { 0.0 }
    }

    // 1->AA, 2->AB, 3->AC ... 26->AZ, 27->BA...
    private fun toDoubleLetter(index1Based: Int): String {
        val i = index1Based - 1
        val first = (i / 26)
        val second = (i % 26)
        val a = 'A'.code
        return "" + (a + first).toChar() + (a + second).toChar()
    }
}
