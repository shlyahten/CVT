package ru.shlyahten.cvt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shlyahten.cvt.obd.CvtTempParser
import ru.shlyahten.cvt.ui.CvtTempFormula
import kotlin.math.abs

class DemoModeTest {

    enum class TempZone { COLD, NORMAL, WARM, HOT }

    private fun getZoneForTemp(temp: Double): TempZone = when {
        temp < 50.0 -> TempZone.COLD
        temp in 50.0..89.9 -> TempZone.NORMAL
        temp in 90.0..99.9 -> TempZone.WARM
        else -> TempZone.HOT
    }

    @Test
    fun testDemoPresetTemperaturesCoverAllZones() {
        val coldPreset = 40.0
        val normalPreset = 75.0
        val warmPreset = 94.0
        val hotPreset = 106.0

        assertEquals(TempZone.COLD, getZoneForTemp(coldPreset))
        assertEquals(TempZone.NORMAL, getZoneForTemp(normalPreset))
        assertEquals(TempZone.WARM, getZoneForTemp(warmPreset))
        assertEquals(TempZone.HOT, getZoneForTemp(hotPreset))
    }

    @Test
    fun testDemoCycleCoversAllFourZones() {
        val observedZones = mutableSetOf<TempZone>()
        var count = 80
        val step = 6

        while (count <= 200) {
            val temp1 = CvtTempParser.convertCountToTemp1(count)
            val zone = getZoneForTemp(temp1)
            observedZones.add(zone)
            count += step
        }

        assertTrue("Demo cycle must include COLD zone", observedZones.contains(TempZone.COLD))
        assertTrue("Demo cycle must include NORMAL zone", observedZones.contains(TempZone.NORMAL))
        assertTrue("Demo cycle must include WARM zone", observedZones.contains(TempZone.WARM))
        assertTrue("Demo cycle must include HOT zone", observedZones.contains(TempZone.HOT))
        assertEquals("All 4 zones must be covered", 4, observedZones.size)
    }

    @Test
    fun testDemoPresetBestCountMatching() {
        val targets = listOf(40.0, 75.0, 94.0, 106.0)

        for (target in targets) {
            val bestCount = (20..220).minByOrNull { n ->
                abs(CvtTempParser.convertCountToTemp1(n) - target)
            }
            assertTrue("Best count must be found", bestCount != null)
            val computedTemp = CvtTempParser.convertCountToTemp1(bestCount!!)
            val diff = abs(computedTemp - target)
            assertTrue("Matching temperature $computedTemp should be within 3°C of target $target", diff < 3.0)
            assertEquals("Computed temp should match expected zone for $target", getZoneForTemp(target), getZoneForTemp(computedTemp))
        }
    }

    @Test
    fun testFormulasInDemoMode() {
        val count = 150
        val temp1 = CvtTempParser.convertCountToTemp1(count)
        val temp2 = CvtTempParser.convertCountToTemp2(count)

        val result1 = when (CvtTempFormula.Temp1) {
            CvtTempFormula.Temp1 -> temp1
            CvtTempFormula.Temp2 -> temp2
            CvtTempFormula.RawCount -> count.toDouble()
        }
        assertEquals(temp1, result1, 0.001)

        val resultRaw = when (CvtTempFormula.RawCount) {
            CvtTempFormula.Temp1 -> temp1
            CvtTempFormula.Temp2 -> temp2
            CvtTempFormula.RawCount -> count.toDouble()
        }
        assertEquals(150.0, resultRaw, 0.001)
    }
}
