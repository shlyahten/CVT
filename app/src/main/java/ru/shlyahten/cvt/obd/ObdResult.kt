package ru.shlyahten.cvt.obd

/**
 * Sealed class representing OBD operation results.
 * Replaces error() exceptions with proper error handling.
 */
sealed class ObdResult {
    data class Success(val value: Double) : ObdResult()
    data class Error(val message: String, val type: ObdErrorType = ObdErrorType.Unknown) : ObdResult()
    object NoData : ObdResult()
    
    fun getOrNull(): Double? = when (this) {
        is Success -> value
        else -> null
    }
    
    fun exceptionOrNull(): Throwable? = when (this) {
        is Error -> ObdException(message, type)
        else -> null
    }
    
    fun toResult(): Result<Double> = when (this) {
        is Success -> Result.success(value)
        is Error -> Result.failure(ObdException(message, type))
        NoData -> Result.failure(ObdException("NO DATA", ObdErrorType.NoData))
    }
}

enum class ObdErrorType {
    NoData,
    ProtocolError,
    PayloadNotFound,
    CalculationError,
    Unknown
}

class ObdException(message: String, val type: ObdErrorType = ObdErrorType.Unknown) : Exception(message)
