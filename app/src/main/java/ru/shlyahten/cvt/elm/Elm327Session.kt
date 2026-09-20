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
    private var currentFilterHex: String? = null

    fun getCurrentHeader(): String? = currentHeaderHex
    fun getCurrentFilter(): String? = currentFilterHex

    fun resetHeader() {
        currentHeaderHex = null
        currentFilterHex = null
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

    fun setCanReceiveAddress(filterHex: String?) {
        val clean = filterHex?.trim()?.uppercase()
        if (currentFilterHex != clean) {
            if (clean.isNullOrBlank()) {
                Log.d(TAG, "Resetting CAN receive address filter (ATAR)...")
                sendExpectOk("ATAR", timeoutMs = 800)
                currentFilterHex = null
            } else {
                Log.d(TAG, "Setting CAN receive address filter: ATCRA$clean (previous: $currentFilterHex)...")
                sendExpectOk("ATCRA$clean", timeoutMs = 800)
                currentFilterHex = clean
            }
        }
    }

    fun configureTiming(fastTiming: Boolean) {
        if (fastTiming) {
            // Use ATAT1 instead of ATAT2: ATAT2 aggressively truncates multi-frame ISO-TP CAN responses (PID 2103)
            Log.d(TAG, "Configuring fast timing: ATAT1 (adaptive) + ATST19 (100ms timeout)...")
            sendExpectOk("ATAT1")
            sendExpectOk("ATST19")
        } else {
            Log.d(TAG, "Configuring standard timing: ATAT1 (standard) + ATST32 (200ms timeout)...")
            sendExpectOk("ATAT1")
            sendExpectOk("ATST32")
        }
    }

    fun configureCompression(compress: Boolean) {
        if (compress) {
            Log.d(TAG, "Enabling ELM327 data compression (ATS0 - spaces off)...")
            sendExpectOk("ATS0")
        } else {
            Log.d(TAG, "Disabling ELM327 data compression (ATS1 - spaces on)...")
            sendExpectOk("ATS1")
        }
    }

    fun configureKline(optimization: Boolean, longMessages: Boolean) {
        if (optimization) {
            Log.d(TAG, "Configuring K-Line optimizations: ATSW20 + ATIB10...")
            runCatching { sendExpectOk("ATSW20", timeoutMs = 800) }
            runCatching { sendExpectOk("ATIB10", timeoutMs = 800) }
        }
        if (longMessages) {
            Log.d(TAG, "Enabling long K-Line messages (ATAL)...")
            runCatching { sendExpectOk("ATAL", timeoutMs = 800) }
        } else {
            runCatching { sendExpectOk("ATNL", timeoutMs = 800) }
        }
    }

    fun initialize(
        headerHex: String = "7E1",
        fastTiming: Boolean = false,
        canFilterHex: String? = null,
        elmCompression: Boolean = true,
        klineOptimization: Boolean = false,
        klineLongMessages: Boolean = false,
    ) {
        Log.d(TAG, "=== Starting ELM327 initialization (fastTiming=$fastTiming, canFilter=$canFilterHex, compression=$elmCompression, klineOpt=$klineOptimization, klineLong=$klineLongMessages) ===")
        Log.d(TAG, "Header: $headerHex")
        currentHeaderHex = null
        currentFilterHex = null

        // Reset + basic setup per algorithm requirements for CVT ECU communication
        Log.d(TAG, "Sending ATZ (reset)...")
        sendExpectOk("ATZ", timeoutMs = 3000)

        Log.d(TAG, "Sending ATE0 (echo off)...")
        sendExpectOk("ATE0")

        Log.d(TAG, "Sending ATL0 (linefeeds off)...")
        sendExpectOk("ATL0")

        if (elmCompression) {
            Log.d(TAG, "Sending ATS0 (spaces off)...")
            sendExpectOk("ATS0")
        } else {
            Log.d(TAG, "Sending ATS1 (spaces on)...")
            sendExpectOk("ATS1")
        }

        Log.d(TAG, "Sending ATH1 (headers on)...")
        sendExpectOk("ATH1")

        Log.d(TAG, "Sending ATSP6 (ISO 15765-4 CAN)...")
        sendExpectOk("ATSP6") // ISO 15765-4 CAN (11bit 500k)

        configureTiming(fastTiming)
        configureKline(klineOptimization, klineLongMessages)

        setHeader(headerHex)

        if (!canFilterHex.isNullOrBlank()) {
            runCatching {
                setCanReceiveAddress(canFilterHex)
            }.onFailure {
                Log.w(TAG, "ATCRA not supported by adapter: ${it.message}")
            }
        }

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
        val start = System.currentTimeMillis()
        val sb = StringBuilder()
        val buf = ByteArray(512)

        try {
            while (System.currentTimeMillis() - start < timeoutMs) {
                val available = input.available()
                if (available > 0) {
                    val read = input.read(buf, 0, minOf(buf.size, available))
                    if (read == -1) break

                    val chunk = String(buf, 0, read, Charsets.US_ASCII)
                    sb.append(chunk)

                    if (chunk.contains('>')) {
                        break
                    }
                } else {
                    Thread.sleep(2)
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
