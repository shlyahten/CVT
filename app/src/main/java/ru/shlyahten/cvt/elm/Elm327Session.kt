package ru.shlyahten.cvt.elm

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.min

class Elm327Session(
    private val input: InputStream,
    private val output: OutputStream,
) : Closeable {

    data class Result(
        val command: String,
        val response: ElmResponseParser.Parsed,
    )

    fun initialize(headerHex: String = "7E1") {
        // Reset + basic setup. Some adapters are slow; we keep it conservative.
        sendExpectOk("ATZ", timeoutMs = 3000)
        sendExpectOk("ATE0")
        sendExpectOk("ATL0")
        sendExpectOk("ATS0")
        sendExpectOk("ATH1") // headers on; helps debugging and multi-ECU answers
        sendExpectOk("ATSP0") // auto protocol
        sendExpectOk("ATSH$headerHex")
    }

    fun send(command: String, timeoutMs: Long = 1500): Result {
        val trimmed = command.trim()
        writeLine(trimmed)
        val raw = readUntilPrompt(timeoutMs = timeoutMs)
        return Result(trimmed, ElmResponseParser.parse(raw))
    }

    fun sendExpectOk(command: String, timeoutMs: Long = 1500) {
        val r = send(command, timeoutMs)
        val n = r.response.normalized.uppercase()
        if (!(n == "OK" || n.endsWith(" OK"))) {
            error("ELM init failed for '$command': ${r.response.normalized}")
        }
    }

    private fun writeLine(line: String) {
        val bytes = (line + "\r").toByteArray(Charsets.US_ASCII)
        output.write(bytes)
        output.flush()
    }

    private fun readUntilPrompt(timeoutMs: Long): String {
        val start = System.nanoTime()
        val buf = ByteArray(256)
        val sb = StringBuilder()

        fun timedOut(): Boolean {
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            return elapsedMs > timeoutMs
        }

        while (true) {
            if (timedOut()) break

            val available = runCatching { input.available() }.getOrDefault(0)
            if (available <= 0) {
                Thread.sleep(10)
                continue
            }

            val toRead = min(buf.size, available)
            val read = input.read(buf, 0, toRead)
            if (read <= 0) break
            val chunk = String(buf, 0, read, Charsets.US_ASCII)
            sb.append(chunk)
            if (chunk.contains('>') || sb.contains(">")) break
        }
        return sb.toString()
    }

    private fun StringBuilder.contains(s: String): Boolean {
        return indexOf(s) >= 0
    }

    override fun close() {
        runCatching { input.close() }
        runCatching { output.close() }
    }
}

