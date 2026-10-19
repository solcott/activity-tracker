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
}
