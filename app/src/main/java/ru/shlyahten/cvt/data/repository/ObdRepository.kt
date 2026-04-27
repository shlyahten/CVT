package ru.shlyahten.cvt.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.shlyahten.cvt.bluetooth.BluetoothSppClient
import ru.shlyahten.cvt.domain.model.ConnectionState
import ru.shlyahten.cvt.elm.Elm327Session
import ru.shlyahten.cvt.obd.ExpressionEvaluator
import ru.shlyahten.cvt.obd.ObdPayloadDecoder
import ru.shlyahten.cvt.obd.ObdResult
import ru.shlyahten.cvt.obd.ObdErrorType
import ru.shlyahten.cvt.obd.ObdException
import ru.shlyahten.cvt.obd.ObdVariableMapping
import ru.shlyahten.cvt.obd.PidSpec
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import java.io.Closeable

/**
 * Repository interface for OBD operations.
 * Defines the contract for OBD data access, abstracting away Bluetooth and ELM327 details.
 */
interface ObdRepository : Closeable {
    
    /**
     * Get list of paired Bluetooth devices.
     */
    fun getBondedDevices(): List<BluetoothDevice>
    
    /**
     * Connect to an OBD device via Bluetooth.
     */
    suspend fun connect(deviceAddress: String): Result<Unit>
    
    /**
     * Disconnect from the current OBD session.
     */
    fun disconnect()
    
    /**
     * Check if currently connected.
     */
    fun isConnected(): Boolean
    
    /**
     * Query a single PID and return the calculated value.
     */
    suspend fun queryPid(spec: PidSpec): Result<Double>
    
    /**
     * Read oil degradation value (specific use case).
     */
    suspend fun readOilDegradation(): Result<Long>
}

/**
 * Implementation of [ObdRepository] that handles Bluetooth communication and ELM327 protocol.
 */
class ObdRepositoryImpl(
    private val bluetoothAdapter: BluetoothAdapter?,
) : ObdRepository {
    
    private var connection: BluetoothSppClient.Connection? = null
    private var session: Elm327Session? = null
    
    override fun getBondedDevices(): List<BluetoothDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        return runCatching {
            BluetoothSppClient(adapter).getBondedDevices()
        }.getOrDefault(emptyList())
    }
    
    override suspend fun connect(deviceAddress: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val adapter = bluetoothAdapter ?: error("BluetoothAdapter is null")
            adapter.cancelDiscovery()
            
            val client = BluetoothSppClient(adapter)
            val device = adapter.getRemoteDevice(deviceAddress)
            val conn = client.connect(device)
            
            connection = conn
            session = Elm327Session(conn.input, conn.output).apply {
                initialize(headerHex = "7E1")
            }
        }
    }
    
    override fun disconnect() {
        runCatching { session?.close() }
        runCatching { connection?.close() }
        session = null
        connection = null
    }
    
    override fun isConnected(): Boolean = session != null
    
    override suspend fun queryPid(spec: PidSpec): Result<Double> = withContext(Dispatchers.IO) {
        runCatching {
            val currentSession = session ?: error("Not connected to OBD device")
            
            // Ensure header is set
            currentSession.sendExpectOk("ATSH${spec.headerHex}", timeoutMs = 800)
            
            val response = currentSession.send(spec.modeAndPid, timeoutMs = 1500)
            
            if (response.response.isNoData) {
                return@runCatching throw ObdException("NO DATA response for ${spec.modeAndPid}", ObdErrorType.NoData)
            }
            if (response.response.isError) {
                return@runCatching throw ObdException(response.response.normalized.ifBlank { "ELM error" }, ObdErrorType.ProtocolError)
            }
            
            val data = ObdPayloadDecoder.extractDataBytes(spec.modeAndPid, response.response.normalized)
                ?: return@runCatching throw ObdException("No payload for ${spec.modeAndPid}", ObdErrorType.PayloadNotFound)
            
            val variables = ObdVariableMapping.fromDataBytes(data)
            ExpressionEvaluator.eval(spec.equation, variables)
        }
    }
    
    override suspend fun readOilDegradation(): Result<Long> = withContext(Dispatchers.IO) {
        val spec = PidSpec(
            name = "CVT oil degradation",
            modeAndPid = "2110",
            equation = "AC*256+AD",
            units = "degr",
            headerHex = "7E1",
        )
        queryPid(spec).map { it.toLong() }
    }
    
    override fun close() {
        disconnect()
    }
}
