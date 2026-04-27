package ru.shlyahten.cvt.domain.model

/**
 * Domain model representing OBD sensor data.
 */
data class ObdSensorData(
    val pidName: String,
    val modeAndPid: String,
    val value: Double,
    val units: String,
)

/**
 * Domain model for oil degradation reading.
 */
data class OilDegradationData(
    val degradation: Long,
    val units: String = "degr"
)

/**
 * Connection state for Bluetooth/OBD session.
 */
sealed class ConnectionState {
    object Idle : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val deviceAddress: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
