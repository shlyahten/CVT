package ru.shlyahten.cvt.elm

import android.util.Log
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

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

    private var currentHeaderHex: String? = null

    fun getCurrentHeader(): String? = currentHeaderHex

    fun resetHeader() {
        currentHeaderHex = null
    }

    fun setHeader(headerHex: String) {
        val clean = headerHex.trim().uppercase()
        if (currentHeaderHex != clean) {
            Log.d(TAG, "Setting ATSH$clean (previous: $currentHeaderHex)...")
            sendExpectOk("ATSH$clean", timeoutMs = 800)
            currentHeaderHex = clean
        } else {
            Log.d(TAG, "ATSH$clean is already cached, skipping")
        }
    }

    fun configureTiming(fastTiming: Boolean) {
        if (fastTiming) {
            Log.d(TAG, "Configuring fast timing: ATAT2 (aggressive) + ATST19 (100ms timeout)...")
            sendExpectOk("ATAT2")
            sendExpectOk("ATST19")
        } else {
            Log.d(TAG, "Configuring standard timing: ATAT1 (standard) + ATST32 (200ms timeout)...")
            sendExpectOk("ATAT1")
            sendExpectOk("ATST32")
        }
    }

    fun initialize(headerHex: String = "7E1", fastTiming: Boolean = false) {
        Log.d(TAG, "=== Starting ELM327 initialization (fastTiming=$fastTiming) ===")
        Log.d(TAG, "Header: $headerHex")
        currentHeaderHex = null

        // Reset + basic setup per algorithm requirements for CVT ECU communication
        Log.d(TAG, "Sending ATZ (reset)...")
        sendExpectOk("ATZ", timeoutMs = 3000)

        Log.d(TAG, "Sending ATE0 (echo off)...")
        sendExpectOk("ATE0")

        Log.d(TAG, "Sending ATL0 (linefeeds off)...")
        sendExpectOk("ATL0")

        Log.d(TAG, "Sending ATS0 (spaces off)...")
        sendExpectOk("ATS0")

        Log.d(TAG, "Sending ATH1 (headers on)...")
        sendExpectOk("ATH1")

        Log.d(TAG, "Sending ATSP6 (ISO 15765-4 CAN)...")
        sendExpectOk("ATSP6") // ISO 15765-4 CAN (11bit 500k)

        configureTiming(fastTiming)

        setHeader(headerHex)

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

        // ATZ (Reset) returns the firmware version instead of "OK"
        val isAtz = command.trim().uppercase() == "ATZ"
        val isOk = n == "OK" || n.endsWith(" OK")

        if (!isAtz && !isOk) {
            Log.e(TAG, "ELM init failed for '$command': ${r.response.normalized}")
            Log.e(TAG, "Full raw response: ${r.response.raw}")
            error("ELM init failed for '$command': ${r.response.normalized}")
        }
        Log.d(TAG, "Command '$command' completed successfully")
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
        val start = System.currentTimeMillis()
        val sb = StringBuilder()

        try {
            while (System.currentTimeMillis() - start < timeoutMs) {
                if (input.available() > 0) {
                    val buf = ByteArray(256)
                    val read = input.read(buf)
                    if (read == -1) break

                    val chunk = String(buf, 0, read, Charsets.US_ASCII)
                    Log.d(TAG, "Read $read bytes: '$chunk'")
                    sb.append(chunk)

                    if (chunk.contains('>')) {
                        Log.d(TAG, "Found prompt '>', stopping read")
                        break
                    }
                } else {
                    Thread.sleep(5)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading from input stream", e)
        }

        val result = sb.toString()
        Log.d(TAG, "Final response string (${result.length} chars): '$result'")
        return result
    }

    override fun close() {
        runCatching { input.close() }
        runCatching { output.close() }
    }
}
