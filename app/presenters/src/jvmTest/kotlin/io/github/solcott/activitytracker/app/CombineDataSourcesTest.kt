package io.github.solcott.activitytracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.detail.ActivityDetailState
import io.github.solcott.activitytracker.app.detail.DetailSection
import io.github.solcott.activitytracker.app.detail.StreamKind
import io.github.solcott.activitytracker.data.activity.DataKind
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.uistate.isLoading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

/** The owner's choice of where a combined activity's data comes from (`feed/combined-sources`), on its page. */
class CombineDataSourcesTest : CombineTestBase() {
    @Test
    fun theOwnerSeesEachKindOfDataAndWhereItComesFrom() = runTest {
        ride()
        presenterTestOf(detail(uri("3kride"), FakeNavigator(ActivityDetailScreen(uri("3kride"))))) {
            val sources = awaitSources().dataSources!!
            assertEquals(listOf(DataKind.HeartRate, DataKind.Power), sources.kinds.map { it.kind })
            val heartRate = sources.kinds.first()
            assertTrue(heartRate.included)
            assertEquals(setOf(uri("3kwatch"), uri("3kedge")), heartRate.offered.map { it.uri.value }.toSet())
            val power = sources.kinds.last()
            assertTrue(power.included, "power, recorded by one member, is included by default")
            assertEquals(uri("3kedge"), power.chosen?.uri?.value)
            assertTrue(power.offered.isEmpty(), "nothing to choose between")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sourcesThatFailToLoadSayWhyAndRetry() = runTest {
        ride()
        failReads = true
        presenterTestOf(detail(uri("3kride"), FakeNavigator(ActivityDetailScreen(uri("3kride"))))) {
            val failed = awaitUntil { it.page()?.dataSources?.error != null }.page()!!
            assertEquals(UiError.Kind.LoadDataSources, failed.dataSources?.error?.kind)
            assertFalse(failed.dataSources!!.isLoading)
            failReads = false
            failed.eventSink(ActivityDetailEvent.RetrySources)
            val sources = awaitSources().dataSources!!
            assertNull(sources.error)
            assertEquals(listOf(DataKind.HeartRate, DataKind.Power), sources.kinds.map { it.kind })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anotherAthleteSeesNoDataSources() = runTest {
        ride()
        bobs += "3kride"
        presenterTestOf(detail(uri("3kride"), FakeNavigator(ActivityDetailScreen(uri("3kride"))))) {
            val loaded = awaitLoaded()
            assertEquals(null, loaded.dataSources)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun excludingThenIncludingUpdatesTheSourcesAfterEachRebuild() = runTest {
        ride()
        presenterTestOf(detail(uri("3kride"), FakeNavigator(ActivityDetailScreen(uri("3kride"))))) {
            awaitSources().eventSink(ActivityDetailEvent.SetIncluded(DataKind.Power, included = false))
            val excluded = awaitUntil { s -> s.powerRow()?.included == false && s.saving() == null }
            assertEquals(null, excluded.powerRow()?.chosen)
            assertEquals(null, streamsOf("3kride")?.power, "rebuilt without power")
            assertEquals("150|140", "${streamsOf("3kwatch")?.heartRate?.first()}|${streamsOf("3kedge")?.heartRate?.first()}")
            assertEquals(250, streamsOf("3kedge")?.power?.first(), "the member keeps its power")

            excluded.page()!!.eventSink(ActivityDetailEvent.SetIncluded(DataKind.Power, included = true))
            awaitUntil { s -> s.powerRow()?.included == true && s.saving() == null }
            assertEquals(250, streamsOf("3kride")?.power?.first(), "power is back")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theChartsFollowARebuildBeforeTheAppViewIndexesIt() = runTest {
        ride()
        servedStreams["3kride"] = streamsOf("3kedge")!!
        presenterTestOf(detail(uri("3kride"), FakeNavigator(ActivityDetailScreen(uri("3kride"))))) {
            val loaded = awaitUntil { it.hasPowerChart() && it.powerRow() != null }.page()!!
            assertTrue(DetailSection.Power in loaded.sections)

            loaded.eventSink(ActivityDetailEvent.SetIncluded(DataKind.Power, included = false))
            val rebuilt = awaitUntil { s -> s.powerRow()?.included == false && s.saving() == null && !s.hasPowerChart() }
            assertTrue(DetailSection.Power !in rebuilt.page()!!.sections, "no Power card beside figures without it")
            assertTrue(rebuilt.page()!!.charts!!.lines.any { it.kind == StreamKind.HeartRate }, "the rebuilt streams, not none")
            assertEquals(250, servedStreams.getValue("3kride").power?.first(), "the AppView still serves the old streams")

            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun ActivityDetailState.hasPowerChart() = page()?.charts?.lines?.any { it.kind == StreamKind.Power } == true

    @Test
    fun choosingASourceOnceRebuildsOnce() = runTest {
        ride()
        presenterTestOf(detail(uri("3kride"), FakeNavigator(ActivityDetailScreen(uri("3kride"))))) {
            val loaded = awaitSources()
            val before = writes.count { it == "put ${Nsid.FEED_ACTIVITY}/3kride" }
            gate = CompletableDeferred()
            loaded.eventSink(ActivityDetailEvent.ChooseSource(DataKind.HeartRate, AtUri(uri("3kwatch"))))
            awaitUntil { it.saving() == DataKind.HeartRate }
            loaded.eventSink(ActivityDetailEvent.ChooseSource(DataKind.HeartRate, AtUri(uri("3kwatch"))))
            gate.complete(Unit)
            val chosen = awaitUntil { s -> s.saving() == null && s.heartRateRow()?.chosen?.uri?.value == uri("3kwatch") }
            assertEquals(before + 1, writes.count { it == "put ${Nsid.FEED_ACTIVITY}/3kride" }, "a second tap does nothing")
            assertTrue(streamsOf("3kride")!!.heartRate!!.all { it == 150 })
            assertEquals(150, AtJson.decodeFromJsonElement(ActivityRecord.serializer(), records.getValue("3kride")).avgHeartRate)
            assertTrue(chosen.page()!!.dataSources?.error == null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedRebuildShowsAnErrorAndRetries() = runTest {
        ride()
        presenterTestOf(detail(uri("3kride"), FakeNavigator(ActivityDetailScreen(uri("3kride"))))) {
            val loaded = awaitSources()
            failReads = true
            loaded.eventSink(ActivityDetailEvent.SetIncluded(DataKind.Power, included = false))
            val failedState = awaitUntil { it.page()?.dataSources?.error != null }
            val failed = failedState.page()!!
            assertEquals(UiError.Kind.SaveDataSource, failed.dataSources?.error?.kind)
            assertEquals(true, failedState.powerRow()?.included, "nothing changed")

            failReads = false
            failed.eventSink(ActivityDetailEvent.RetrySources)
            awaitUntil { s -> s.powerRow()?.included == false && s.page()!!.dataSources?.error == null }
            assertEquals(null, streamsOf("3kride")?.power)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
