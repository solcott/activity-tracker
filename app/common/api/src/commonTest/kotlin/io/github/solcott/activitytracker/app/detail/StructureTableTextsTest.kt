package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.AppText.Companion.named
import io.github.solcott.activitytracker.app.text.AppText.Companion.written
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem
import kotlin.test.Test
import kotlin.test.assertEquals

/** The legs, lengths and sets tables as both UIs read them (dev/shared-display-text). */
class StructureTableTextsTest {
    @Test
    fun aLengthHasItsStrokeAndSwolfAndARestOnlyItsTime() {
        val table =
            StructureTableTexts.lengths(
                listOf(LengthRow(1, "freestyle", 30, 15, rest = false), LengthRow(null, null, 20, null, rest = true)),
            )
        assertEquals(5, table.headers.size)
        assertEquals(1, table.wideColumn)
        assertEquals(
            listOf(
                written("1"),
                AppText.Named("stroke_freestyle", fallback = written("Freestyle")),
                written("0:30"),
                written("15"),
                written("45"),
            ),
            table.rows[0].cells,
        )
        assertEquals(
            listOf(named("table_rest"), StatText.missing, written("0:20"), StatText.missing, StatText.missing),
            table.rows[1].cells,
        )
    }

    @Test
    fun anUnknownExerciseReadsAsWords() {
        val set =
            SetRow(1, rest = false, category = "row", exercise = "single_arm_cable_row", reps = 10, weightGrams = 20_000, seconds = 45)
        val table = StructureTableTexts.sets(listOf(set), UnitSystem.Metric)
        assertEquals(AppText.Named("exercise_single_arm_cable_row", fallback = written("Single arm cable row")), table.rows[0].cells[1])
        assertEquals(StatText.weight(20_000, UnitSystem.Metric), table.rows[0].cells[3])
        assertEquals(named("set_unknown_exercise"), StructureTableTexts.exercise(set.copy(exercise = null, category = null)))
    }

    @Test
    fun aLegHasItsSportAndRateAndATransitionOnlyItsTime() {
        val table =
            StructureTableTexts.legs(
                listOf(
                    LegRow("running", null, transition = false, seconds = 1_500, distanceMeters = 5_000),
                    LegRow("transition", null, true, 60, null),
                ),
                UnitSystem.Metric,
            )
        assertEquals(named("sport_running").let { (it as AppText.Named).name }, (table.rows[0].cells[0] as AppText.Named).name)
        assertEquals(StatText.distance(5_000.0, UnitSystem.Metric), table.rows[0].cells[2])
        assertEquals(named("leg_transition"), table.rows[1].cells[0])
        assertEquals(-1, table.wideColumn)
    }
}
