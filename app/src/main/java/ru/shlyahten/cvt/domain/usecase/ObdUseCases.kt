package ru.shlyahten.cvt.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ru.shlyahten.cvt.data.repository.ObdRepository
import ru.shlyahten.cvt.obd.*
import android.util.Log

/**
 * Use case for reading CVT temperature from the vehicle.
 * Encapsulates the business logic for temperature calculation using different formulas.
 */
class ReadCvtTemperature(
    private val obdRepository: ObdRepository,
) {

    enum class Formula {
        Temp1,
        Temp2
    }

    /**
     * Execute CVT temperature reading with the specified formula.
     * @param formula The formula to use for temperature calculation.
     * @return Result containing the temperature value in Celsius, or an error.
     */
    suspend fun execute(formula: Formula): Result<Double> = withContext(Dispatchers.IO) {
        val spec = when (formula) {
            Formula.Temp1 -> PidSpec(
                name = "CVT temp 1",
                modeAndPid = "2103",
                equation = "(0.000000002344*(N^5))+(-0.000001387*(N^4))+(0.0003193*(N^3))+(-0.03501*(N^2))+(2.302*N)+(-36.6)",
                units = "°C",
                headerHex = "7E1",
            )
            Formula.Temp2 -> PidSpec(
                name = "CVT temp 2",
                modeAndPid = "2103",
                equation = "(0.0000286*(N^3))+(-0.00951*(N^2))+(1.46*N)+(-30.1)",
                units = "°C",
                headerHex = "7E9",
            )
        }

        // Attempt reading with one retry on NO DATA
        var result = obdRepository.queryPid(spec)

        if (result.isFailure && result.exceptionOrNull() is ObdException &&
            (result.exceptionOrNull() as ObdException).type == ObdErrorType.NoData) {

            Log.w("CVT_TEMP", "First attempt failed with NO DATA. Retrying in 500ms...")
            delay(500)
            result = obdRepository.queryPid(spec)
        }
        // Final range validation for Temp2 if it was calculated via queryPid
        if (result.isSuccess && formula == Formula.Temp2) {
            val temp = result.getOrThrow()
            if (temp < -50.0 || temp > 250.0) {
                Log.w("CVT_TEMP", "Temperature out of realistic range: $temp °C")
            }
        }

        result
    }
}

/**
 * Use case for reading oil degradation from the vehicle.
 */
class ReadOilDegradation(
    private val obdRepository: ObdRepository,
) {
    /**
     * Execute oil degradation reading.
     * @return Result containing the degradation value, or an error.
     */
    suspend fun execute(): Result<Long> {
        return obdRepository.readOilDegradation()
    }
}

/**
 * Use case for managing Bluetooth connection to OBD device.
 */
class ManageObdConnection(
    private val obdRepository: ObdRepository,
) {
    /**
     * Get list of bonded devices.
     */
    fun getBondedDevices() = obdRepository.getBondedDevices()

    /**
     * Connect to an OBD device.
     * @param deviceAddress The Bluetooth address of the device.
     * @return Result indicating success or failure.
     */
    suspend fun connect(deviceAddress: String): Result<Unit> {
        return obdRepository.connect(deviceAddress)
    }

    /**
     * Disconnect from the current OBD session.
     */
    fun disconnect() {
        obdRepository.disconnect()
    }

    /**
     * Check if currently connected.
     */
    fun isConnected(): Boolean {
        return obdRepository.isConnected()
    }
}
