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
import ru.shlyahten.cvt.R
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
        addLogEntry(context.getString(R.string.log_app_initialized))
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
        if (granted) {
            addLogEntry("Bluetooth permission granted")
        }
    }

    fun setFormula(formula: CvtTempFormula) {
        _state.update { it.copy(cvtTempFormula = formula) }
    }

    fun connect(context: Context) {
        val address = state.value.selectedDeviceAddress ?: return
        if (Build.VERSION.SDK_INT >= 31 && !state.value.hasConnectPermission) {
            addLogEntry(context.getString(R.string.log_missing_bluetooth_permission))
            _state.update { it.copy(status = context.getString(R.string.status_missing_bluetooth_permission)) }
            return
        }

        disconnect()
        _state.update { it.copy(isConnecting = true, status = context.getString(R.string.status_connecting)) }
        addLogEntry(context.getString(R.string.log_connecting_to_device, address))

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    Log.d(TAG, "=== Starting Bluetooth connection ===")
                    addLogEntry(context.getString(R.string.log_opening_bluetooth_connection))
                    manageConnection.connect(address)
                        .getOrElse { throw it }
                    Log.d(TAG, "=== Connection established and ELM327 initialized ===")
                    addLogEntry(context.getString(R.string.log_elm_initialized))
                }
                _state.update { it.copy(isConnecting = false, isConnected = true, status = context.getString(R.string.status_connected)) }
                addLogEntry(context.getString(R.string.log_connection_established))
                startPolling()
            } catch (t: Throwable) {
                Log.e(TAG, "Connection failed: ${t.message}", t)
                addLogEntry(context.getString(R.string.log_connection_failed, t.message))
                disconnect()
                addLogEntry(context.getString(R.string.log_disconnected))
                _state.update { it.copy(isConnecting = false, isConnected = false, status = context.getString(R.string.status_connect_error, t.message)) }
            }
        }
    }

    fun disconnect() {
        pollJob?.cancel()
        pollJob = null
        manageConnection.disconnect()
        _state.update { it.copy(isConnecting = false, isConnected = false) }
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
                    val tempName = if (formula == CvtTempFormula.Temp1) "Temp1" else "Temp2"
                    addLogEntry("Polling CVT temp ($tempName)...")
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
                    addLogEntry("Poll error ($errorCount): ${t.message}")
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

