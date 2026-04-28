package ru.shlyahten.cvt.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import android.util.Log
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
import ru.shlyahten.cvt.data.repository.ObdRepository
import ru.shlyahten.cvt.data.repository.ObdRepositoryImpl
import ru.shlyahten.cvt.domain.usecase.ManageObdConnection
import ru.shlyahten.cvt.domain.usecase.ReadCvtTemperature
import ru.shlyahten.cvt.domain.usecase.ReadOilDegradation

data class UiState(
    val hasConnectPermission: Boolean = Build.VERSION.SDK_INT < 31,
    val bondedDevices: List<BluetoothDevice> = emptyList(),
    val selectedDeviceAddress: String? = null,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val status: String = "Idle",
    val lastRaw: String? = null,
    val logEntries: List<String> = emptyList(),
    val cvtTempC: Double? = null,
    val cvtTempFormula: CvtTempFormula = CvtTempFormula.Temp1,
    val oilDegradation: Long? = null,
)

enum class CvtTempFormula { Temp1, Temp2 }

class MainViewModel : ViewModel() {
    companion object {
        private const val TAG = "MainViewModel"
        private const val MAX_LOG_ENTRIES = 100
    }
    
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    
    // Repository and use cases - business logic is delegated here
    private lateinit var obdRepository: ObdRepository
    private lateinit var manageConnection: ManageObdConnection
    private lateinit var readCvtTemperature: ReadCvtTemperature
    private lateinit var readOilDegradation: ReadOilDegradation
    
    private var pollJob: Job? = null
    
    private fun addLogEntry(entry: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        val logLine = "[$timestamp] $entry"
        _state.update { currentState ->
            val updatedLogs = (currentState.logEntries + logLine).takeLast(MAX_LOG_ENTRIES)
            currentState.copy(logEntries = updatedLogs)
        }
    }
    
    private fun clearLog() {
        _state.update { it.copy(logEntries = emptyList()) }
    }
    
    fun clearLogPublic() {
        clearLog()
        addLogEntry("Log cleared by user")
    }
    
    /**
     * Initialize the repository and use cases with Android context.
     * Call this before using any OBD functionality.
     */
    fun initialize(context: Context) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        obdRepository = ObdRepositoryImpl(adapter)
        manageConnection = ManageObdConnection(obdRepository)
        readCvtTemperature = ReadCvtTemperature(obdRepository)
        readOilDegradation = ReadOilDegradation(obdRepository)
    }
    
    fun refreshBondedDevices() {
        addLogEntry("Refreshing bonded devices...")
        val devices = runCatching { manageConnection.getBondedDevices() }
            .getOrDefault(emptyList())
        _state.update { s ->
            s.copy(
                bondedDevices = devices.sortedBy { it.name ?: it.address },
                selectedDeviceAddress = s.selectedDeviceAddress ?: devices.firstOrNull()?.address,
                status = if (devices.isEmpty()) "No paired devices" else s.status,
            )
        }
        addLogEntry("Found ${devices.size} bonded devices")
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
            addLogEntry("Missing BLUETOOTH_CONNECT permission")
            _state.update { it.copy(status = "Missing BLUETOOTH_CONNECT permission") }
            return
        }

        disconnect()
        clearLog()
        addLogEntry("Connecting to $address...")
        _state.update { it.copy(isConnecting = true, status = "Connecting...") }

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    Log.d(TAG, "=== Starting Bluetooth connection ===")
                    addLogEntry("Opening Bluetooth connection...")
                    manageConnection.connect(address)
                        .getOrElse { throw it }
                    Log.d(TAG, "=== Connection established and ELM327 initialized ===")
                    addLogEntry("ELM327 initialized successfully")
                }
                _state.update { it.copy(isConnecting = false, isConnected = true, status = "Connected") }
                addLogEntry("Connection established")
                startPolling()
            } catch (t: Throwable) {
                Log.e(TAG, "Connection failed: ${t.message}", t)
                addLogEntry("Connection failed: ${t.message}")
                disconnect()
                _state.update { it.copy(isConnecting = false, isConnected = false, status = "Connect error: ${t.message}") }
            }
        }
    }

    fun disconnect() {
        addLogEntry("Disconnecting...")
        pollJob?.cancel()
        pollJob = null
        manageConnection.disconnect()
        _state.update { it.copy(isConnecting = false, isConnected = false) }
        addLogEntry("Disconnected")
    }

    fun readOilDegradationOnce() {
        if (!manageConnection.isConnected()) {
            addLogEntry("Cannot read oil degradation: not connected")
            _state.update { it.copy(status = "Not connected") }
            return
        }
        viewModelScope.launch {
            try {
                addLogEntry("Reading oil degradation (2110)...")
                val value = readOilDegradation.execute()
                    .getOrElse { throw it }
                addLogEntry("Oil degradation: $value")
                _state.update { it.copy(oilDegradation = value, status = "Oil degradation read") }
            } catch (t: Throwable) {
                addLogEntry("Oil read error: ${t.message}")
                _state.update { it.copy(status = "Oil read error: ${t.message}") }
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var errorCount = 0
            while (true) {
                if (!manageConnection.isConnected()) break
                try {
                    val formula = state.value.cvtTempFormula
                    addLogEntry("Polling CVT temp (${if (formula == CvtTempFormula.Temp1) "Temp1" else "Temp2"})...")
                    val tempResult = when (formula) {
                        CvtTempFormula.Temp1 -> readCvtTemperature.execute(ReadCvtTemperature.Formula.Temp1)
                        CvtTempFormula.Temp2 -> readCvtTemperature.execute(ReadCvtTemperature.Formula.Temp2)
                    }
                    val temp = tempResult.getOrElse { throw it }
                    errorCount = 0
                    addLogEntry("CVT temp: ${String.format("%.1f", temp)} °C")
                    _state.update { it.copy(cvtTempC = temp, status = "OK") }
                } catch (t: Throwable) {
                    errorCount++
                    addLogEntry("Poll error (${errorCount}): ${t.message}")
                    _state.update { it.copy(status = "Poll error: ${t.message}") }
                }
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        disconnect()
        obdRepository.close()
        super.onCleared()
    }
}

