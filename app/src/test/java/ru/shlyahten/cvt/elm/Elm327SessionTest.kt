package ru.shlyahten.cvt.elm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream

class Elm327SessionTest {

    /**
     * An InputStream simulator that answers commands written to a ByteArrayOutputStream.
     */
    private class SimulatedElmStream : InputStream() {
        private val queue = mutableListOf<Byte>()

        fun feedResponse(response: String) {
            for (b in response.toByteArray(Charsets.US_ASCII)) {
                queue.add(b)
            }
        }

        override fun available(): Int = queue.size

        override fun read(): Int {
            return if (queue.isNotEmpty()) {
                queue.removeAt(0).toInt() and 0xFF
            } else {
                -1
            }
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (queue.isEmpty()) return -1
            val toRead = minOf(len, queue.size)
            for (i in 0 until toRead) {
                b[off + i] = queue.removeAt(0)
            }
            return toRead
        }
    }

    @Test
    fun `setHeader sends ATSH on first call and caches it`() {
        val simulatedInput = SimulatedElmStream()
        val recordedOutput = object : ByteArrayOutputStream() {
            override fun write(b: ByteArray, off: Int, len: Int) {
                super.write(b, off, len)
                val written = String(b, off, len, Charsets.US_ASCII)
                if (written.contains("ATSH")) {
                    simulatedInput.feedResponse("OK\r\n>")
                }
            }
        }

        val session = Elm327Session(simulatedInput, recordedOutput)

        assertNull(session.getCurrentHeader())

        // First call sends ATSH7E1
        session.setHeader("7E1")
        assertEquals("7E1", session.getCurrentHeader())
        assertEquals("ATSH7E1\r", recordedOutput.toString(Charsets.US_ASCII.name()))

        // Clear output buffer
        recordedOutput.reset()

        // Second call with same header should be skipped (cached)
        session.setHeader("7E1")
        assertEquals("7E1", session.getCurrentHeader())
        assertEquals("", recordedOutput.toString(Charsets.US_ASCII.name()))

        // Third call with different header should send ATSH7E2
        session.setHeader("7E2")
        assertEquals("7E2", session.getCurrentHeader())
        assertEquals("ATSH7E2\r", recordedOutput.toString(Charsets.US_ASCII.name()))
    }

    @Test
    fun `resetHeader clears cached header`() {
        val simulatedInput = SimulatedElmStream()
        val recordedOutput = object : ByteArrayOutputStream() {
            override fun write(b: ByteArray, off: Int, len: Int) {
                super.write(b, off, len)
                simulatedInput.feedResponse("OK\r\n>")
            }
        }

        val session = Elm327Session(simulatedInput, recordedOutput)

        session.setHeader("7E1")
        assertEquals("7E1", session.getCurrentHeader())

        session.resetHeader()
        assertNull(session.getCurrentHeader())

        recordedOutput.reset()
        session.setHeader("7E1")
        assertEquals("7E1", session.getCurrentHeader())
        assertEquals("ATSH7E1\r", recordedOutput.toString(Charsets.US_ASCII.name()))
    }

    @Test
    fun `configureTiming sends ATAT2 and ATST19 when fastTiming is true`() {
        val simulatedInput = SimulatedElmStream()
        val recordedOutput = object : ByteArrayOutputStream() {
            override fun write(b: ByteArray, off: Int, len: Int) {
                super.write(b, off, len)
                simulatedInput.feedResponse("OK\r\n>")
            }
        }

        val session = Elm327Session(simulatedInput, recordedOutput)

        session.configureTiming(fastTiming = true)
        val commands = recordedOutput.toString(Charsets.US_ASCII.name())
        assertEquals("ATAT2\rATST19\r", commands)
    }

    @Test
    fun `configureTiming sends ATAT1 and ATST32 when fastTiming is false`() {
        val simulatedInput = SimulatedElmStream()
        val recordedOutput = object : ByteArrayOutputStream() {
            override fun write(b: ByteArray, off: Int, len: Int) {
                super.write(b, off, len)
                simulatedInput.feedResponse("OK\r\n>")
            }
        }

        val session = Elm327Session(simulatedInput, recordedOutput)

        session.configureTiming(fastTiming = false)
        val commands = recordedOutput.toString(Charsets.US_ASCII.name())
        assertEquals("ATAT1\rATST32\r", commands)
    }
}
