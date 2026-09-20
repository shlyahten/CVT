package ru.shlyahten.cvt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shlyahten.cvt.ui.UiState

class BluetoothAutoEnableTest {

    @Test
    fun testUiStateBluetoothDefaults() {
        val defaultState = UiState()
        assertTrue("Auto-enable Bluetooth should be enabled by default for Teyes head units", defaultState.autoEnableBluetoothDesired)
        assertTrue("Bluetooth should assume enabled initially until checked", defaultState.isBluetoothEnabled)
        assertFalse("Bluetooth should not be in enabling state initially", defaultState.isBluetoothEnabling)
    }

    @Test
    fun testDevicePrioritizationWithAutoSelection() {
        val savedAddr = "AA:BB:CC:DD:EE:FF"
        
        // When saved address matches, it should have the highest priority (0)
        assertEquals(0, BluetoothDevicePrioritizer.getPriority("OBDII", savedAddr, savedAddr))
        assertEquals(0, BluetoothDevicePrioritizer.getPriority("Other BT", savedAddr, savedAddr))

        // When no saved address matches, OBD devices should precede general devices
        val obd1Priority = BluetoothDevicePrioritizer.getPriority("OBDII", "11:22:33:44:55:66", null)
        val obd2Priority = BluetoothDevicePrioritizer.getPriority("OBD2-ELM327", "22:33:44:55:66:77", null)
        val phonePriority = BluetoothDevicePrioritizer.getPriority("Pixel 7", "33:44:55:66:77:88", null)

        assertTrue(obd1Priority < phonePriority)
        assertTrue(obd2Priority < phonePriority)
    }

    @Test
    fun testTeyesAccOnActions() {
        assertEquals("com.glsx.boot.ACCON", ru.shlyahten.cvt.BootReceiver.ACTION_GLSX_ACCON)
        assertEquals("com.fyt.boot.ACCON", ru.shlyahten.cvt.BootReceiver.ACTION_FYT_ACCON)
    }

    @Test
    fun testTeyesAccOffActions() {
        assertEquals("com.glsx.boot.ACCOFF", ru.shlyahten.cvt.BootReceiver.ACTION_GLSX_ACCOFF)
        assertEquals("com.fyt.boot.ACCOFF", ru.shlyahten.cvt.BootReceiver.ACTION_FYT_ACCOFF)
    }
}
