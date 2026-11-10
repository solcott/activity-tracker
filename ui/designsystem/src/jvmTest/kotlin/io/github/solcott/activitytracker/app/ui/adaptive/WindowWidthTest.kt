package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowWidthTest {
    @Test
    fun thresholds() {
        assertEquals(WindowWidth.Compact, WindowWidth.of(599.dp))
        assertEquals(WindowWidth.Medium, WindowWidth.of(600.dp))
        assertEquals(WindowWidth.Medium, WindowWidth.of(839.dp))
        assertEquals(WindowWidth.Expanded, WindowWidth.of(840.dp))
    }

    // ui/window-layout "A short window is never laid out as a wide one".
    @Test
    fun aShortWindowIsNeverExpanded() {
        assertEquals(WindowWidth.Medium, WindowWidth.of(840.dp, 499.dp))
        assertEquals(WindowWidth.Expanded, WindowWidth.of(840.dp, 500.dp))
        assertEquals(WindowWidth.Medium, WindowWidth.of(874.dp, 402.dp), "a phone on its side")
        assertEquals(WindowWidth.Expanded, WindowWidth.of(1_180.dp, 820.dp), "a tablet on its side")
        assertEquals(WindowWidth.Medium, WindowWidth.of(1_000.dp, 450.dp), "a short desktop window")
    }

    @Test
    fun heightChangesNothingBelowExpanded() {
        assertEquals(WindowWidth.Compact, WindowWidth.of(412.dp, 300.dp))
        assertEquals(WindowWidth.Medium, WindowWidth.of(700.dp, 300.dp))
        assertEquals(WindowWidth.Medium, WindowWidth.of(800.dp, 1_200.dp), "a tablet upright")
    }

    @Test
    fun anUnboundedHeightIsTall() {
        assertEquals(WindowWidth.Expanded, WindowWidth.of(1_000.dp, androidx.compose.ui.unit.Dp.Infinity))
        assertEquals(WindowWidth.Expanded, WindowWidth.of(1_000.dp))
    }
}
