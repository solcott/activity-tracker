package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.gear.IgnoredSensorsEvent
import io.github.solcott.activitytracker.app.gear.IgnoredSensorsPresenter
import io.github.solcott.activitytracker.app.gear.IgnoredSensorsScreen
import io.github.solcott.activitytracker.app.gear.LinkedSensor
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.app.GearAutoAssignRecord
import io.github.solcott.activitytracker.lexicon.app.SensorKey
import io.github.solcott.uistate.hasLoaded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** The devices the athlete marked as not gear, and un-ignoring them (gear/gear-sensors). */
class IgnoredSensorsPresenterTest {
    private val fixture = SessionTestFixture()
    private val created = Instant.parse("2026-10-20T12:00:00Z")
    private val edge = SensorKey(manufacturer = "Garmin", serialNumber = "3510682071", model = "Edge 840")
    private val strap = SensorKey(manufacturer = "Garmin", model = "HRM 200")

    @Test
    fun unignoringTheEdge840LeavesTheStrap() = runTest {
        fixture.gearAutoAssign =
            AtJson.encodeToString(GearAutoAssignRecord.serializer(), GearAutoAssignRecord(created, ignored = listOf(edge, strap)))
        val repos = fixture.repos()
        presenterTestOf({ IgnoredSensorsPresenter(FakeNavigator(IgnoredSensorsScreen), repos.gearDefaults) }) {
            val loaded = awaitUntil { it.content.hasLoaded && it.content.data.isNotEmpty() }
            assertEquals(
                listOf(LinkedSensor(edge.id, "Garmin", "Edge 840", "2071"), LinkedSensor(strap.id, "Garmin", "HRM 200", weak = true)),
                loaded.content.data.toList(),
            )
            loaded.eventSink(IgnoredSensorsEvent.Unignore(edge.id))
            assertEquals(listOf(strap.id), awaitUntil { it.content.data.size == 1 }.content.data.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
        val saved = AtJson.decodeFromString(GearAutoAssignRecord.serializer(), requireNotNull(fixture.gearAutoAssign))
        assertEquals(listOf(strap), saved.ignored)
    }

    @Test
    fun noneIgnoredIsAnEmptyList() = runTest {
        val repos = fixture.repos()
        presenterTestOf({ IgnoredSensorsPresenter(FakeNavigator(IgnoredSensorsScreen), repos.gearDefaults) }) {
            val loaded = awaitUntil { it.content.hasLoaded }
            assertEquals(emptyList(), loaded.content.data.toList())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
