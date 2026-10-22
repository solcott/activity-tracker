package io.github.solcott.activitytracker.app

import com.slack.circuit.runtime.CircuitUiState
import io.github.solcott.activitytracker.app.record.ActivityTypeChoice
import io.github.solcott.activitytracker.app.record.ActivityTypeEvent
import io.github.solcott.activitytracker.app.record.ActivityTypeField
import io.github.solcott.activitytracker.data.sport.SportCatalog
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest

class ActivityTypeChoiceTest {
    private val fixture = SessionTestFixture()

    private data class Shown(val field: ActivityTypeField, val choice: ActivityTypeChoice) : CircuitUiState

    @Test
    fun startsAtTheInitialTypeAndFollowsPicksAndTheForm() = runTest {
        val choices = fixture.repos().typeChoices
        val gravel = ActivityType(Sport.RIDE, SubSport.GRAVEL)
        presenterTestOf({ choices.produce(ActivityType(Sport.OTHER)).let { Shown(it.field, it) } }) {
            val initial = awaitUntil { it.field.quickTypes.isNotEmpty() }
            assertEquals(ActivityType(Sport.OTHER), initial.field.selected)
            assertEquals(SportCatalog.common, initial.field.quickTypes, "nothing tracked yet")

            initial.field.eventSink(ActivityTypeEvent.Select(gravel))
            val picked = awaitUntil { it.field.selected == gravel }
            assertEquals(gravel, picked.choice.selected)

            // As import does from a file.
            picked.choice.select(ActivityType(Sport.SWIM))
            assertEquals(ActivityType(Sport.SWIM), awaitUntil { it.field.selected.sport == Sport.SWIM }.choice.selected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun outdoorOnlyLeavesIndoorTypesOutOfTheQuickRow() = runTest {
        val choices = fixture.repos().typeChoices
        presenterTestOf({ choices.produce(ActivityType(Sport.RUN), outdoorOnly = true).let { Shown(it.field, it) } }) {
            val quick = awaitUntil { it.field.quickTypes.isNotEmpty() }.field.quickTypes
            assertEquals(SportCatalog.common.filterNot(SportCatalog::isIndoor), quick)
            assertFalse(quick.any(SportCatalog::isIndoor))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
