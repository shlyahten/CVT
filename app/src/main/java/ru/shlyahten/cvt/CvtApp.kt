package ru.shlyahten.cvt

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.shlyahten.cvt.data.AppSettings

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

    private val _oilDegradation = MutableStateFlow<Long?>(null)
    val oilDegradation: StateFlow<Long?> = _oilDegradation.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        appSettings = AppSettings.getInstance(this)
    }

    fun updateOverlayCvtTemp1(celsius: Double?) {
        _cvtTemp1C.value = celsius
    }

    fun updateData(temp: Double?, count: Int?, connected: Boolean, status: String) {
        _cvtTemp1C.value = temp
        _cvtTempCount.value = count
        _isConnected.value = connected
        _connectionStatus.value = status
    }

    fun updateOilDegradation(degradation: Long?) {
        _oilDegradation.value = degradation
    }
}

