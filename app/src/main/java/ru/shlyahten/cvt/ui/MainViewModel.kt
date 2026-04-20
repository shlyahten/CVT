package ru.shlyahten.cvt.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.shlyahten.cvt.bluetooth.BluetoothSppClient
import ru.shlyahten.cvt.elm.Elm327Session
import ru.shlyahten.cvt.obd.ExpressionEvaluator
import ru.shlyahten.cvt.obd.ObdPayloadDecoder
import ru.shlyahten.cvt.obd.ObdVariableMapping
import ru.shlyahten.cvt.obd.PidSpec

data class UiState(
    val hasConnectPermission: Boolean = Build.VERSION.SDK_INT < 31,
    val bondedDevices: List<BluetoothDevice> = emptyList(),
    val selectedDeviceAddress: String? = null,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val status: String = "Idle",
    val lastRaw: String? = null,
    val cvtTempC: Double? = null,
    val cvtTempFormula: CvtTempFormula = CvtTempFormula.Temp1,
    val oilDegradation: Long? = null,
)

enum class CvtTempFormula { Temp1, Temp2 }

class MainViewModel : ViewModel() {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var session: Elm327Session? = null
    private var connection: BluetoothSppClient.Connection? = null
    private var pollJob: Job? = null

    fun refreshBondedDevices(context: Context) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val client = BluetoothSppClient(adapter)
        val devices = runCatching { client.getBondedDevices() }.getOrDefault(emptyList())
        _state.update { s ->
            s.copy(
                bondedDevices = devices.sortedBy { it.name ?: it.address },
                selectedDeviceAddress = s.selectedDeviceAddress ?: devices.firstOrNull()?.address,
                status = if (devices.isEmpty()) "No paired devices" else s.status,
            )
        }
    }

    fun selectDevice(address: String) {
        _state.update { it.copy(selectedDeviceAddress = address) }
    }

    fun setHasConnectPermission(granted: Boolean) {
        _state.update { it.copy(hasConnectPermission = granted) }
    }

    fun setFormula(formula: CvtTempFormula) {
        _state.update { it.copy(cvtTempFormula = formula) }
    }

    fun connect(context: Context) {
        val address = state.value.selectedDeviceAddress ?: return
        if (Build.VERSION.SDK_INT >= 31 && !state.value.hasConnectPermission) {
            _state.update { it.copy(status = "Missing BLUETOOTH_CONNECT permission") }
            return
        }

        disconnect()
        _state.update { it.copy(isConnecting = true, status = "Connecting...") }

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val adapter = BluetoothAdapter.getDefaultAdapter()
                    val device = adapter.getRemoteDevice(address)
                    val client = BluetoothSppClient(adapter)
                    val conn = client.connect(device)
                    connection = conn
                    session = Elm327Session(conn.input, conn.output).also { it.initialize(headerHex = "7E1") }
                }
                _state.update { it.copy(isConnecting = false, isConnected = true, status = "Connected") }
                startPolling()
            } catch (t: Throwable) {
                disconnect()
                _state.update { it.copy(isConnecting = false, isConnected = false, status = "Connect error: ${t.message}") }
            }
        }
    }

    fun disconnect() {
        pollJob?.cancel()
        pollJob = null
        runCatching { session?.close() }
        runCatching { connection?.close() }
        session = null
        connection = null
        _state.update { it.copy(isConnecting = false, isConnected = false) }
    }

    fun readOilDegradationOnce() {
        val s = session ?: run {
            _state.update { it.copy(status = "Not connected") }
            return
        }
        viewModelScope.launch {
            try {
                val value = withContext(Dispatchers.IO) {
                    val spec = PidSpec(
                        name = "CVT oil degradation",
                        modeAndPid = "2110",
                        equation = "AC*256+AD",
                        units = "degr",
                        headerHex = "7E1",
                    )
                    queryPid(s, spec)
                }
                _state.update { it.copy(oilDegradation = value.toLong(), status = "Oil degradation read") }
            } catch (t: Throwable) {
                _state.update { it.copy(status = "Oil read error: ${t.message}") }
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                val s = session ?: break
                try {
                    val spec = when (state.value.cvtTempFormula) {
                        CvtTempFormula.Temp1 -> PidSpec(
                            name = "CVT temp 1",
                            modeAndPid = "2103",
                            equation = "(0.000000002344*(N^5))+(-0.000001387*(N^4))+(0.0003193*(N^3))+(-0.03501*(N^2))+(2.302*N)+(-36.6)",
                            units = "°C",
                            headerHex = "7E1",
                        )
                        CvtTempFormula.Temp2 -> PidSpec(
                            name = "CVT temp 2",
                            modeAndPid = "2103",
                            equation = "(0.0000286*N*N*N)+(-0.00951*N*N)+(1.46*N)+(-30.1)",
                            units = "°C",
                            headerHex = "7E1",
                        )
                    }
                    val temp = withContext(Dispatchers.IO) { queryPid(s, spec) }
                    _state.update { it.copy(cvtTempC = temp, status = "OK") }
                } catch (t: Throwable) {
                    _state.update { it.copy(status = "Poll error: ${t.message}") }
                }
                delay(1000)
            }
        }
    }

    private fun queryPid(session: Elm327Session, spec: PidSpec): Double {
        // Ensure header is set (cheap + safe).
        session.sendExpectOk("ATSH${spec.headerHex}", timeoutMs = 800)

        val r = session.send(spec.modeAndPid, timeoutMs = 1500)
        _state.update { it.copy(lastRaw = r.response.raw) }
        if (r.response.isNoData) error("NO DATA")
        if (r.response.isError) error(r.response.normalized.ifBlank { "ELM error" })

        val data = ObdPayloadDecoder.extractDataBytes(spec.modeAndPid, r.response.normalized)
            ?: error("No payload for ${spec.modeAndPid}")

        val vars = ObdVariableMapping.fromDataBytes(data)
        return ExpressionEvaluator.eval(spec.equation, vars)
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }
}

