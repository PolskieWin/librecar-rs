package com.librecar.rust

import com.librecar.rust.data.AssistantVoicePreset
import com.librecar.rust.data.BUILT_IN_RADIO_STATIONS
import com.librecar.rust.data.BuiltInCarAi
import com.librecar.rust.data.CarCache
import com.librecar.rust.data.CarModelPreset
import com.librecar.rust.data.LayoutMode
import com.librecar.rust.data.LoopMode
import com.librecar.rust.data.MapVisualMode
import com.librecar.rust.data.TaskbarPosition
import com.librecar.rust.nativeengine.TelemetrySnapshot
import com.librecar.rust.ui.theme.AccentPreset
import com.librecar.rust.ui.theme.AppLang
import com.librecar.rust.ui.theme.stringsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarDataUnitTest {
    @Test
    fun layoutAndEnumRoundTripsWork() {
        LayoutMode.entries.forEach { assertEquals(it, LayoutMode.fromId(it.id)) }
        TaskbarPosition.entries.forEach { assertEquals(it, TaskbarPosition.fromId(it.id)) }
        MapVisualMode.entries.forEach { assertEquals(it, MapVisualMode.fromId(it.id)) }
        CarModelPreset.entries.forEach { assertEquals(it, CarModelPreset.fromId(it.id)) }
        AccentPreset.entries.forEach { assertEquals(it, AccentPreset.fromId(it.id)) }
        AssistantVoicePreset.entries.forEach { assertEquals(it, AssistantVoicePreset.fromId(it.id)) }
        assertEquals(MapVisualMode.SATELLITE_3D_HYBRID, MapVisualMode.fromId("amoled_night"))
        assertTrue(LayoutMode.fromId("ultra_compact") == LayoutMode.ULTRA_COMPACT)
        assertEquals(3, LoopMode.entries.size)
        assertTrue(BUILT_IN_RADIO_STATIONS.all { it.streamUrl.startsWith("https://") })
    }

    @Test
    fun polishAndEnglishStringsLoadedWithFuelLabels() {
        val pl = stringsFor(AppLang.PL)
        val en = stringsFor(AppLang.EN)
        assertTrue(pl.isPolish)
        assertTrue(!en.isPolish)
        assertTrue(pl.lowFuelBanner.contains("PALIWA"))
        assertTrue(en.lowFuelBanner.contains("LOW FUEL"))
        assertTrue(pl.mapHudScaleLabel.contains("GUI"))
        assertTrue(en.mapHudScaleLabel.contains("Map GUI"))
    }

    @Test
    fun memoryCacheHonorsTtlAndStaleFallback() {
        CarCache.put("unit_test_key", "{\"ok\":true}")
        assertEquals("{\"ok\":true}", CarCache.get("unit_test_key", maxAgeMs = 60_000L))
        assertNull(CarCache.get("unit_test_key", maxAgeMs = -1L, allowStaleFallback = false))
        assertEquals("{\"ok\":true}", CarCache.get("unit_test_key", maxAgeMs = -1L, allowStaleFallback = true))
    }

    @Test
    fun builtInCarAiAnswersOfflineFuelMathAndPolish() {
        val snap = TelemetrySnapshot(
            speedKmh = 68.0,
            tripAKm = 14.2,
            odometerKm = 142.0,
            ecoDriveScore = 93,
            fuelLiters = 38.0,
            tankCapacityLiters = 50.0,
            avgLPer100Km = 6.2,
            estimatedRangeKm = 612.0,
        )
        val enFuel = BuiltInCarAi.answerOffline("How much fuel range left?", false, "VW Golf 1.4 TSI", snap, null, emptyList(), null, null)
        assertTrue(enFuel.contains("38.0") && enFuel.contains("612"))

        val plEco = BuiltInCarAi.answerOffline("Jakie mam spalanie i wynik eko?", true, "VW Golf 1.4 TSI", snap, null, emptyList(), null, null)
        assertTrue(plEco.contains("93%") && plEco.contains("6.2"))

        val math = BuiltInCarAi.answerOffline("Calculate 12 * 5", false, "VW Golf", snap, null, emptyList(), null, null)
        assertTrue(math.contains("60"))
    }
}
