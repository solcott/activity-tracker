package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/** A ski day's laps table (segments/segment-lifts): lift laps named after their lifts, on one line in a wide window. */
@OptIn(ExperimentalTestApi::class)
class LapTableUiTest {
    private fun lap(number: Int, elapsed: Int, rest: Boolean, lift: String? = null) =
        LapRow(
            number,
            elapsed,
            null,
            distance = 0.9,
            speed = 9.3,
            avgHeartRate = 80,
            avgPower = null,
            avgCadence = null,
            ascent = null,
            intensity = if (rest) "rest" else "active",
            descent = if (rest) null else 400.0,
            liftName = lift,
        )

    @Test
    fun aLiftLapsNameFitsOnOneLineInAWideWindow() = runComposeUiTest {
        val laps =
            persistentListOf(lap(1, 363, rest = true, lift = "Crescent Hi-Speed Quad"), lap(2, 334, rest = false), lap(3, 296, rest = true))
        setContent { AppPreview { Box(Modifier.width(720.dp)) { LapTable(laps, ski = true) } } }
        val lift = onNodeWithContentDescription("Crescent Hi-Speed Quad", substring = true).getBoundsInRoot()
        val run = onNodeWithContentDescription("Run 1", substring = true).getBoundsInRoot()
        assertEquals(run.bottom - run.top, lift.bottom - lift.top, "a named lift lap is one line, like a run")
        onNodeWithContentDescription("Lift", substring = true).assertExists()
    }
}
