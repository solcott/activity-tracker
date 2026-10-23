package io.github.solcott.activitytracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.runtime.CircuitUiState
import io.github.solcott.activitytracker.app.record.GearChoice
import io.github.solcott.activitytracker.app.record.GearEvent
import io.github.solcott.activitytracker.app.record.GearField
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.GearAutoAssignRecord
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.solcott.activitytracker.lexicon.app.GearSportDefault
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject

/** The gear picker every form shares (`gear/activity-gear`: "Gear is picked when an activity is saved", "Defaults per activity type"). */
class GearChoiceTest {
    private val fixture = SessionTestFixture()
    private val created = Instant.parse("2026-10-20T12:00:00Z")

    private fun uri(rkey: String) = AtUri("at://${fixture.did.value}/${Nsid.GEAR_ITEM}/$rkey")

    private fun store(place: String, rkey: String, record: GearItemRecord) {
        fixture.gear.getOrPut(place) { mutableMapOf() }[rkey] =
            AtJson.encodeToJsonElement(GearItemRecord.serializer(), record) as JsonObject
    }

    private fun defaults(vararg entries: GearSportDefault) {
        fixture.gearAutoAssign = AtJson.encodeToString(GearAutoAssignRecord.serializer(), GearAutoAssignRecord(created, entries.toList()))
    }

    private data class Shown(val field: GearField, val choice: GearChoice, val type: ActivityType, val setType: (ActivityType) -> Unit) :
        CircuitUiState

    private suspend fun test(initial: ActivityType, block: suspend app.cash.turbine.ReceiveTurbine<Shown>.() -> Unit) {
        val choices = fixture.repos().gearChoices
        presenterTestOf(
            {
                var type by remember { mutableStateOf(initial) }
                choices.produce(type).let { Shown(it.field, it, type) { t -> type = t } }
            },
            block = block,
        )
    }

    @Test
    fun itemsForTheSportComeFirstThenByName() = runTest {
        store("repo", "3bike", GearItemRecord("Allez", GearType.BIKE, created, sports = listOf(Sport.RIDE)))
        store(Visibility.SELF, "3trail", GearItemRecord("Speedgoat", GearType.SHOES, created, sports = listOf(Sport.RUN)))
        store("repo", "3road", GearItemRecord("Clifton", GearType.SHOES, created, sports = listOf(Sport.RUN)))
        test(ActivityType(Sport.RUN)) {
            val field = awaitUntil { it.field.options.size == 3 }.field
            assertEquals(listOf("Clifton", "Speedgoat", "Allez"), field.options.map { it.name })
            assertEquals(Visibility.SELF, field.options[1].visibility, "labelled with who sees the item")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRetiredItemShowsOnlyWhenSelected() = runTest {
        store("repo", "3old", GearItemRecord("Old shoes", GearType.SHOES, created, retiredAt = created))
        store("repo", "3new", GearItemRecord("New shoes", GearType.SHOES, created))
        test(ActivityType(Sport.RUN)) {
            val loaded = awaitUntil { it.field.options.isNotEmpty() }
            assertEquals(listOf("New shoes"), loaded.field.options.map { it.name })
            // As edit does for a run that lists them.
            loaded.choice.seed(listOf(uri("3old")))
            val seeded = awaitUntil { it.field.selected.isNotEmpty() }.field
            assertEquals(listOf(uri("3old")), seeded.selected)
            assertTrue(seeded.options.single { it.uri == uri("3old") }.retired)
            assertEquals(2, seeded.options.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aTypeChangeFollowsTheDefaultsUntilTheAthletePicks() = runTest {
        store("repo", "3road", GearItemRecord("Clifton", GearType.SHOES, created))
        store("repo", "3trail", GearItemRecord("Speedgoat", GearType.SHOES, created))
        defaults(
            GearSportDefault(Sport.RUN, listOf(uri("3road"))),
            GearSportDefault(Sport.RUN, listOf(uri("3trail")), subSport = SubSport.TRAIL),
        )
        test(ActivityType(Sport.RUN)) {
            val run = awaitUntil { it.field.selected.isNotEmpty() }
            assertEquals(listOf(uri("3road")), run.field.selected)
            run.setType(ActivityType(Sport.RUN, SubSport.TRAIL))
            val trail = awaitUntil { it.field.selected == listOf(uri("3trail")) }

            // A pick is kept whatever the type.
            trail.field.eventSink(GearEvent.Toggle(uri("3road")))
            val picked = awaitUntil { it.field.selected.size == 2 }
            assertTrue(picked.choice.touched)
            picked.setType(ActivityType(Sport.RUN))
            assertEquals(listOf(uri("3trail"), uri("3road")), awaitUntil { it.type == ActivityType(Sport.RUN) }.field.selected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun removingAllGearLeavesNone() = runTest {
        store("repo", "3bike", GearItemRecord("Tarmac", GearType.BIKE, created))
        defaults(GearSportDefault(Sport.RIDE, listOf(uri("3bike"))))
        test(ActivityType(Sport.RIDE)) {
            val ride = awaitUntil { it.field.selected.isNotEmpty() }
            ride.field.eventSink(GearEvent.Clear)
            assertEquals(emptyList(), awaitUntil { it.field.selected.isEmpty() }.choice.selected)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
