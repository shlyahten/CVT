package ru.shlyahten.cvt.elm

object ElmResponseParser {
    data class Parsed(
        val raw: String,
        val normalized: String,
        val isNoData: Boolean,
        val isError: Boolean,
    )

    /**
     * With ATS0, ELM may return one token per line: 3-digit CAN id + an even number of hex digits (e.g. `7E910126103020200B4`).
     * Split into space-separated byte tokens so downstream parsers can find `61 03` and reassemble ISO-TP.
     */
    fun expandCompactElmCanLine(line: String): String {
        val t = line.trim()
        if (t.isEmpty() || ' ' in t) return t
        if (t.length < 11) return t
        if (!t.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }) return t
        val head = t.take(3)
        val rest = t.drop(3)
        if (rest.isEmpty() || rest.length % 2 != 0) return t
        if (!rest.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }) return t
        return buildString {
            append(head.uppercase())
            var i = 0
            while (i < rest.length) {
                append(' ')
                append(rest.substring(i, i + 2).uppercase())
                i += 2
            }
        }
    }

    fun parse(raw: String): Parsed {
        val noPrompt = raw.replace(">", "")
        val lines = noPrompt
            .replace("\r", "\n")
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.equals("SEARCHING...", ignoreCase = true) }
            .filterNot { it.equals("STOPPED", ignoreCase = true) }
            .map { expandCompactElmCanLine(it) }

        val normalized = lines.joinToString(" ").trim()
        val upper = normalized.uppercase()
        val isNoData = upper.contains("NO DATA") || upper.contains("UNABLE TO CONNECT")
        val isError = upper.startsWith("ERROR") ||
            upper.contains("?") ||
            upper.contains("CAN ERROR") ||
            upper.contains("BUS INIT") ||
            upper.contains("BUS ERROR")

        return Parsed(
            raw = raw,
            normalized = normalized,
            isNoData = isNoData,
            isError = isError,
        )
    }
}

