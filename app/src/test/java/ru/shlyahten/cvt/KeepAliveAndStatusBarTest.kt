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
        assertTrue("Status bar temp icon should be enabled by default", state.statusBarTempIconDesired)
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
}
