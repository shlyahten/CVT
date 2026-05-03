package ru.shlyahten.cvt.obd

data class PidSpec(
    val name: String,
    val modeAndPid: String, // e.g. "2103"
    val equation: String, // e.g. "(0.0000286*N*N*N)+..."
    val units: String,
    val headerHex: String = "7E1",
    val valueIndex: Int = 0,
    val valueLength: Int = 1,
)
