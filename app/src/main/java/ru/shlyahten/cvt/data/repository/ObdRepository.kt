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
import android.util.Log
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
     * Get the current Elm327Session for direct communication.
     * Returns null if not connected.
     */
    fun getSession(): Elm327Session?

    /**
     * Query a single PID and return the calculated value.
     */
    suspend fun queryPid(spec: PidSpec): Result<Double>
}

/**
 * Implementation of [ObdRepository] that handles Bluetooth communication and ELM327 protocol.
 * Supports demo mode with synthetic data generation when real OBD connection is not available.
 */
class ObdRepositoryImpl(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val demoModeManager: DemoModeManager? = null,
) : ObdRepository {
    
    private var connection: BluetoothSppClient.Connection? = null
    private var session: Elm327Session? = null
    
    override fun getBondedDevices(): List<BluetoothDevice> {
        // In demo mode, return empty list (no real devices needed)
        if (demoModeManager?.isDemoModeEnabled() == true) {
            return emptyList()
        }
        
        val adapter = bluetoothAdapter ?: return emptyList()
        return runCatching {
            BluetoothSppClient(adapter).getBondedDevices()
        }.getOrDefault(emptyList())
    }
    
    override suspend fun connect(deviceAddress: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Check if demo mode is enabled
            if (demoModeManager?.isDemoModeEnabled() == true) {
                Log.d("OBD", "Demo mode: simulated connection successful")
                // In demo mode, skip actual Bluetooth connection
                return@runCatching
            }
            
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
    
    override fun isConnected(): Boolean {
        // In demo mode, always report as connected
        if (demoModeManager?.isDemoModeEnabled() == true) {
            return true
        }
        return session != null
    }
    
    override fun getSession(): Elm327Session? = session
    
    override suspend fun queryPid(spec: PidSpec): Result<Double> = withContext(Dispatchers.IO) {
        // Check if demo mode is enabled
        if (demoModeManager?.isDemoModeEnabled() == true) {
            return@withContext generateSyntheticPidResult(spec)
        }
        
        runCatching {
            val currentSession = session ?: error("Not connected to OBD device")
            
            // Ensure header is set
            currentSession.sendExpectOk("ATSH${spec.headerHex}", timeoutMs = 800)
            
            val response = currentSession.send(spec.modeAndPid, timeoutMs = 2000)
            
            if (response.response.isNoData) {
                Log.w("OBD", "NO DATA for ${spec.modeAndPid}. Raw: ${response.response.raw}")
                return@runCatching throw ObdException("NO DATA response for ${spec.modeAndPid}", ObdErrorType.NoData)
            }
            if (response.response.isError) {
                return@runCatching throw ObdException(response.response.normalized.ifBlank { "ELM error" }, ObdErrorType.ProtocolError)
            }
            
            val normalized = response.response.normalized
            val mfPayload = ObdPayloadDecoder.parseIsoTpMultiFrame(listOf(normalized))
            val data = mfPayload
                ?.takeIf { it.size >= 2 && it[0] == 0x61.toByte() && it[1] == 0x03.toByte() }
                ?.let { payload ->
                    ObdPayloadDecoder.extractDataBytes(
                        spec.modeAndPid,
                        payload.joinToString(" ") { b -> "%02X".format(b) },
                    )
                }
                ?: ObdPayloadDecoder.extractDataBytes(spec.modeAndPid, normalized)
                ?: return@runCatching throw ObdException("No payload for ${spec.modeAndPid}", ObdErrorType.PayloadNotFound)
            
            val variables = ObdVariableMapping.fromDataBytes(data, spec.valueIndex)
            ExpressionEvaluator.eval(spec.equation, variables)
        }
    }
    
    /**
     * Generate synthetic PID results for demo mode.
     * Provides realistic fake data based on the requested PID.
     */
    private fun generateSyntheticPidResult(spec: PidSpec): Result<Double> {
        return runCatching {
            when (spec.modeAndPid) {
                "2103" -> {
                    // CVT Temperature PID - generate realistic temperature reading
                    val rawCount = demoModeManager?.generateSyntheticCvtTempRawCount() ?: 95
                    val variables = mapOf("N" to rawCount.toDouble())
                    ExpressionEvaluator.eval(spec.equation, variables)
                }
                "2110" -> {
                    // Oil degradation PID - generate realistic degradation percentage
                    val degradation = demoModeManager?.generateSyntheticOilDegradation() ?: 15L
                    degradation.toDouble()
                }
                else -> {
                    // Default: return a reasonable synthetic value
                    Log.w("OBD", "Demo mode: unknown PID ${spec.modeAndPid}, returning synthetic value")
                    50.0 // Default fallback value
                }
            }
        }
    }
    
    
    override fun close() {
        disconnect()
    }
}
