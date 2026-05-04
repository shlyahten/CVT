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
        // Both formulas use the same PID but different equations.
        // For PID 2103, N is a single byte at data[2] after response header 61 03.
        val spec = createPidSpec(formula)

        // Attempt reading with one retry on NO DATA
        var result = obdRepository.queryPid(spec)

        if (result.isFailure && result.exceptionOrNull() is ObdException &&
            (result.exceptionOrNull() as ObdException).type == ObdErrorType.NoData) {

            Log.w("CVT_TEMP", "First attempt failed with NO DATA. Retrying in 500ms...")
            delay(500)
            result = obdRepository.queryPid(spec)
        }

        // Final range validation per algorithm: T ∈ [-30; 120]
        if (result.isSuccess) {
            val temp = result.getOrThrow()
            if (temp < -30.0 || temp > 120.0) {
                Log.w("CVT_TEMP", "Temperature out of realistic range: $temp °C (expected -30 to 120)")
            }
        }

        result
    }

    /**
     * Creates a PidSpec for CVT temperature based on the selected formula.
     */
    private fun createPidSpec(formula: Formula): PidSpec {
        return when (formula) {
            Formula.Temp1 -> PidSpec(
                name = "CVT temp 1",
                modeAndPid = "2103",
                equation = "(0.000000002344*(N^5))+(-0.000001387*(N^4))+(0.0003193*(N^3))+(-0.03501*(N^2))+(2.302*N)+(-36.6)",
                units = "°C",
                headerHex = "7E1",
                valueIndex = 2,
            )
            Formula.Temp2 -> PidSpec(
                name = "CVT temp 2",
                modeAndPid = "2103",
                equation = "0.0000286*N*N*N - 0.00951*N*N + 1.46*N - 30.1",
                units = "°C",
                headerHex = "7E1",
                valueIndex = 2,
            )
        }
    }
}

/**
 * Use case for reading oil degradation from the vehicle.
 * Supports both standard and test degradation calculation methods.
 */
class ReadOilDegradation(
    private val obdRepository: ObdRepository,
) {

    enum class Formula {
        Default,
        Test
    }

    /**
     * Execute oil degradation reading with the specified formula.
     * @param formula The formula to use for degradation calculation.
     * @return Result containing the degradation value, or an error.
     */
    suspend fun execute(formula: Formula = Formula.Default): Result<Long> = withContext(Dispatchers.IO) {
        // Get the vehicle config for oil degradation formulas
        val config = VehicleConfigs.getByName("Mitsubishi Lancer X CVT")
            ?: error("Vehicle config not found")
        
        val oilConfig = config.oilDegradationPid
            ?: error("Oil degradation config not found")
        
        // Create PidSpec based on selected formula
        val spec = when (formula) {
            Formula.Default -> PidSpec(
                name = "CVT oil degradation",
                modeAndPid = oilConfig.modeAndPid,
                equation = oilConfig.formulas["Default"] ?: error("Default formula not found"),
                units = "degr",
                headerHex = oilConfig.headerHex,
                valueIndex = 0, // For oil degradation, we use A and B bytes (indices 0,1)
            )
            Formula.Test -> PidSpec(
                name = "CVT oil degradation test",
                modeAndPid = oilConfig.modeAndPid,
                equation = oilConfig.formulas["Test"] ?: error("Test formula not found"),
                units = "degr",
                headerHex = oilConfig.headerHex,
                valueIndex = 0, // For oil degradation test, we use A, B, and C bytes (indices 0,1,2)
            )
        }

        // Query the PID and convert result to Long
        obdRepository.queryPid(spec).map { it.toLong() }
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
