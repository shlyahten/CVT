package ru.shlyahten.cvt

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CvtApp : Application() {

    private val _cvtTemp1C = MutableStateFlow<Double?>(null)
    val cvtTemp1C: StateFlow<Double?> = _cvtTemp1C.asStateFlow()

    fun updateOverlayCvtTemp1(celsius: Double?) {
        _cvtTemp1C.value = celsius
    }
}
