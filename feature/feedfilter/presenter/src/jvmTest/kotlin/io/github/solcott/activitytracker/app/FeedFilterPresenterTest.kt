package io.github.solcott.activitytracker.app

import co.touchlab.kermit.Logger
import com.slack.circuit.subcircuit.test.SubCircuitReceiveTurbine
import com.slack.circuit.subcircuit.test.test
import io.github.solcott.activitytracker.app.feedfilter.DateRange
import io.github.solcott.activitytracker.app.feedfilter.FeedFilter
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterEvent
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterOuterEvent
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterPresenter
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterScreen
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterState
import io.github.solcott.activitytracker.app.feedfilter.Selection
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.data.sport.SportCatalog
import io.github.solcott.activitytracker.data.sport.SportCategory
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class FeedFilterPresenterTest {
    private val http = HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) }) { expectSuccess = false }
    private val ride = ActivityType(Sport.RIDE)
    private val run = ActivityType(Sport.RUN)

    /** Sends [event] from [state] and returns the next state. */
    private suspend fun SubCircuitReceiveTurbine<FeedFilterOuterEvent, FeedFilterState>.send(
        state: FeedFilterState,
        event: FeedFilterEvent,
    ): FeedFilterState {
        state.eventSink(event)
        return awaitItem()
    }

    /** The first state, after the filter it reports when first shown. */
    private suspend fun SubCircuitReceiveTurbine<FeedFilterOuterEvent, FeedFilterState>.awaitShown(): FeedFilterState {
        val state = awaitItem()
        assertEquals(FeedFilterOuterEvent.Changed(state.filter), outerEvents.awaitEvent())
        return state
    }

    private suspend fun presenter(initial: FeedFilter = FeedFilter.Default): FeedFilterPresenter {
        val (db, _, clients) = signedInTestSession(http)
        val recent = RecentActivityTypes(db, clients, FeedRepository(db, clients, LocalLikes(), Logger))
        return FeedFilterPresenter(FeedFilterScreen(initial), recent)
    }

    @Test
    fun startsFromAndReportsTheScreensInitialFilter() = runTest {
        val initial = FeedFilter(persistentSetOf(ride))
        presenter(initial).test {
            val state = awaitShown()
            assertEquals(initial, state.filter)
            assertNull(state.typePicker)
            outerEvents.assertNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun typeDraftAppliesOnlyOnApply() = runTest {
        presenter().test {
            awaitShown().eventSink(FeedFilterEvent.OpenTypes)
            awaitItem().eventSink(FeedFilterEvent.ToggleType(ride))
            var state = awaitItem()
            assertEquals<Set<ActivityType>?>(setOf(ride), state.typePicker?.draft)
            assertEquals(persistentSetOf(), state.filter.types, "a draft doesn't change the filter")

            // Dismissing throws the draft away.
            state.eventSink(FeedFilterEvent.DismissTypes)
            state = awaitItem()
            assertNull(state.typePicker)
            outerEvents.assertNoEvents()

            state = send(state, FeedFilterEvent.OpenTypes)
            assertEquals<Set<ActivityType>?>(emptySet(), state.typePicker?.draft, "a reopened picker starts from the filter")
            state = send(state, FeedFilterEvent.ToggleType(run))
            send(state, FeedFilterEvent.ApplyTypes).let {
                assertEquals(setOf(run), it.filter.types)
                assertNull(it.typePicker)
            }
            assertEquals(FeedFilterOuterEvent.Changed(FeedFilter(types = persistentSetOf(run))), outerEvents.awaitEvent())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun categoriesToggleTriState() = runTest {
        presenter().test {
            awaitShown().eventSink(FeedFilterEvent.OpenTypes)
            fun gym(state: FeedFilterState) = assertNotNull(state.typePicker).categories.first { it.category == SportCategory.Gym }
            val opened = awaitItem()
            assertEquals(Selection.None, gym(opened).selection)

            var state = send(opened, FeedFilterEvent.ToggleType(ActivityType(Sport.ROW, SubSport.INDOOR)))
            assertEquals(Selection.Partial, gym(state).selection, "a sub-sport filed under the category counts")

            state = send(state, FeedFilterEvent.ToggleCategory(SportCategory.Gym))
            assertEquals(Selection.All, gym(state).selection)
            val draft = assertNotNull(state.typePicker).draft
            val gymTypes = SportCatalog.categories.first { it.category == SportCategory.Gym }.types
            assertEquals(true, gymTypes.all { type -> type in draft || ActivityType(type.sport) in draft })
            assertEquals(Selection.None, gym(send(state, FeedFilterEvent.ToggleCategory(SportCategory.Gym))).selection)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun categorySelectsItsSportsWhole() = runTest {
        presenter().test {
            awaitShown().eventSink(FeedFilterEvent.OpenTypes)
            fun selection(state: FeedFilterState, category: SportCategory) =
                assertNotNull(state.typePicker).categories.first { it.category == category }.selection

            val state = send(awaitItem(), FeedFilterEvent.ToggleCategory(SportCategory.WaterSports))
            val draft = assertNotNull(state.typePicker).draft
            assertEquals(true, ActivityType(Sport.ROW) in draft, "Row is selected whole")
            assertEquals(Selection.All, selection(state, SportCategory.WaterSports))
            assertEquals(Selection.Partial, selection(state, SportCategory.Gym), "Indoor Row, filed under Gym, is covered by Row")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun everySubSportAcrossCategoriesCollapsesToTheSport() = runTest {
        presenter().test {
            awaitShown().eventSink(FeedFilterEvent.OpenTypes)
            var state = awaitItem()
            SportCatalog.subSports(Sport.ROW).forEach {
                state = send(state, FeedFilterEvent.ToggleType(ActivityType(Sport.ROW, it.subSport)))
            }
            assertEquals(setOf(ActivityType(Sport.ROW)), assertNotNull(state.typePicker).draft)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun customRangeTakesTwoDatesInEitherOrder() = runTest {
        presenter().test {
            var state = send(awaitShown(), FeedFilterEvent.OpenCustomRange)
            assertEquals(null, assertNotNull(state.customRange).start)
            state = send(state, FeedFilterEvent.PickRangeDate(LocalDate(2026, 3, 31)))
            send(state, FeedFilterEvent.PickRangeDate(LocalDate(2026, 3, 1))).let {
                assertNull(it.customRange)
                assertEquals(DateRange.Custom(LocalDate(2026, 3, 1), LocalDate(2026, 3, 31)), it.filter.range)
            }
            assertEquals(
                FeedFilterOuterEvent.Changed(FeedFilter(range = DateRange.Custom(LocalDate(2026, 3, 1), LocalDate(2026, 3, 31)))),
                outerEvents.awaitEvent(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun presetsAndClearAreReported() = runTest {
        presenter(FeedFilter(persistentSetOf(ride))).test {
            val state = send(awaitShown(), FeedFilterEvent.SetRange(DateRange.LastDays(7)))
            assertEquals(DateRange.LastDays(7), state.filter.range)
            assertEquals(
                FeedFilterOuterEvent.Changed(FeedFilter(persistentSetOf(ride), DateRange.LastDays(7))),
                outerEvents.awaitEvent(),
            )

            send(state, FeedFilterEvent.Clear).let { assertEquals(FeedFilter.Default, it.filter) }
            assertEquals(FeedFilterOuterEvent.Changed(FeedFilter.Default), outerEvents.awaitEvent())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
