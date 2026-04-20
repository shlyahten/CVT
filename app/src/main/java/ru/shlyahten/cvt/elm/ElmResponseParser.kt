package ru.shlyahten.cvt.elm

object ElmResponseParser {
    data class Parsed(
        val raw: String,
        val normalized: String,
        val isNoData: Boolean,
        val isError: Boolean,
    )

    fun parse(raw: String): Parsed {
        val noPrompt = raw.replace(">", "")
        val lines = noPrompt
            .replace("\r", "\n")
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.equals("SEARCHING...", ignoreCase = true) }
            .filterNot { it.equals("STOPPED", ignoreCase = true) }

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

