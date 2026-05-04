package ru.shlyahten.cvt.config

/**
 * Configuration for a single PID (Parameter ID).
 * Contains all necessary information to query and decode a specific OBD parameter.
 */
data class PidConfig(
    val modeAndPid: String,           // e.g. "2103"
    val headerHex: String = "7E1",    // ECU header
    val formulas: Map<String, String>, // Named formulas for this PID
    val valueIndex: Int = 0,
    val valueLength: Int = 1
)

/**
 * Vehicle-specific configuration containing all PID definitions.
 */
data class VehicleConfig(
    val name: String,
    val tempPid: PidConfig,
    val oilDegradationPid: PidConfig? = null
)

/**
 * Central repository for vehicle configurations.
 * All vehicle-specific PID definitions and formulas are stored here.
 */
object VehicleConfigs {

    val MitsubishiLancerXCvt = VehicleConfig(
        name = "Mitsubishi Lancer X CVT",
        tempPid = PidConfig(
            modeAndPid = "2103",
            headerHex = "7E1",
            formulas = mapOf(
                "Temp1" to "(0.000000002344*(N^5))+(-0.000001387*(N^4))+(0.0003193*(N^3))+(-0.03501*(N^2))+(2.302*N)+(-36.6)",
                "Temp2" to "0.0000286*N*N*N - 0.00951*N*N + 1.46*N - 30.1"
            ),
            valueIndex = 2
        ),
        oilDegradationPid = PidConfig(
            modeAndPid = "2110",
            headerHex = "7E1",
            formulas = mapOf(
                "Default" to "AC*256+AD"
            )
        )
    )

    /**
     * Get configuration by vehicle name.
     */
    fun getByName(name: String): VehicleConfig? = when (name.lowercase()) {
        "mitsubishi lancer x cvt", "lancer x", "mitsubishi" -> MitsubishiLancerXCvt
        else -> null
    }

    /**
     * List of all available vehicle configurations.
     */
    val allConfigs: List<VehicleConfig> = listOf(MitsubishiLancerXCvt)
}
