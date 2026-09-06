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

        @Volatile
        private var instance: AppSettings? = null

        fun getInstance(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context.applicationContext).also { instance = it }
            }
    }
}
