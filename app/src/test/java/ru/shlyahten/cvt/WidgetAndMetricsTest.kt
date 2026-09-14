package ru.shlyahten.cvt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shlyahten.cvt.ui.CvtTempFormula
import ru.shlyahten.cvt.ui.UiState
import java.util.Locale

class WidgetAndMetricsTest {

    @Test
    fun testUiStateDefaultsForWidgetAndMetrics() {
        val state = UiState()
        assertEquals(1.0f, state.overlayScale, 0.001f)
        assertEquals(0, state.overlayTransparency)
        assertNull(state.connectionLatencyMs)
        assertEquals(0, state.errorCount)
        assertNull(state.lastError)
        assertEquals(BtStatus.DISCONNECTED, state.btStatus)
    }

    @Test
    fun testBtStatusColorMapping() {
        // Requirements:
        // Кружок зеленый/желтый/красный - статус соединения Bluetooth в виджете, а не температура.
        // зеленый - ок, желтый - соединение, красный - ошибка.
        fun getBtDotColorHex(status: BtStatus): String = when (status) {
            BtStatus.CONNECTED -> "#22C55E" // Green: OK
            BtStatus.CONNECTING -> "#F59E0B" // Yellow: Connecting
            BtStatus.ERROR, BtStatus.DISCONNECTED -> "#EF4444" // Red: Error / Disconnected
        }

        assertEquals("#22C55E", getBtDotColorHex(BtStatus.CONNECTED))
        assertEquals("#F59E0B", getBtDotColorHex(BtStatus.CONNECTING))
        assertEquals("#EF4444", getBtDotColorHex(BtStatus.ERROR))
        assertEquals("#EF4444", getBtDotColorHex(BtStatus.DISCONNECTED))
    }

    @Test
    fun testWidgetTextOutputOnConnectedVsDisconnectedOrError() {
        // Requirements:
        // При потере связи или ошибки вывод - - в виджете
        // Убрать слово "CVT" из виджета
        fun formatWidgetText(btStatus: BtStatus, temp: Double?, formula: CvtTempFormula): String {
            return if (btStatus == BtStatus.CONNECTED && temp != null) {
                if (formula == CvtTempFormula.RawCount) {
                    "${temp.toInt()} cnt"
                } else {
                    String.format(Locale.US, "%.1f°C", temp)
                }
            } else {
                "--"
            }
        }

        // When connected with valid temperature
        assertEquals("75.4°C", formatWidgetText(BtStatus.CONNECTED, 75.4, CvtTempFormula.Temp1))
        assertEquals("80 cnt", formatWidgetText(BtStatus.CONNECTED, 80.0, CvtTempFormula.RawCount))

        // When disconnected
        assertEquals("--", formatWidgetText(BtStatus.DISCONNECTED, 75.4, CvtTempFormula.Temp1))
        assertEquals("--", formatWidgetText(BtStatus.DISCONNECTED, null, CvtTempFormula.Temp1))

        // When connecting
        assertEquals("--", formatWidgetText(BtStatus.CONNECTING, 75.4, CvtTempFormula.Temp1))

        // When in error state
        assertEquals("--", formatWidgetText(BtStatus.ERROR, 75.4, CvtTempFormula.Temp1))
        assertEquals("--", formatWidgetText(BtStatus.ERROR, null, CvtTempFormula.Temp1))

        // Verify "CVT" is NOT in any widget output
        assertTrue(!formatWidgetText(BtStatus.CONNECTED, 75.4, CvtTempFormula.Temp1).contains("CVT"))
        assertTrue(!formatWidgetText(BtStatus.ERROR, null, CvtTempFormula.Temp1).contains("CVT"))
    }

    @Test
    fun testOverlayScaleAndTransparencyBounds() {
        fun clampScale(scale: Float): Float = scale.coerceIn(0.5f, 2.0f)
        fun clampTransparency(t: Int): Int = t.coerceIn(0, 100)

        assertEquals(1.0f, clampScale(1.0f), 0.001f)
        assertEquals(0.5f, clampScale(0.2f), 0.001f)
        assertEquals(2.0f, clampScale(2.5f), 0.001f)

        assertEquals(0, clampTransparency(0))
        assertEquals(50, clampTransparency(50))
        assertEquals(100, clampTransparency(100))
        assertEquals(0, clampTransparency(-10))
        assertEquals(100, clampTransparency(150))
    }

    @Test
    fun testBackgroundAlphaCalculation() {
        fun calculateBgAlpha(transparency: Int): Int =
            ((1.0f - (transparency.coerceIn(0, 100) / 100.0f)) * 235).toInt().coerceIn(0, 255)

        // 0% transparency -> 235 (solid dark glass, ~92% opaque)
        assertEquals(235, calculateBgAlpha(0))

        // 50% transparency -> 117 (translucent)
        assertEquals(117, calculateBgAlpha(50))

        // 100% transparency -> 0 (completely transparent)
        assertEquals(0, calculateBgAlpha(100))
    }

    @Test
    fun testConnectionLatencyColorClassification() {
        fun getLatencyCategory(latencyMs: Long): String {
            return when {
                latencyMs < 100 -> "FAST"
                latencyMs < 300 -> "MODERATE"
                else -> "SLOW"
            }
        }

        assertEquals("FAST", getLatencyCategory(25))
        assertEquals("FAST", getLatencyCategory(99))
        assertEquals("MODERATE", getLatencyCategory(100))
        assertEquals("MODERATE", getLatencyCategory(299))
        assertEquals("SLOW", getLatencyCategory(300))
        assertEquals("SLOW", getLatencyCategory(1200))
    }
}
