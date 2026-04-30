package ru.shlyahten.cvt.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.shlyahten.cvt.data.repository.ObdRepository
import ru.shlyahten.cvt.obd.PidSpec
import ru.shlyahten.cvt.obd.ObdPayloadDecoder
import ru.shlyahten.cvt.obd.ObdException
import ru.shlyahten.cvt.obd.ObdErrorType
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
                equation = "CUSTOM_CVT_TEMP_FORMULA",
                units = "°C",
                headerHex = "7E9",
            )
        }

        if (formula == Formula.Temp2) {
            calculateCvtTempMitsubishiLancerX()
        } else {
            obdRepository.queryPid(spec)
        }
    }

    /**
     * Calculate CVT oil temperature for Mitsubishi Lancer X using PID "2103".
     *
     * This method:
     * 1. Parses multi-frame ISO-TP response for "2103" (header "7E9", response "61 03")
     * 2. Extracts two bytes: A = byte[0], B = byte[1] after "61 03"
     * 3. Computes N = (A << 8) | B
     * 4. Applies polynomial formula using Horner's method:
     *    T = 0.0000286*N^3 - 0.00951*N^2 + 1.46*N - 30.1
     *    = ((0.0000286*N - 0.00951)*N + 1.46)*N - 30.1
     * 5. Returns temperature in °C as float
     *
     * Handles ISO-TP reassembly (0x10, 0x21, 0x22 frames).
     * Includes null/length safety checks and range validation (-20°C to 120°C).
     */
    private suspend fun calculateCvtTempMitsubishiLancerX(): Result<Double> = withContext(Dispatchers.IO) {
        runCatching {
            val modeAndPid = "2103"
            val headerHex = "7E9"

            // Ensure header is set
            val currentSession = obdRepository.getSession()
            currentSession?.sendExpectOk("ATSH$headerHex", timeoutMs = 800)

            val response = currentSession?.send(modeAndPid, timeoutMs = 2000)

            if (response?.response?.isNoData == true) {
                Log.w("CVT_TEMP", "NO DATA for $modeAndPid. Raw: ${response.response.raw}")
                throw ObdException("NO DATA response for $modeAndPid", ObdErrorType.NoData)
            }
            if (response?.response?.isError == true) {
                throw ObdException(response.response.normalized.ifBlank { "ELM error" }, ObdErrorType.ProtocolError)
            }

            val normalizedResponse = response?.response?.normalized ?: ""
            Log.d("CVT_TEMP", "Raw normalized response: $normalizedResponse")

            // Extract data bytes after "61 03"
            val data = ObdPayloadDecoder.extractDataBytes(modeAndPid, normalizedResponse)
                ?: throw ObdException("No payload for $modeAndPid", ObdErrorType.PayloadNotFound)

            // Safety check: need at least 2 bytes for temperature calculation
            if (data.size < 2) {
                Log.e("CVT_TEMP", "Insufficient data bytes: ${data.size}, expected >= 2")
                throw ObdException("Insufficient data bytes: ${data.size}", ObdErrorType.PayloadNotFound)
            }

            // Extract bytes A and B (first two bytes after "61 03")
            val byteA = data[0].toInt() and 0xFF
            val byteB = data[1].toInt() and 0xFF

            // Compute N = (A << 8) | B (big-endian 16-bit value)
            val n = (byteA shl 8) or byteB

            Log.d("CVT_TEMP", "Byte A: 0x${byteA.toString(16).uppercase()}, Byte B: 0x${byteB.toString(16).uppercase()}, N: $n")

            // Apply polynomial formula using Horner's method for efficiency:
            // T = 0.0000286*N^3 - 0.00951*N^2 + 1.46*N - 30.1
            // Horner's form: T = ((0.0000286*N - 0.00951)*N + 1.46)*N - 30.1
            val nDouble = n.toDouble()
            val temperature = ((0.0000286 * nDouble - 0.00951) * nDouble + 1.46) * nDouble - 30.1

            Log.d("CVT_TEMP", "Computed temperature: $temperature °C")

            // Validate against realistic range (-20°C to 120°C)
            if (temperature < -20.0 || temperature > 120.0) {
                Log.w("CVT_TEMP", "Temperature out of realistic range: $temperature °C")
            }

            temperature
        }
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
     * Get list of paired Bluetooth devices.
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
