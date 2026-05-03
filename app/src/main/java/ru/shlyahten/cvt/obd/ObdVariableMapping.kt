package ru.shlyahten.cvt.obd

object ObdVariableMapping {
    /**
     * Builds a variable map compatible with the provided Torque-like formulas.
     *
     * - AA, AB, AC, AD ... represent bytes 1..n after the response header (61 xx)
     * - A, B, C, D ... are aliases to AA, AB, AC, AD ...
     * - N is a special single-byte variable used in formulas, selected by valueIndex.
     *   For Mitsubishi Lancer X CVT temperature PID 2103, valueIndex is 2.
     */
    fun fromDataBytes(data: ByteArray, valueIndex: Int = 0): Map<String, Double> {
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

        // N is one byte, not an aggregate of the payload.
        baseMap["N"] = data.getOrNull(valueIndex)?.let { (it.toInt() and 0xFF).toDouble() } ?: 0.0

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
