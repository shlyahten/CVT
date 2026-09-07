package ru.shlyahten.cvt.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.shlyahten.cvt.ui.CvtTempFormula

class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _autostartFlow = MutableStateFlow(isAutostartEnabled())
    val autostartFlow: StateFlow<Boolean> = _autostartFlow.asStateFlow()

    private val _overlayFlow = MutableStateFlow(isOverlayEnabled())
    val overlayFlow: StateFlow<Boolean> = _overlayFlow.asStateFlow()

    private val _formulaFlow = MutableStateFlow(getFormula())
    val formulaFlow: StateFlow<CvtTempFormula> = _formulaFlow.asStateFlow()

    fun getSelectedDeviceAddress(): String? =
        prefs.getString(KEY_DEVICE_ADDRESS, null)

    fun setSelectedDeviceAddress(address: String?) {
        prefs.edit().putString(KEY_DEVICE_ADDRESS, address).apply()
    }

    fun getSelectedDeviceName(): String? =
        prefs.getString(KEY_DEVICE_NAME, null)

    fun setSelectedDeviceName(name: String?) {
        prefs.edit().putString(KEY_DEVICE_NAME, name).apply()
    }

    fun isAutostartEnabled(): Boolean =
        prefs.getBoolean(KEY_AUTOSTART, false)

    fun setAutostartEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOSTART, enabled).apply()
        _autostartFlow.value = enabled
    }

    fun isAutoconnectEnabled(): Boolean =
        prefs.getBoolean(KEY_AUTOCONNECT, true)

    fun setAutoconnectEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOCONNECT, enabled).apply()
    }

    fun isOverlayEnabled(): Boolean =
        prefs.getBoolean(KEY_OVERLAY_ENABLED, true)

    fun setOverlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply()
        _overlayFlow.value = enabled
    }

    fun getFormula(): CvtTempFormula {
        val name = prefs.getString(KEY_FORMULA, CvtTempFormula.Temp1.name)
        return runCatching { CvtTempFormula.valueOf(name ?: CvtTempFormula.Temp1.name) }
            .getOrDefault(CvtTempFormula.Temp1)
    }

    fun setFormula(formula: CvtTempFormula) {
        prefs.edit().putString(KEY_FORMULA, formula.name).apply()
        _formulaFlow.value = formula
    }

    fun getOverlayX(): Int = prefs.getInt(KEY_OVERLAY_X, 24)
    fun getOverlayY(): Int = prefs.getInt(KEY_OVERLAY_Y, 120)

    fun setOverlayPosition(x: Int, y: Int) {
        prefs.edit().putInt(KEY_OVERLAY_X, x).putInt(KEY_OVERLAY_Y, y).apply()
    }

    fun getPollIntervalMs(): Long = prefs.getLong(KEY_POLL_INTERVAL_MS, 1000L)

    fun setPollIntervalMs(ms: Long) {
        prefs.edit().putLong(KEY_POLL_INTERVAL_MS, ms).apply()
    }

    fun isOnboardingCompleted(): Boolean =
        prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    fun getLastOilDegradation(): Long? {
        if (!prefs.contains(KEY_LAST_OIL_DEGRADATION)) return null
        return prefs.getLong(KEY_LAST_OIL_DEGRADATION, 0L)
    }

    fun getDegradationHistory(): List<DegradationSample> {
        val raw = prefs.getString(KEY_OIL_DEGRADATION_HISTORY, null)
        return DegradationCalculator.parseHistory(raw)
    }

    fun recordOilDegradation(value: Long, timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_OIL_DEGRADATION, value).apply()
        val history = getDegradationHistory().toMutableList()
        val updated = DegradationCalculator.addSample(history, value, timestamp)
        val serialized = DegradationCalculator.serializeHistory(updated)
        prefs.edit().putString(KEY_OIL_DEGRADATION_HISTORY, serialized).apply()
    }

    fun getWeeklyDegradationIncrease(currentValue: Long, now: Long = System.currentTimeMillis()): Long {
        return DegradationCalculator.calculateWeeklyIncrease(getDegradationHistory(), currentValue, now)
    }

    fun getDevicePriority(name: String?, address: String): Int {
        return BluetoothDevicePrioritizer.getPriority(name, address, getSelectedDeviceAddress())
    }

    companion object {
        private const val PREFS_NAME = "cvt_monitor_prefs"
        private const val KEY_DEVICE_ADDRESS = "pref_device_address"
        private const val KEY_DEVICE_NAME = "pref_device_name"
        private const val KEY_AUTOSTART = "pref_autostart"
        private const val KEY_AUTOCONNECT = "pref_autoconnect"
        private const val KEY_OVERLAY_ENABLED = "pref_overlay_enabled"
        private const val KEY_FORMULA = "pref_formula"
        private const val KEY_OVERLAY_X = "pref_overlay_x"
        private const val KEY_OVERLAY_Y = "pref_overlay_y"
        private const val KEY_POLL_INTERVAL_MS = "pref_poll_interval_ms"
        private const val KEY_ONBOARDING_COMPLETED = "pref_onboarding_completed"
        private const val KEY_LAST_OIL_DEGRADATION = "pref_last_oil_degradation"
        private const val KEY_OIL_DEGRADATION_HISTORY = "pref_oil_degradation_history"

        @Volatile
        private var instance: AppSettings? = null

        fun getInstance(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context.applicationContext).also { instance = it }
            }
    }
}

data class DegradationSample(
    val timestamp: Long,
    val value: Long,
)

object DegradationCalculator {
    fun parseHistory(raw: String?): List<DegradationSample> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                val ts = parts[0].toLongOrNull()
                val v = parts[1].toLongOrNull()
                if (ts != null && v != null) DegradationSample(ts, v) else null
            } else null
        }
    }

    fun serializeHistory(history: List<DegradationSample>): String {
        return history.joinToString(";") { "${it.timestamp}:${it.value}" }
    }

    fun addSample(
        history: List<DegradationSample>,
        value: Long,
        timestamp: Long = System.currentTimeMillis()
    ): List<DegradationSample> {
        val list = history.toMutableList()
        val cutoff = timestamp - 180L * 24 * 3600 * 1000L
        list.removeAll { it.timestamp < cutoff }

        val last = list.lastOrNull()
        val oneHourMs = 60 * 60 * 1000L
        if (last != null && last.value == value && (timestamp - last.timestamp) < oneHourMs) {
            list[list.lastIndex] = DegradationSample(timestamp, value)
        } else {
            list.add(DegradationSample(timestamp, value))
        }
        return list
    }

    fun calculateWeeklyIncrease(
        history: List<DegradationSample>,
        currentValue: Long,
        now: Long = System.currentTimeMillis()
    ): Long {
        if (history.isEmpty()) return 0L

        val oneWeekMs = 7L * 24 * 3600 * 1000L
        val targetTime = now - oneWeekMs
        val oneDayMs = 24L * 3600 * 1000L

        val olderSamples = history.filter { it.timestamp <= (now - oneDayMs) }
        val baselineSample = if (olderSamples.isNotEmpty()) {
            olderSamples.minByOrNull { kotlin.math.abs(it.timestamp - targetTime) }
        } else {
            history.firstOrNull()
        }

        if (baselineSample == null) return 0L
        val diff = currentValue - baselineSample.value
        return diff.coerceAtLeast(0L)
    }
}

object BluetoothDevicePrioritizer {
    fun getPriority(name: String?, address: String, savedAddress: String?): Int {
        if (address.equals(savedAddress, ignoreCase = true)) return 0
        if (name.isNullOrBlank()) return 4
        val clean = name.trim().uppercase()
        return when {
            clean == "OBDII" || clean == "OBD2" || clean == "OBD-II" -> 1
            clean.startsWith("OBDII") || clean.startsWith("OBD2") || clean.contains("OBD") -> 2
            else -> 3
        }
    }
}


