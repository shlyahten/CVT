package ru.shlyahten.cvt

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.shlyahten.cvt.data.AppSettings

enum class BtStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR,
}

class CvtApp : Application() {

    lateinit var appSettings: AppSettings
        private set

    private val _cvtTemp1C = MutableStateFlow<Double?>(null)
    val cvtTemp1C: StateFlow<Double?> = _cvtTemp1C.asStateFlow()

    private val _cvtTempCount = MutableStateFlow<Int?>(null)
    val cvtTempCount: StateFlow<Int?> = _cvtTempCount.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Idle")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _btStatus = MutableStateFlow(BtStatus.DISCONNECTED)
    val btStatus: StateFlow<BtStatus> = _btStatus.asStateFlow()

    private val _connectionLatencyMs = MutableStateFlow<Long?>(null)
    val connectionLatencyMs: StateFlow<Long?> = _connectionLatencyMs.asStateFlow()

    private val _errorCount = MutableStateFlow(0)
    val errorCount: StateFlow<Int> = _errorCount.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _oilDegradation = MutableStateFlow<Long?>(null)
    val oilDegradation: StateFlow<Long?> = _oilDegradation.asStateFlow()

    private val _oilDegradationWeeklyDiff = MutableStateFlow<Long?>(null)
    val oilDegradationWeeklyDiff: StateFlow<Long?> = _oilDegradationWeeklyDiff.asStateFlow()

    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        _connectionStatus.value = getString(R.string.status_idle)
        appSettings = AppSettings.getInstance(this)
        val lastDegradation = appSettings.getLastOilDegradation()
        if (lastDegradation != null) {
            _oilDegradation.value = lastDegradation
            _oilDegradationWeeklyDiff.value = appSettings.getWeeklyDegradationIncrease(lastDegradation)
        }
    }

    fun setDemoMode(active: Boolean) {
        _isDemoMode.value = active
    }

    fun updateOverlayCvtTemp1(celsius: Double?) {
        _cvtTemp1C.value = celsius
    }

    fun updateBtStatus(status: BtStatus) {
        _btStatus.value = status
    }

    fun updateConnectionMetrics(
        latencyMs: Long?,
        errorIncrement: Boolean = false,
        errorMsg: String? = null,
        resetErrors: Boolean = false,
    ) {
        _connectionLatencyMs.value = latencyMs
        if (resetErrors) {
            _errorCount.value = 0
            _lastError.value = null
        } else {
            if (errorIncrement) {
                _errorCount.value += 1
            }
            if (errorMsg != null) {
                _lastError.value = errorMsg
            }
        }
    }

    fun updateData(temp: Double?, count: Int?, connected: Boolean, status: String) {
        _cvtTemp1C.value = temp
        _cvtTempCount.value = count
        _isConnected.value = connected
        _connectionStatus.value = status
        if (!connected) {
            _connectionLatencyMs.value = null
        }
    }

    fun updateOilDegradation(degradation: Long?) {
        _oilDegradation.value = degradation
        if (degradation != null) {
            appSettings.recordOilDegradation(degradation)
            _oilDegradationWeeklyDiff.value = appSettings.getWeeklyDegradationIncrease(degradation)
        } else {
            _oilDegradationWeeklyDiff.value = null
        }
    }
}

