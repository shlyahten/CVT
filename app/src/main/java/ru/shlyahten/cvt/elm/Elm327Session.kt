package ru.shlyahten.cvt.elm

import android.util.Log
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.min

class Elm327Session(
    private val input: InputStream,
    private val output: OutputStream,
) : Closeable {

    companion object {
        private const val TAG = "Elm327Session"
    }

    data class Result(
        val command: String,
        val response: ElmResponseParser.Parsed,
    )

    fun initialize(headerHex: String = "7E1") {
        Log.d(TAG, "=== Starting ELM327 initialization ===")
        Log.d(TAG, "Header: $headerHex")
        
        // Reset + basic setup. Some adapters are slow; we keep it conservative.
        Log.d(TAG, "Sending ATZ (reset)...")
        sendExpectOk("ATZ", timeoutMs = 3000)
        
        Log.d(TAG, "Sending ETE0 (echo off)...")
        sendExpectOk("ATE0")
        
        Log.d(TAG, "Sending ATL0 (linefeeds off)...")
        sendExpectOk("ATL0")
        
        Log.d(TAG, "Sending ATS0 (spaces off)...")
        sendExpectOk("ATS0")
        
        Log.d(TAG, "Sending ATH1 (headers on)...")
        sendExpectOk("ATH1") // headers on; helps debugging and multi-ECU answers
        
        Log.d(TAG, "Sending ATSP0 (auto protocol)...")
        sendExpectOk("ATSP0") // auto protocol
        
        Log.d(TAG, "Sending ATSH$headerHex...")
        sendExpectOk("ATSH$headerHex")
        
        Log.d(TAG, "=== ELM327 initialization complete ===")
    }

    fun send(command: String, timeoutMs: Long = 1500): Result {
        val trimmed = command.trim()
        Log.d(TAG, ">>> Sending command: '$trimmed' (timeout=${timeoutMs}ms)")
        writeLine(trimmed)
        val raw = readUntilPrompt(timeoutMs = timeoutMs)
        Log.d(TAG, "<<< Raw response: '$raw'")
        val parsed = ElmResponseParser.parse(raw)
        Log.d(TAG, "<<< Parsed response: '${parsed.normalized}' (isNoData=${parsed.isNoData}, isError=${parsed.isError})")
        return Result(trimmed, parsed)
    }

    fun sendExpectOk(command: String, timeoutMs: Long = 1500) {
        Log.d(TAG, "Expecting OK for command: '$command'")
        val r = send(command, timeoutMs)
        val n = r.response.normalized.uppercase()
        Log.d(TAG, "Normalized response: '$n'")
        if (!(n == "OK" || n.endsWith(" OK"))) {
            Log.e(TAG, "ELM init failed for '$command': ${r.response.normalized}")
            Log.e(TAG, "Full raw response: ${r.response.raw}")
            error("ELM init failed for '$command': ${r.response.normalized}")
        }
        Log.d(TAG, "Command '$command' completed successfully with OK")
    }

    private fun writeLine(line: String) {
        val bytes = (line + "\r").toByteArray(Charsets.US_ASCII)
        Log.d(TAG, "Writing bytes (${bytes.size}): ${bytes.joinToString(" ") { "%02X".format(it) }}")
        output.write(bytes)
        output.flush()
        Log.d(TAG, "Bytes written and flushed")
    }

    private fun readUntilPrompt(timeoutMs: Long): String {
        Log.d(TAG, "Reading response with timeout ${timeoutMs}ms...")
        val start = System.nanoTime()
        val buf = ByteArray(256)
        val sb = StringBuilder()

        fun timedOut(): Boolean {
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            return elapsedMs > timeoutMs
        }

        var totalBytesRead = 0
        while (true) {
            if (timedOut()) {
                Log.d(TAG, "Read timeout after ${(System.nanoTime() - start) / 1_000_000}ms, total bytes: $totalBytesRead")
                break
            }

            val available = runCatching { input.available() }.getOrDefault(0)
            if (available <= 0) {
                Thread.sleep(10)
                continue
            }

            val toRead = min(buf.size, available)
            val read = input.read(buf, 0, toRead)
            if (read <= 0) break
            
            totalBytesRead += read
            val chunk = String(buf, 0, read, Charsets.US_ASCII)
            Log.d(TAG, "Read $read bytes: '$chunk'")
            sb.append(chunk)
            if (chunk.contains('>') || sb.contains(">")) {
                Log.d(TAG, "Found prompt '>', stopping read")
                break
            }
        }
        val result = sb.toString()
        Log.d(TAG, "Final response string (${result.length} chars): '$result'")
        return result
    }

    private fun StringBuilder.contains(s: String): Boolean {
        return indexOf(s) >= 0
    }

    override fun close() {
        runCatching { input.close() }
        runCatching { output.close() }
    }
}

