package ru.shlyahten.cvt.obd

object ObdVariableMapping {
    /**
     * Builds a variable map compatible with the provided Torque-like formulas.
     *
     * - AA, AB, AC, AD ... represent bytes 1..n after the response header (61 xx)
     * - A, B, C, D ... are aliases to AA, AB, AC, AD ...
     * - N aliases AA (per your CSV usage)
     */
    fun fromDataBytes(data: ByteArray): Map<String, Double> {
        val vars = HashMap<String, Double>(64)
        for (i in data.indices) {
            val v = (data[i].toInt() and 0xFF).toDouble()
            val idx = i + 1
            val nameAA = toDoubleLetter(idx) // AA, AB, AC...
            vars[nameAA] = v
        }
        // A/B/C/D aliases
        val single = listOf("A", "B", "C", "D", "E", "F", "G", "H")
        for ((i, s) in single.withIndex()) {
            val idx = i + 1
            val aa = toDoubleLetter(idx)
            vars[s] = vars[aa] ?: 0.0
        }
        // N alias to AA
        vars["N"] = vars["AA"] ?: 0.0

        return vars
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

