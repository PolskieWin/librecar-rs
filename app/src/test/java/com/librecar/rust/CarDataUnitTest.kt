package com.librecar.rust

import com.librecar.rust.data.LayoutMode
import com.librecar.rust.data.MapVisualMode
import com.librecar.rust.data.TaskbarPosition
import com.librecar.rust.ui.theme.AccentPreset
import com.librecar.rust.ui.theme.AppLang
import com.librecar.rust.ui.theme.stringsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarDataUnitTest {
    @Test
    fun layoutAndEnumRoundTripsWork() {
        LayoutMode.entries.forEach { assertEquals(it, LayoutMode.fromId(it.id)) }
        TaskbarPosition.entries.forEach { assertEquals(it, TaskbarPosition.fromId(it.id)) }
        MapVisualMode.entries.forEach { assertEquals(it, MapVisualMode.fromId(it.id)) }
        AccentPreset.entries.forEach { assertEquals(it, AccentPreset.fromId(it.id)) }
    }

    @Test
    fun polishAndEnglishStringsLoaded() {
        val pl = stringsFor(AppLang.PL)
        val en = stringsFor(AppLang.EN)
        assertTrue(pl.isPolish)
        assertTrue(!en.isPolish)
        assertTrue(pl.bestFuelTrack.contains("paliw"))
        assertTrue(en.bestFuelTrack.contains("Fuel"))
    }
}
