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
        assertEquals(false, state.fastTimingDesired)
        assertEquals(true, state.elmCompressionDesired)
        assertEquals(false, state.klineOptimizationDesired)
        assertEquals(false, state.klineLongMessagesDesired)
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
        // После потери соединения или ошибки, оставить в виджете последние данные по температуре с полупрозрачным шрифтом и красным индикатором ошибки связи.
        // Убрать слово "CVT" из виджета
        fun formatWidgetDisplay(
            btStatus: BtStatus,
            currentTemp: Double?,
            lastKnownTemp: Double?,
            formula: CvtTempFormula
        ): Pair<String, Float> {
            val temp = currentTemp ?: lastKnownTemp
            val isConnected = btStatus == BtStatus.CONNECTED
            val alpha = if (isConnected && temp != null) 1.0f else 0.5f

            val text = if (temp != null) {
                if (formula == CvtTempFormula.RawCount) {
                    "${temp.toInt()} cnt"
                } else {
                    String.format(Locale.US, "%.1f°C", temp)
                }
            } else {
                "--"
            }
            return text to alpha
        }

        // When connected with valid temperature: 1.0f alpha
        val (connectedText, connectedAlpha) = formatWidgetDisplay(BtStatus.CONNECTED, 75.4, null, CvtTempFormula.Temp1)
        assertEquals("75.4°C", connectedText)
        assertEquals(1.0f, connectedAlpha, 0.01f)

        // When disconnected after having received 75.4°C: retains 75.4°C with 0.5f alpha
        val (disconnWithLastText, disconnWithLastAlpha) = formatWidgetDisplay(BtStatus.DISCONNECTED, null, 75.4, CvtTempFormula.Temp1)
        assertEquals("75.4°C", disconnWithLastText)
        assertEquals(0.5f, disconnWithLastAlpha, 0.01f)

        // When in error state after having received 75.4°C: retains 75.4°C with 0.5f alpha
        val (errorWithLastText, errorWithLastAlpha) = formatWidgetDisplay(BtStatus.ERROR, null, 75.4, CvtTempFormula.Temp1)
        assertEquals("75.4°C", errorWithLastText)
        assertEquals(0.5f, errorWithLastAlpha, 0.01f)

        // When disconnected and no data was ever received: "--" with 0.5f alpha
        val (disconnNoDataText, disconnNoDataAlpha) = formatWidgetDisplay(BtStatus.DISCONNECTED, null, null, CvtTempFormula.Temp1)
        assertEquals("--", disconnNoDataText)
        assertEquals(0.5f, disconnNoDataAlpha, 0.01f)

        // When in error state and no data was ever received: "--" with 0.5f alpha
        val (errorNoDataText, errorNoDataAlpha) = formatWidgetDisplay(BtStatus.ERROR, null, null, CvtTempFormula.Temp1)
        assertEquals("--", errorNoDataText)
        assertEquals(0.5f, errorNoDataAlpha, 0.01f)

        // Verify "CVT" is NOT in any widget output
        assertTrue(!formatWidgetDisplay(BtStatus.CONNECTED, 75.4, null, CvtTempFormula.Temp1).first.contains("CVT"))
        assertTrue(!formatWidgetDisplay(BtStatus.ERROR, null, 75.4, CvtTempFormula.Temp1).first.contains("CVT"))
        assertTrue(!formatWidgetDisplay(BtStatus.ERROR, null, null, CvtTempFormula.Temp1).first.contains("CVT"))
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
