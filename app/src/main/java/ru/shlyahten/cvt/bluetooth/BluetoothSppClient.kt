package ru.shlyahten.cvt.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothSppClient(
    private val adapter: BluetoothAdapter?,
) : Closeable {

    data class Connection(
        val device: BluetoothDevice,
        val socket: BluetoothSocket,
        val input: InputStream,
        val output: OutputStream,
    ) : Closeable {
        override fun close() {
            runCatching { input.close() }
            runCatching { output.close() }
            runCatching { socket.close() }
        }
    }

    fun getBondedDevices(): List<BluetoothDevice> {
        val a = adapter ?: return emptyList()
        return a.bondedDevices?.toList().orEmpty()
    }

    fun connect(device: BluetoothDevice, uuid: UUID = SPP_UUID): Connection {
        val a = adapter ?: error("BluetoothAdapter is null")
        a.cancelDiscovery()

        var socket: BluetoothSocket? = null
        var lastError: Exception? = null

        // 1. Try standard SPP UUID
        try {
            socket = device.createRfcommSocketToServiceRecord(uuid)
            socket.connect()
        } catch (e: Exception) {
            lastError = e
            runCatching { socket?.close() }
            socket = null
        }

        // 2. Fallback to reflection on channel 1 (common for ELM327 / Teyes head units)
        if (socket == null) {
            try {
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                @Suppress("UNCHECKED_CAST")
                val fallback = method.invoke(device, 1) as BluetoothSocket
                fallback.connect()
                socket = fallback
            } catch (e: Exception) {
                lastError = e
                runCatching { socket?.close() }
                throw lastError
            }
        }

        val activeSocket = socket ?: throw (lastError ?: IllegalStateException("Failed to connect"))
        return Connection(
            device = device,
            socket = activeSocket,
            input = activeSocket.inputStream,
            output = activeSocket.outputStream,
        )
    }

    override fun close() {
        // no-op; connection is returned and should be closed by caller
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

