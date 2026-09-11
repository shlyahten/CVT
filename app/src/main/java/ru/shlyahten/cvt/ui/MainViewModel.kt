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
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import ru.shlyahten.cvt.CvtApp
import ru.shlyahten.cvt.CvtOverlayService
import ru.shlyahten.cvt.R
import ru.shlyahten.cvt.data.AppSettings

data class DiscoveredBluetoothDevice(
    val device: BluetoothDevice,
    val name: String?,
    val address: String,
    val rssi: Short? = null,
    val isBonded: Boolean = false,
)

data class UiState(
    val hasConnectPermission: Boolean = Build.VERSION.SDK_INT < 31,
    val bondedDevices: List<BluetoothDevice> = emptyList(),
    val customDevices: List<Pair<String, String>> = emptyList(),
    val discoveredDevices: List<DiscoveredBluetoothDevice> = emptyList(),
    val isScanning: Boolean = false,
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
    val oilDegradationWeeklyDiff: Long? = null,
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
                oilDegradation = app.oilDegradation.value,
                oilDegradationWeeklyDiff = app.oilDegradationWeeklyDiff.value,
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

        // Observe weekly degradation difference
        viewModelScope.launch {
            app.oilDegradationWeeklyDiff.collectLatest { diff ->
                _state.update { it.copy(oilDegradationWeeklyDiff = diff) }
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

    private var scanReceiver: BroadcastReceiver? = null

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
        val systemDevices = try {
            adapter?.bondedDevices?.toList().orEmpty()
        } catch (e: SecurityException) {
            emptyList()
        }

        // Custom / saved devices from settings
        val customDevicePairs = settings?.getCustomDevices().orEmpty()

        // Combine system bonded devices and custom devices (reconstructed via adapter.getRemoteDevice)
        val combinedMap = LinkedHashMap<String, BluetoothDevice>()
        for (dev in systemDevices) {
            combinedMap[dev.address.uppercase()] = dev
        }
        if (adapter != null) {
            for ((addr, _) in customDevicePairs) {
                val clean = addr.trim().uppercase()
                if (!combinedMap.containsKey(clean)) {
                    runCatching {
                        adapter.getRemoteDevice(clean)
                    }.getOrNull()?.let { dev ->
                        combinedMap[clean] = dev
                    }
                }
            }
        }

        val allDevices = combinedMap.values.toList()

        // Sort devices with priority: saved device first, then OBDII / OBD devices, then others
        val sortedDevices = allDevices.sortedWith(
            compareBy<BluetoothDevice> { dev ->
                val name = dev.name ?: customDevicePairs.find { it.first.equals(dev.address, ignoreCase = true) }?.second
                settings?.getDevicePriority(name, dev.address) ?: 3
            }.thenBy { it.name ?: it.address }
        )

        _state.update { s ->
            val savedAddress = settings?.getSelectedDeviceAddress()
            val selected = when {
                s.selectedDeviceAddress != null && sortedDevices.any { it.address == s.selectedDeviceAddress } -> s.selectedDeviceAddress
                savedAddress != null && sortedDevices.any { it.address == savedAddress } -> savedAddress
                savedAddress != null -> savedAddress // Keep saved address even if not in list currently
                else -> sortedDevices.firstOrNull()?.address
            }

            if (selected != null && selected != savedAddress) {
                settings?.setSelectedDeviceAddress(selected)
                val chosenDev = sortedDevices.find { it.address == selected }
                val chosenName = chosenDev?.name ?: customDevicePairs.find { it.first.equals(selected, ignoreCase = true) }?.second
                settings?.setSelectedDeviceName(chosenName)
            }

            s.copy(
                bondedDevices = sortedDevices,
                customDevices = customDevicePairs,
                selectedDeviceAddress = selected,
                hasConnectPermission = true,
                status = if (sortedDevices.isEmpty() && selected == null) "No paired devices" else s.status,
            )
        }
    }

    fun selectDevice(address: String) {
        val cleanAddr = address.trim().uppercase()
        settings?.setSelectedDeviceAddress(cleanAddr)
        val dev = state.value.bondedDevices.find { it.address.equals(cleanAddr, ignoreCase = true) }
        val devName = dev?.name ?: state.value.customDevices.find { it.first.equals(cleanAddr, ignoreCase = true) }?.second
        settings?.setSelectedDeviceName(devName)
        _state.update { it.copy(selectedDeviceAddress = cleanAddr) }
        addLogEntry("Selected OBD device: ${devName ?: cleanAddr}")
    }

    fun addManualDevice(context: Context, address: String, name: String? = null) {
        val cleanAddr = address.trim().uppercase()
        val cleanName = if (!name.isNullOrBlank()) name.trim() else "OBDII (Manual)"
        settings?.addCustomDevice(cleanAddr, cleanName)
        settings?.setSelectedDeviceAddress(cleanAddr)
        settings?.setSelectedDeviceName(cleanName)
        addLogEntry("Added manual OBD device: $cleanName ($cleanAddr)")
        refreshBondedDevices(context)
        selectDevice(cleanAddr)
    }

    fun removeCustomDevice(context: Context, address: String) {
        settings?.removeCustomDevice(address)
        addLogEntry("Removed custom device: $address")
        refreshBondedDevices(context)
    }

    fun startDiscovery(context: Context) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            addLogEntry("Cannot scan: Bluetooth adapter is disabled or null")
            return
        }

        stopDiscovery(context)

        _state.update { it.copy(isScanning = true, discoveredDevices = emptyList()) }
        addLogEntry("Started Bluetooth device discovery...")

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                when (action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = if (Build.VERSION.SDK_INT >= 33) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        } ?: return

                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                        val devName = runCatching { device.name }.getOrNull()
                        val devAddress = device.address

                        _state.update { curr ->
                            val existingIndex = curr.discoveredDevices.indexOfFirst { it.address.equals(devAddress, ignoreCase = true) }
                            val isBonded = runCatching { device.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)
                            val discovered = DiscoveredBluetoothDevice(
                                device = device,
                                name = devName,
                                address = devAddress,
                                rssi = if (rssi != Short.MIN_VALUE) rssi else null,
                                isBonded = isBonded,
                            )
                            val updated = if (existingIndex >= 0) {
                                curr.discoveredDevices.toMutableList().apply { set(existingIndex, discovered) }
                            } else {
                                curr.discoveredDevices + discovered
                            }
                            curr.copy(discoveredDevices = updated.sortedByDescending { it.rssi ?: Short.MIN_VALUE })
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        _state.update { it.copy(isScanning = false) }
                        addLogEntry("Bluetooth device discovery finished. Found ${_state.value.discoveredDevices.size} devices.")
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        refreshBondedDevices(context)
                    }
                }
            }
        }

        scanReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        context.registerReceiver(receiver, filter)

        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            adapter.startDiscovery()
        } catch (e: SecurityException) {
            addLogEntry("Scan failed: security exception (${e.message})")
            _state.update { it.copy(isScanning = false) }
        }
    }

    fun stopDiscovery(context: Context) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        runCatching {
            if (adapter?.isDiscovering == true) {
                adapter.cancelDiscovery()
            }
        }
        scanReceiver?.let {
            runCatching { context.unregisterReceiver(it) }
            scanReceiver = null
        }
        _state.update { it.copy(isScanning = false) }
    }

    fun pairDevice(context: Context, device: BluetoothDevice) {
        addLogEntry("Initiating pairing with ${device.name ?: device.address}...")
        try {
            val bonded = device.createBond()
            if (bonded) {
                addLogEntry("Pairing request sent to ${device.address}")
            } else {
                addLogEntry("Pairing returned false for ${device.address}")
            }
        } catch (e: Exception) {
            addLogEntry("Pairing error: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanReceiver?.let {
            cvtApp?.let { app ->
                runCatching { app.unregisterReceiver(it) }
            }
            scanReceiver = null
        }
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

