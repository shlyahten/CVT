package ru.shlyahten.cvt.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.shlyahten.cvt.CvtApp
import ru.shlyahten.cvt.CvtOverlayService
import ru.shlyahten.cvt.R
import ru.shlyahten.cvt.data.AppSettings

data class UiState(
    val hasConnectPermission: Boolean = Build.VERSION.SDK_INT < 31,
    val bondedDevices: List<BluetoothDevice> = emptyList(),
    val selectedDeviceAddress: String? = null,
    val isServiceRunning: Boolean = false,
    val isConnected: Boolean = false,
    val status: String = "Idle",
    val lastRaw: String? = null,
    val logEntries: List<String> = emptyList(),
    val cvtTempC: Double? = null,
    val cvtTempCount: Int? = null,
    val cvtTempFormula: CvtTempFormula = CvtTempFormula.Temp1,
    val oilDegradation: Long? = null,
    val floatingOverlayDesired: Boolean = true,
    val autostartDesired: Boolean = false,
    val autoconnectDesired: Boolean = true,
    val isDemoMode: Boolean = false,
    val demoCycleActive: Boolean = true,
    val demoPresetTemp: Double? = null,
)

enum class CvtTempFormula { Temp1, Temp2, RawCount }

class MainViewModel : ViewModel() {
    companion object {
        private const val MAX_LOG_ENTRIES = 80
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var cvtApp: CvtApp? = null
    private var settings: AppSettings? = null

    private fun addLogEntry(entry: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        val logLine = "[$timestamp] $entry"
        _state.update { currentState ->
            val updatedLogs = (currentState.logEntries + logLine).takeLast(MAX_LOG_ENTRIES)
            currentState.copy(logEntries = updatedLogs)
        }
    }

    fun clearLogPublic() {
        _state.update { it.copy(logEntries = emptyList()) }
        addLogEntry("Log cleared by user")
    }

    fun initialize(context: Context) {
        val app = context.applicationContext as CvtApp
        cvtApp = app
        val s = AppSettings.getInstance(context)
        settings = s

        val savedAddress = s.getSelectedDeviceAddress()
        val savedFormula = s.getFormula()
        val savedOverlay = s.isOverlayEnabled()
        val savedAutostart = s.isAutostartEnabled()
        val savedAutoconnect = s.isAutoconnectEnabled()

        _state.update {
            it.copy(
                selectedDeviceAddress = savedAddress,
                cvtTempFormula = savedFormula,
                floatingOverlayDesired = savedOverlay,
                autostartDesired = savedAutostart,
                autoconnectDesired = savedAutoconnect,
            )
        }

        val hasBtPermission = if (Build.VERSION.SDK_INT >= 31) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        _state.update { it.copy(hasConnectPermission = hasBtPermission) }

        if (hasBtPermission) {
            refreshBondedDevices(context)
        }

        // Observe background service running state
        viewModelScope.launch {
            CvtOverlayService.isRunningFlow.collectLatest { running ->
                _state.update { it.copy(isServiceRunning = running) }
                if (running) {
                    addLogEntry(context.getString(R.string.screen_main_status_running))
                } else {
                    addLogEntry(context.getString(R.string.screen_main_status_stopped))
                }
            }
        }

        // Observe temperature data from CvtApp
        viewModelScope.launch {
            app.cvtTemp1C.collectLatest { temp ->
                _state.update { it.copy(cvtTempC = temp) }
            }
        }

        viewModelScope.launch {
            app.cvtTempCount.collectLatest { count ->
                _state.update { it.copy(cvtTempCount = count) }
            }
        }

        // Observe connection state from CvtApp
        viewModelScope.launch {
            app.isConnected.collectLatest { connected ->
                _state.update { it.copy(isConnected = connected) }
            }
        }

        // Observe connection status text
        viewModelScope.launch {
            app.connectionStatus.collectLatest { status ->
                _state.update { it.copy(status = status) }
                addLogEntry("Status: $status")
            }
        }

        // Observe oil degradation
        viewModelScope.launch {
            app.oilDegradation.collectLatest { degr ->
                _state.update { it.copy(oilDegradation = degr) }
                if (degr != null) {
                    addLogEntry("Oil degradation: $degr")
                }
            }
        }

        // Observe demo mode state
        viewModelScope.launch {
            CvtOverlayService.isDemoModeFlow.collectLatest { demo ->
                _state.update { it.copy(isDemoMode = demo) }
                if (demo) {
                    addLogEntry("Demo mode active")
                }
            }
        }

        addLogEntry(context.getString(R.string.log_app_initialized))
    }

    fun refreshBondedDevices(context: Context? = null) {
        val hasBtPermission = if (Build.VERSION.SDK_INT >= 31) {
            if (context != null) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                state.value.hasConnectPermission
            }
        } else {
            true
        }

        if (!hasBtPermission) {
            _state.update { s ->
                s.copy(
                    hasConnectPermission = false,
                    status = context?.getString(R.string.status_missing_bluetooth_permission) ?: "Missing Bluetooth permission",
                )
            }
            return
        }

        val bluetoothManager = context?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        val devices = try {
            adapter?.bondedDevices?.toList().orEmpty()
        } catch (e: SecurityException) {
            emptyList()
        }

        _state.update { s ->
            val selected = s.selectedDeviceAddress ?: devices.firstOrNull()?.address
            if (s.selectedDeviceAddress == null && selected != null) {
                settings?.setSelectedDeviceAddress(selected)
            }
            s.copy(
                bondedDevices = devices.sortedBy { it.name ?: it.address },
                selectedDeviceAddress = selected,
                hasConnectPermission = true,
                status = if (devices.isEmpty()) "No paired devices" else s.status,
            )
        }
    }

    fun selectDevice(address: String) {
        settings?.setSelectedDeviceAddress(address)
        val dev = state.value.bondedDevices.find { it.address == address }
        settings?.setSelectedDeviceName(dev?.name)
        _state.update { it.copy(selectedDeviceAddress = address) }
        addLogEntry("Selected OBD device: ${dev?.name ?: address}")
    }

    fun setHasConnectPermission(granted: Boolean) {
        _state.update { it.copy(hasConnectPermission = granted) }
        if (granted) {
            addLogEntry("Bluetooth permission granted")
        }
    }

    fun setFormula(formula: CvtTempFormula) {
        settings?.setFormula(formula)
        _state.update { it.copy(cvtTempFormula = formula) }
        addLogEntry("Formula set to ${formula.name}")
    }

    fun setFloatingOverlayDesired(context: Context, enabled: Boolean) {
        settings?.setOverlayEnabled(enabled)
        _state.update { it.copy(floatingOverlayDesired = enabled) }
        addLogEntry("Floating widget ${if (enabled) "enabled" else "disabled"}")
    }

    fun setAutostartDesired(enabled: Boolean) {
        settings?.setAutostartEnabled(enabled)
        _state.update { it.copy(autostartDesired = enabled) }
        addLogEntry("Autostart ${if (enabled) "enabled" else "disabled"}")
    }

    fun setAutoconnectDesired(enabled: Boolean) {
        settings?.setAutoconnectEnabled(enabled)
        _state.update { it.copy(autoconnectDesired = enabled) }
        addLogEntry("Autoconnect ${if (enabled) "enabled" else "disabled"}")
    }

    fun connect(context: Context) {
        val address = state.value.selectedDeviceAddress
        if (address.isNullOrBlank()) {
            addLogEntry("Cannot connect: no device selected")
            return
        }
        addLogEntry("Starting CVT monitor service for $address...")
        CvtOverlayService.start(context)
    }

    fun disconnect(context: Context) {
        addLogEntry("Stopping CVT monitor service...")
        CvtOverlayService.stop(context)
    }

    fun readOilDegradationOnce(context: Context) {
        if (!state.value.isConnected) {
            addLogEntry("Cannot read oil degradation: OBD not connected")
            return
        }
        addLogEntry("Requesting oil degradation (PID 2110)...")
        CvtOverlayService.readOilDegradation(context)
    }

    fun startDemoMode(context: Context, cycle: Boolean = true, fixedTemp: Double? = null) {
        _state.update {
            it.copy(
                demoCycleActive = cycle,
                demoPresetTemp = fixedTemp,
                floatingOverlayDesired = true,
            )
        }
        addLogEntry("Starting Demo Mode (cycle=$cycle, temp=$fixedTemp)...")
        CvtOverlayService.startDemo(context, cycle = cycle, fixedTemp = fixedTemp)
    }

    fun stopDemoMode(context: Context) {
        addLogEntry("Stopping Demo Mode...")
        CvtOverlayService.stop(context)
        _state.update { it.copy(isDemoMode = false) }
    }

    fun setDemoPresetTemp(context: Context, temp: Double) {
        _state.update {
            it.copy(
                demoCycleActive = false,
                demoPresetTemp = temp,
            )
        }
        addLogEntry("Demo preset set to $temp°C")
        if (state.value.isDemoMode) {
            CvtOverlayService.setDemoTemp(context, temp)
        } else {
            startDemoMode(context, cycle = false, fixedTemp = temp)
        }
    }

    fun setDemoCycle(context: Context) {
        _state.update {
            it.copy(
                demoCycleActive = true,
                demoPresetTemp = null,
            )
        }
        addLogEntry("Demo switched to auto-cycle")
        startDemoMode(context, cycle = true, fixedTemp = null)
    }
}

