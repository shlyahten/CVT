package ru.shlyahten.cvt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shlyahten.cvt.ui.UiState

class KeepAliveAndStatusBarTest {

    @Test
    fun testKeepAliveAndStatusBarDefaults() {
        val state = UiState()
        assertTrue("Persistent Keep-Alive should be enabled by default for head units", state.keepAliveDesired)
        assertTrue("Persistent notification should be enabled by default", state.persistentNotificationDesired)
        assertTrue("Status bar temp icon alias should be enabled by default", state.statusBarTempIconDesired)
        assertTrue("Autoconnect should be enabled by default", state.autoconnectDesired)
    }

    @Test
    fun testServiceConstants() {
        assertEquals("ru.shlyahten.cvt.action.STANDBY", CvtOverlayService.ACTION_STANDBY)
        assertEquals("ru.shlyahten.cvt.ACTION_CVT_TEMP_UPDATE", CvtOverlayService.ACTION_CVT_TEMP_UPDATE)
        assertEquals("extra_temp_celsius", CvtOverlayService.EXTRA_TEMP_CELSIUS)
        assertEquals("extra_temp_int", CvtOverlayService.EXTRA_TEMP_INT)
        assertEquals("extra_status", CvtOverlayService.EXTRA_STATUS)
        assertEquals("extra_connected", CvtOverlayService.EXTRA_CONNECTED)
    }

    @Test
    fun testErrorCountOnlyIncrementsAfterConnectionInSession() {
        val app = CvtApp()
        assertEquals(0, app.errorCount.value)
        assertEquals(false, app.hasConnectedInSession.value)

        // Errors occurring before connection in session must NOT increment error count
        app.updateConnectionMetrics(latencyMs = null, errorIncrement = true, errorMsg = "Adapter not found")
        assertEquals(0, app.errorCount.value)
        assertEquals("Adapter not found", app.lastError.value)

        // Once session is connected, error count remains 0 until an actual error occurs
        app.setSessionConnected(true)
        assertTrue(app.hasConnectedInSession.value)
        assertEquals(0, app.errorCount.value)

        // Errors occurring after successful connection in session MUST increment error count
        app.updateConnectionMetrics(latencyMs = 50L, errorIncrement = true, errorMsg = "OBD query timeout")
        assertEquals(1, app.errorCount.value)
        assertEquals("OBD query timeout", app.lastError.value)

        app.updateConnectionMetrics(latencyMs = 55L, errorIncrement = true, errorMsg = "CAN frame dropped")
        assertEquals(2, app.errorCount.value)

        // Ending session resets error count and session connection state
        app.setSessionConnected(false)
        assertEquals(0, app.errorCount.value)
        assertEquals(false, app.hasConnectedInSession.value)
        assertEquals(null, app.lastError.value)
    }
}
