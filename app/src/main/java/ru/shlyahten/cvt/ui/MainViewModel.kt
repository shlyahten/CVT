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
import ru.shlyahten.cvt.CvtApp
import ru.shlyahten.cvt.R
import ru.shlyahten.cvt.data.repository.DemoModeManager
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
    val floatingOverlayDesired: Boolean = false,
    val isDemoMode: Boolean = true,  // Demo mode state for UI
)

enum class CvtTempFormula { Temp1, Temp2, RawCount }

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
    private lateinit var demoModeManager: DemoModeManager

    private var cvtApp: CvtApp? = null

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
        
        // Initialize demo mode manager first
        demoModeManager = DemoModeManager(context)
        
        // Pass demo mode manager to repository for conditional behavior
        obdRepository = ObdRepositoryImpl(adapter, demoModeManager)
        manageConnection = ManageObdConnection(obdRepository)
        readCvtTemperature = ReadCvtTemperature(obdRepository)
        readOilDegradation = ReadOilDegradation(obdRepository)
        cvtApp = context.applicationContext as CvtApp
        
        // Update UI state with current demo mode status
        _state.update { it.copy(isDemoMode = demoModeManager.isDemoModeEnabled()) }
        
        addLogEntry(context.getString(R.string.log_app_initialized))
        
        // Add demo mode indicator to log
        if (demoModeManager.isDemoModeEnabled()) {
            addLogEntry("DEMO MODE ACTIVE - Using simulated OBD2 data")
        } else {
            addLogEntry("FULL MODE - Real Bluetooth OBD2 connection enabled")
        }
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

    fun setFloatingOverlayDesired(enabled: Boolean) {
        _state.update { it.copy(floatingOverlayDesired = enabled) }
    }

    /**
     * Toggle demo mode on/off.
     * When enabled, uses synthetic OBD2 data instead of real Bluetooth connection.
     * When disabled, restores full Bluetooth OBD2 functionality.
     */
    fun toggleDemoMode() {
        val newIsDemoMode = demoModeManager.toggleDemoMode()
        _state.update { it.copy(isDemoMode = newIsDemoMode) }
        
        if (newIsDemoMode) {
            addLogEntry("Switched to DEMO MODE - Using simulated OBD2 data")
            // In demo mode, disconnect from any real device and use synthetic data
            disconnect()
        } else {
            addLogEntry("Switched to FULL MODE - Real Bluetooth OBD2 connection enabled")
            // In full mode, user needs to manually connect to a device
            addLogEntry("Please select a paired device and press Connect")
        }
    }

    /**
     * Set demo mode explicitly.
     * @param enabled true for demo mode, false for full mode.
     */
    fun setDemoMode(enabled: Boolean) {
        if (demoModeManager.isDemoModeEnabled() != enabled) {
            toggleDemoMode()
        }
    }

    fun connect(context: Context) {
        val address = state.value.selectedDeviceAddress ?: return
        
        // In demo mode, simulate connection without Bluetooth
        if (demoModeManager.isDemoModeEnabled()) {
            addLogEntry("DEMO MODE: Simulating connection...")
            _state.update { it.copy(isConnecting = true, status = "Connecting (demo)...") }
            
            viewModelScope.launch {
                delay(500) // Simulate connection delay
                withContext(Dispatchers.IO) {
                    Log.d(TAG, "=== Demo mode: simulated connection ===")
                    manageConnection.connect(address)
                        .getOrElse { throw it }
                }
                _state.update { 
                    it.copy(
                        isConnecting = false, 
                        isConnected = true, 
                        status = "Connected (demo mode)"
                    ) 
                }
                addLogEntry("DEMO MODE: Connection simulated successfully")
                startPolling()
            }
            return
        }
        
        // Full mode: real Bluetooth connection
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
        cvtApp?.updateOverlayCvtTemp1(null)
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
                    val tempName = when (formula) {
                        CvtTempFormula.Temp1 -> "Temp1"
                        CvtTempFormula.Temp2 -> "Temp2"
                        CvtTempFormula.RawCount -> "RawCount"
                    }
                    addLogEntry("Polling CVT temp (2103 N → $tempName)...")
                    val rawResult = readCvtTemperature.execute(ReadCvtTemperature.Formula.RawCount)
                    val n = rawResult.getOrElse { throw it }.toInt().coerceIn(0, 255)
                    val domainFormula = when (formula) {
                        CvtTempFormula.Temp1 -> ReadCvtTemperature.Formula.Temp1
                        CvtTempFormula.Temp2 -> ReadCvtTemperature.Formula.Temp2
                        CvtTempFormula.RawCount -> ReadCvtTemperature.Formula.RawCount
                    }
                    val temp = ReadCvtTemperature.computeFromCount(n, domainFormula)
                    val temp1 = ReadCvtTemperature.computeFromCount(n, ReadCvtTemperature.Formula.Temp1)
                    if (formula != CvtTempFormula.RawCount && (temp < -30.0 || temp > 120.0)) {
                        Log.w(TAG, "Temperature out of realistic range: $temp °C (expected -30 to 120)")
                    }
                    errorCount = 0
                    cvtApp?.updateOverlayCvtTemp1(temp1)
                    val displayValue = when (formula) {
                        CvtTempFormula.Temp1, CvtTempFormula.Temp2 -> String.format("%.1f", temp)
                        CvtTempFormula.RawCount -> n.toString()
                    }
                    val unit = when (formula) {
                        CvtTempFormula.Temp1, CvtTempFormula.Temp2 -> "°C"
                        CvtTempFormula.RawCount -> "count"
                    }
                    addLogEntry("CVT temp: $displayValue $unit")
                    _state.update { it.copy(cvtTempC = temp, status = "OK") }
                } catch (t: Throwable) {
                    errorCount++
                    addLogEntry("Poll error ($errorCount): ${t.message}")
                    cvtApp?.updateOverlayCvtTemp1(null)
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

