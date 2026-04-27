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
    val cvtTempC: Double? = null,
    val cvtTempFormula: CvtTempFormula = CvtTempFormula.Temp1,
    val oilDegradation: Long? = null,
)

enum class CvtTempFormula { Temp1, Temp2 }

class MainViewModel : ViewModel() {
    companion object {
        private const val TAG = "MainViewModel"
    }
    
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    
    // Repository and use cases - business logic is delegated here
    private lateinit var obdRepository: ObdRepository
    private lateinit var manageConnection: ManageObdConnection
    private lateinit var readCvtTemperature: ReadCvtTemperature
    private lateinit var readOilDegradation: ReadOilDegradation
    
    private var pollJob: Job? = null
    
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
        val devices = runCatching { manageConnection.getBondedDevices() }
            .getOrDefault(emptyList())
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
                    Log.d(TAG, "=== Starting Bluetooth connection ===")
                    manageConnection.connect(address)
                        .getOrElse { throw it }
                    Log.d(TAG, "=== Connection established and ELM327 initialized ===")
                }
                _state.update { it.copy(isConnecting = false, isConnected = true, status = "Connected") }
                startPolling()
            } catch (t: Throwable) {
                Log.e(TAG, "Connection failed: ${t.message}", t)
                disconnect()
                _state.update { it.copy(isConnecting = false, isConnected = false, status = "Connect error: ${t.message}") }
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
            _state.update { it.copy(status = "Not connected") }
            return
        }
        viewModelScope.launch {
            try {
                val value = readOilDegradation.execute()
                    .getOrElse { throw it }
                _state.update { it.copy(oilDegradation = value, status = "Oil degradation read") }
            } catch (t: Throwable) {
                _state.update { it.copy(status = "Oil read error: ${t.message}") }
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                if (!manageConnection.isConnected()) break
                try {
                    val formula = state.value.cvtTempFormula
                    val tempResult = when (formula) {
                        CvtTempFormula.Temp1 -> readCvtTemperature.execute(ReadCvtTemperature.Formula.Temp1)
                        CvtTempFormula.Temp2 -> readCvtTemperature.execute(ReadCvtTemperature.Formula.Temp2)
                    }
                    val temp = tempResult.getOrElse { throw it }
                    _state.update { it.copy(cvtTempC = temp, status = "OK") }
                } catch (t: Throwable) {
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

