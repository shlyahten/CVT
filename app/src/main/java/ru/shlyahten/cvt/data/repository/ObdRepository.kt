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
import ru.shlyahten.cvt.obd.CvtTempParser
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
    suspend fun connect(
        deviceAddress: String,
        fastTiming: Boolean = false,
        cacheAtsh: Boolean = true,
        canFiltering: Boolean = true,
        elmCompression: Boolean = true,
        klineOptimization: Boolean = false,
        klineLongMessages: Boolean = false,
    ): Result<Unit>

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

    /**
     * Dynamically update fast timing on active session.
     */
    fun updateFastTiming(fastTiming: Boolean)

    /**
     * Update whether ATSH header caching is enabled.
     */
    fun setCacheAtsh(cacheAtsh: Boolean)

    /**
     * Update whether CAN hardware address filtering (AT CRA) is enabled.
     */
    fun setCanFiltering(canFiltering: Boolean)

    /**
     * Update whether ELM327 data compression (ATS0/ATS1) is enabled.
     */
    fun setElmCompression(enabled: Boolean)

    /**
     * Update K-Line optimization settings.
     */
    fun setKlineOptimization(optimization: Boolean, longMessages: Boolean)
}

/**
 * Implementation of [ObdRepository] that handles Bluetooth communication and ELM327 protocol.
 */
class ObdRepositoryImpl(
    private val bluetoothAdapter: BluetoothAdapter?,
) : ObdRepository {
    
    private var connection: BluetoothSppClient.Connection? = null
    private var session: Elm327Session? = null
    private var fastTimingEnabled: Boolean = false
    private var cacheAtshEnabled: Boolean = true
    private var canFilteringEnabled: Boolean = true
    private var elmCompressionEnabled: Boolean = true
    private var klineOptimizationEnabled: Boolean = false
    private var klineLongMessagesEnabled: Boolean = false

    private fun getResponseCanId(requestHeaderHex: String): String? {
        val req = requestHeaderHex.toIntOrNull(16) ?: return null
        return (req + 8).toString(16).uppercase().padStart(3, '0')
    }
    
    override fun getBondedDevices(): List<BluetoothDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        return runCatching {
            BluetoothSppClient(adapter).getBondedDevices()
        }.getOrDefault(emptyList())
    }
    
    override suspend fun connect(
        deviceAddress: String,
        fastTiming: Boolean,
        cacheAtsh: Boolean,
        canFiltering: Boolean,
        elmCompression: Boolean,
        klineOptimization: Boolean,
        klineLongMessages: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        fastTimingEnabled = fastTiming
        cacheAtshEnabled = cacheAtsh
        canFilteringEnabled = canFiltering
        elmCompressionEnabled = elmCompression
        klineOptimizationEnabled = klineOptimization
        klineLongMessagesEnabled = klineLongMessages
        runCatching {
            val adapter = bluetoothAdapter ?: error("BluetoothAdapter is null")
            adapter.cancelDiscovery()
            
            val client = BluetoothSppClient(adapter)
            val device = adapter.getRemoteDevice(deviceAddress)
            val conn = client.connect(device)
            
            connection = conn
            val filterId = if (canFiltering) getResponseCanId("7E1") else null
            session = Elm327Session(conn.input, conn.output).apply {
                initialize(
                    headerHex = "7E1",
                    fastTiming = fastTiming,
                    canFilterHex = filterId,
                    elmCompression = elmCompression,
                    klineOptimization = klineOptimization,
                    klineLongMessages = klineLongMessages,
                )
            }
        }
    }

    override fun updateFastTiming(fastTiming: Boolean) {
        fastTimingEnabled = fastTiming
        session?.runCatching {
            configureTiming(fastTiming)
        }
    }

    override fun setCacheAtsh(cacheAtsh: Boolean) {
        cacheAtshEnabled = cacheAtsh
        if (!cacheAtsh) {
            session?.resetHeader()
        }
    }

    override fun setCanFiltering(canFiltering: Boolean) {
        canFilteringEnabled = canFiltering
        session?.runCatching {
            if (!canFiltering) {
                setCanReceiveAddress(null)
            } else {
                val header = getCurrentHeader() ?: "7E1"
                getResponseCanId(header)?.let { setCanReceiveAddress(it) }
            }
        }
    }

    override fun setElmCompression(enabled: Boolean) {
        elmCompressionEnabled = enabled
        session?.runCatching {
            configureCompression(enabled)
        }
    }

    override fun setKlineOptimization(optimization: Boolean, longMessages: Boolean) {
        klineOptimizationEnabled = optimization
        klineLongMessagesEnabled = longMessages
        session?.runCatching {
            configureKline(optimization, longMessages)
        }
    }
    
    override fun disconnect() {
        session?.resetHeader()
        runCatching { session?.close() }
        runCatching { connection?.close() }
        session = null
        connection = null
    }
    
    override fun isConnected(): Boolean = session != null
    
    override fun getSession(): Elm327Session? = session
    
    override suspend fun queryPid(spec: PidSpec): Result<Double> = withContext(Dispatchers.IO) {
        runCatching {
            val currentSession = session ?: error("Not connected to OBD device")
            
            // Ensure header is set (cached if enabled to save ~40ms round-trip)
            if (cacheAtshEnabled) {
                currentSession.setHeader(spec.headerHex)
            } else {
                currentSession.sendExpectOk("ATSH${spec.headerHex}", timeoutMs = 800)
            }

            // Ensure hardware CAN filter is configured
            if (canFilteringEnabled) {
                getResponseCanId(spec.headerHex)?.let { filterId ->
                    runCatching { currentSession.setCanReceiveAddress(filterId) }
                }
            }
            
            // Do not append " 1" because it prematurely truncates multi-frame ISO-TP CAN responses (PID 2103)
            val cmd = spec.modeAndPid.trim()
            val response = currentSession.send(cmd, timeoutMs = 2000)
            
            if (response.response.isNoData) {
                Log.w("OBD", "NO DATA for ${spec.modeAndPid}. Raw: ${response.response.raw}")
                return@runCatching throw ObdException("NO DATA response for ${spec.modeAndPid}", ObdErrorType.NoData)
            }
            if (response.response.isError) {
                return@runCatching throw ObdException(response.response.normalized.ifBlank { "ELM error" }, ObdErrorType.ProtocolError)
            }
            
            val normalized = response.response.normalized
            val req = spec.modeAndPid.trim().uppercase()
            val modeHex = if (req.length >= 2) req.substring(0, 2) else "01"
            val expMode = ((modeHex.toIntOrNull(16) ?: 0) + 0x40).toByte()
            val pidHex = if (req.length >= 4) req.substring(2, 4) else ""
            val expPid = pidHex.toIntOrNull(16)?.toByte()

            val mfPayload = ObdPayloadDecoder.parseIsoTpMultiFrame(listOf(normalized))
            val data = mfPayload
                ?.takeIf { it.size >= 2 && it[0] == expMode && (expPid == null || it[1] == expPid) }
                ?.let { payload ->
                    ObdPayloadDecoder.extractDataBytes(
                        spec.modeAndPid,
                        payload.joinToString(" ") { b -> "%02X".format(b) },
                    )
                }
                ?: ObdPayloadDecoder.extractDataBytes(spec.modeAndPid, normalized)
                ?: return@runCatching throw ObdException("No payload for ${spec.modeAndPid}", ObdErrorType.PayloadNotFound)
            
            val variables = ObdVariableMapping.fromDataBytes(data, spec.valueIndex)

            // High performance fast-path for CVT temperature equations (avoids AST/RPN allocation on every tick)
            val normalizedEq = spec.equation.replace(" ", "")
            when {
                normalizedEq == "N" -> variables["N"] ?: 0.0
                normalizedEq.contains("0.000000002344") -> {
                    CvtTempParser.convertCountToTemp1((variables["N"] ?: 0.0).toInt())
                }
                normalizedEq.contains("0.0000286") -> {
                    CvtTempParser.convertCountToTemp2((variables["N"] ?: 0.0).toInt())
                }
                else -> ExpressionEvaluator.eval(spec.equation, variables)
            }
        }
    }
    
    
    override fun close() {
        disconnect()
    }
}
