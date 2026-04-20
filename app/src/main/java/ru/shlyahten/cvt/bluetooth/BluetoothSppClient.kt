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

        val socket = device.createRfcommSocketToServiceRecord(uuid)
        socket.connect()
        return Connection(
            device = device,
            socket = socket,
            input = socket.inputStream,
            output = socket.outputStream,
        )
    }

    override fun close() {
        // no-op; connection is returned and should be closed by caller
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}

