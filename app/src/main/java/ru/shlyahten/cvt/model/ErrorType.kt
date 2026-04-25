package ru.shlyahten.cvt.model

sealed class AppError {
    object None : AppError()
    data class Bluetooth(val message: String) : AppError()
    data class ElmProtocol(val message: String) : AppError()
    data class ObdData(val message: String) : AppError()
    data class Math(val message: String) : AppError()
    data class Unknown(val message: String) : AppError()

    override fun toString(): String = when (this) {
        is None -> ""
        is Bluetooth -> "Bluetooth Error: $message"
        is ElmProtocol -> "ELM327 Error: $message"
        is ObdData -> "Data Error: $message"
        is Math -> "Calculation Error: $message"
        is Unknown -> "Error: $message"
    }
}
