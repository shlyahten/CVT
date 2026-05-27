package ru.shlyahten.cvt.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Random

/**
 * Manager for Demo Mode state.
 * Handles persistence of demo mode preference using SharedPreferences.
 * Provides synthetic OBD2 data for demo mode operation.
 */
class DemoModeManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * StateFlow indicating whether demo mode is active.
     * Observed by UI components to adjust behavior.
     */
    private val _isDemoMode = MutableStateFlow(isDemoModeEnabled())
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()
    
    /**
     * Check if demo mode is enabled from persistent storage.
     * @return true if demo mode is enabled, false otherwise.
     */
    fun isDemoModeEnabled(): Boolean {
        // Default to true for first-time users (demo mode by default)
        return prefs.getBoolean(KEY_DEMO_MODE, true)
    }
    
    /**
     * Enable or disable demo mode.
     * @param enabled true to enable demo mode, false to switch to full (real Bluetooth) mode.
     */
    fun setDemoMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEMO_MODE, enabled).apply()
        _isDemoMode.value = enabled
    }
    
    /**
     * Toggle demo mode state.
     * @return the new demo mode state after toggling.
     */
    fun toggleDemoMode(): Boolean {
        val newState = !isDemoModeEnabled()
        setDemoMode(newState)
        return newState
    }
    
    /**
     * Generate synthetic CVT temperature raw count (N value for PID 2103).
     * Simulates realistic temperature sensor readings.
     * @return Raw count value (0-255) corresponding to realistic temperature range.
     */
    fun generateSyntheticCvtTempRawCount(): Int {
        // Generate a realistic raw count that maps to ~60-90°C range
        // Based on typical CVT operating temperatures
        // N ≈ 80-120 maps to roughly 60-90°C with Temp1 formula
        val baseN = 95 // Center point around ~75°C
        val variation = random.nextInt(15) - 7 // ±7 variation
        return (baseN + variation).coerceIn(0, 255)
    }
    
    /**
     * Generate synthetic oil degradation value.
     * Simulates oil condition reading from PID 2110.
     * @return Oil degradation percentage (0-100).
     */
    fun generateSyntheticOilDegradation(): Long {
        // Simulate oil degradation between 5-25% (typical for healthy vehicle)
        return random.nextLong(5, 26)
    }
    
    /**
     * Check if connected in demo mode (always returns true when demo mode is active).
     * @return true if demo mode is enabled (simulated connection), false otherwise.
     */
    fun isDemoConnected(): Boolean = isDemoModeEnabled()
    
    companion object {
        private const val PREFS_NAME = "cvt_demo_prefs"
        private const val KEY_DEMO_MODE = "demo_mode_enabled"
        private val random = Random()
    }
}
