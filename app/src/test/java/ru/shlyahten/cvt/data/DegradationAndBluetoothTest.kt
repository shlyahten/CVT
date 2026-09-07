package ru.shlyahten.cvt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DegradationAndBluetoothTest {

    @Test
    fun testBluetoothDevicePrioritizer() {
        val savedAddr = "AA:BB:CC:DD:EE:01"
        
        // Priority 0: Saved device
        assertEquals(0, BluetoothDevicePrioritizer.getPriority("Some Device", savedAddr, savedAddr))
        
        // Priority 1: Exact OBDII / OBD2
        assertEquals(1, BluetoothDevicePrioritizer.getPriority("OBDII", "11:22:33:44:55:66", savedAddr))
        assertEquals(1, BluetoothDevicePrioritizer.getPriority("obdii", "11:22:33:44:55:66", savedAddr))
        assertEquals(1, BluetoothDevicePrioritizer.getPriority("OBD2", "11:22:33:44:55:66", savedAddr))
        assertEquals(1, BluetoothDevicePrioritizer.getPriority("OBD-II", "11:22:33:44:55:66", savedAddr))

        // Priority 2: Contains OBD or starts with OBD
        assertEquals(2, BluetoothDevicePrioritizer.getPriority("OBDII_BT", "11:22:33:44:55:66", savedAddr))
        assertEquals(2, BluetoothDevicePrioritizer.getPriority("Viecar OBD", "11:22:33:44:55:66", savedAddr))

        // Priority 3: Other bonded devices
        assertEquals(3, BluetoothDevicePrioritizer.getPriority("Galaxy S24", "11:22:33:44:55:66", savedAddr))
        assertEquals(3, BluetoothDevicePrioritizer.getPriority("Sony WH-1000XM4", "11:22:33:44:55:66", savedAddr))

        // Priority 4: Blank/null
        assertEquals(4, BluetoothDevicePrioritizer.getPriority(null, "11:22:33:44:55:66", savedAddr))
        assertEquals(4, BluetoothDevicePrioritizer.getPriority("   ", "11:22:33:44:55:66", savedAddr))
    }

    @Test
    fun testHistorySerializationAndParsing() {
        val samples = listOf(
            DegradationSample(1000000L, 500L),
            DegradationSample(2000000L, 510L)
        )
        val serialized = DegradationCalculator.serializeHistory(samples)
        assertEquals("1000000:500;2000000:510", serialized)

        val parsed = DegradationCalculator.parseHistory(serialized)
        assertEquals(2, parsed.size)
        assertEquals(1000000L, parsed[0].timestamp)
        assertEquals(500L, parsed[0].value)
        assertEquals(2000000L, parsed[1].timestamp)
        assertEquals(510L, parsed[1].value)

        assertTrue(DegradationCalculator.parseHistory(null).isEmpty())
        assertTrue(DegradationCalculator.parseHistory("").isEmpty())
    }

    @Test
    fun testAddSampleDeduplicationAndPruning() {
        val now = 100_000_000L
        val oldTimestamp = now - 200L * 24 * 3600 * 1000L // 200 days ago (should be pruned)
        val recentTimestamp = now - 30 * 60 * 1000L // 30 mins ago

        val initial = listOf(
            DegradationSample(oldTimestamp, 100L),
            DegradationSample(recentTimestamp, 200L)
        )

        // Adding same value 200 within 1 hour should update timestamp of last entry and prune old
        val updated = DegradationCalculator.addSample(initial, 200L, now)
        assertEquals(1, updated.size)
        assertEquals(now, updated[0].timestamp)
        assertEquals(200L, updated[0].value)

        // Adding new value should append
        val updated2 = DegradationCalculator.addSample(updated, 205L, now + 1000)
        assertEquals(2, updated2.size)
        assertEquals(205L, updated2[1].value)
    }

    @Test
    fun testCalculateWeeklyIncrease() {
        val now = 1_000_000_000_000L
        val oneDayMs = 24L * 3600 * 1000L
        val oneWeekMs = 7L * oneDayMs

        // Empty history
        assertEquals(0L, DegradationCalculator.calculateWeeklyIncrease(emptyList(), 1500L, now))

        // History with sample exactly 7 days ago
        val history = listOf(
            DegradationSample(now - 14 * oneDayMs, 1400L),
            DegradationSample(now - 7 * oneDayMs, 1485L),
            DegradationSample(now - 1 * oneDayMs, 1495L)
        )
        // Current value is 1500; closest to 7 days ago is 1485 -> increase = 15
        val increase = DegradationCalculator.calculateWeeklyIncrease(history, 1500L, now)
        assertEquals(15L, increase)

        // Reset or negative diff is coerced to 0
        val resetIncrease = DegradationCalculator.calculateWeeklyIncrease(history, 1000L, now)
        assertEquals(0L, resetIncrease)
    }
}
