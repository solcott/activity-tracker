package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import app.cash.turbine.ReceiveTurbine
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.trim.TrimEvent
import io.github.solcott.activitytracker.app.trim.TrimPresenter
import io.github.solcott.activitytracker.app.trim.TrimScreen
import io.github.solcott.activitytracker.app.trim.TrimState
import io.github.solcott.activitytracker.data.activity.trim.KeptSpan
import io.github.solcott.activitytracker.data.activity.trim.TrimDrafts
import io.github.solcott.activitytracker.data.activity.trim.TrimInput
import io.github.solcott.activitytracker.data.activity.trim.TrimTotals
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.uistate.LoadStatus
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** The trim screen's presenter (feed/activity-trim) over a draft a recording offered, and a saved activity that can't be read. */
class TrimPresenterTest {
    private val fixture = SessionTestFixture()
    private val drafts = TrimDrafts()
    private val start = Instant.parse("2026-11-22T07:36:00Z")

    /** A ride sampled every 10 s: 6 min standing, an hour at 8 m/s, a 9 min stop, then 4 min driving at 13.6 m/s. */
    private val times = (0..END step 10).toList()

    private fun speedAt(t: Int) =
        when {
            t < STANDING -> 0.0
            t < RIDE_END -> 8.0
            t < DRIVE_START -> 0.0
            else -> 13.6
        }

    private val meters = run {
        var d = 0.0
        times.mapIndexed { i, t ->
            if (i > 0) d += speedAt(times[i - 1]) * (t - times[i - 1])
            d
        }
    }

    private val ride =
        TrimInput(
            start,
            END,
            ActivityStreamData(
                time = times,
                distance = meters.map { (it * 100).roundToInt() },
                speed = times.map { (speedAt(it) * 1000).roundToInt() },
            ),
            emptyList(),
        )
    private val manual = TrimInput(start, 3_600, null, emptyList())

    private suspend fun presenter(
        screen: TrimScreen,
        navigator: FakeNavigator = FakeNavigator(screen),
    ): @androidx.compose.runtime.Composable () -> TrimState {
        val repos = fixture.repos()
        return { TrimPresenter(screen, navigator, repos.feed, repos.manager, SegmentRepository(repos.clients), drafts) }
    }

    private suspend fun ReceiveTurbine<TrimState>.awaitEditor() = awaitUntil { it.editor != null }

    private fun offer(input: TrimInput = ride) =
        drafts.offer(DRAFT, input, TrimTotals(meters.last(), 3_860, input.elapsedSeconds.toLong(), null), sport = "ride")

    @Test
    fun aDraftOpensWholeWithItsSuggestion() = runTest {
        offer()
        presenterTestOf(presenter(TrimScreen(draftId = DRAFT))) {
            val opened = awaitEditor()
            val editor = assertNotNull(opened.editor)
            assertEquals(0, editor.from)
            assertEquals(END, editor.to)
            assertTrue(editor.offersDistance)
            assertFalse(editor.changed)
            assertEquals(KeptSpan(STANDING, RIDE_END), editor.suggestion?.span)
            assertTrue(opened.canSave, "a draft's Done is always there")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun handlesStayAMinuteApartAndTheSuggestionAppliesAndResets() = runTest {
        offer()
        presenterTestOf(presenter(TrimScreen(draftId = DRAFT))) {
            val loaded = awaitEditor()
            loaded.eventSink(TrimEvent.Moved(END, END))
            val crossed = awaitUntil { it.editor?.from != 0 }.editor!!
            assertEquals(END - KeptSpan.MIN_SECONDS, crossed.from, "the start stops a minute before the end")

            loaded.eventSink(TrimEvent.ApplySuggestion)
            val suggested = awaitUntil { it.editor?.suggestionApplied == true }.editor!!
            assertEquals(STANDING, suggested.from)
            assertEquals(RIDE_END, suggested.to)
            assertEquals(meters.last() - (meters.last() - suggested.toMeters!!), suggested.after.distanceMeters!!, 1.0)

            loaded.eventSink(TrimEvent.Reset)
            assertFalse(awaitUntil { it.editor?.changed == false }.editor!!.suggestionApplied)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun byDistanceAStartAtADistanceAlsoCutsTheStandingThere() = runTest {
        offer()
        presenterTestOf(presenter(TrimScreen(draftId = DRAFT))) {
            val loaded = awaitEditor()
            loaded.eventSink(TrimEvent.ModeChanged(byDistance = true))
            awaitUntil { it.editor?.byDistance == true }
            // Ten seconds in, still standing at 0 m: the kept span starts where the athlete set off.
            loaded.eventSink(TrimEvent.Moved(10, END))
            assertEquals(STANDING, awaitUntil { it.editor?.from != 0 }.editor!!.from)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun exactValuesTypeAndStepAndRefuseWhatDoesntRead() = runTest {
        offer()
        presenterTestOf(presenter(TrimScreen(draftId = DRAFT))) {
            val loaded = awaitEditor()
            loaded.eventSink(TrimEvent.EditExact(start = false, UnitSystem.Metric))
            val open = assertNotNull(awaitUntil { it.exact != null }.exact)
            assertEquals("1:19:00", open.field.text.toString())

            loaded.eventSink(TrimEvent.StepExact(later = false, UnitSystem.Metric))
            assertEquals(END - 10, awaitUntil { it.editor?.to == END - 10 }.editor!!.to)
            assertEquals("1:18:50", open.field.text.toString())

            open.field.setTextAndPlaceCursorAtEnd("soon")
            loaded.eventSink(TrimEvent.ApplyExact(UnitSystem.Metric))
            assertTrue(awaitUntil { it.exact?.invalid == true }.exact!!.invalid)

            open.field.setTextAndPlaceCursorAtEnd("1:06:00")
            loaded.eventSink(TrimEvent.ApplyExact(UnitSystem.Metric))
            val applied = awaitUntil { it.exact == null }
            assertEquals(RIDE_END, applied.editor!!.to)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aDistanceThatIsntAFiniteNumberIsRefused() = runTest {
        offer()
        presenterTestOf(presenter(TrimScreen(draftId = DRAFT))) {
            val loaded = awaitEditor()
            loaded.eventSink(TrimEvent.ModeChanged(byDistance = true))
            awaitUntil { it.editor?.byDistance == true }
            loaded.eventSink(TrimEvent.EditExact(start = false, UnitSystem.Metric))
            val open = assertNotNull(awaitUntil { it.exact != null }.exact)

            open.field.setTextAndPlaceCursorAtEnd("Infinity")
            loaded.eventSink(TrimEvent.ApplyExact(UnitSystem.Metric))
            assertTrue(awaitUntil { it.exact?.invalid == true }.exact!!.invalid)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aManualEntryMovesItsTimesOnlyAndTakesAClockTime() = runTest {
        offer(manual)
        presenterTestOf(presenter(TrimScreen(draftId = DRAFT))) {
            val loaded = awaitEditor()
            val editor = loaded.editor!!
            assertTrue(editor.manual)
            assertFalse(editor.offersDistance)
            loaded.eventSink(TrimEvent.ModeChanged(byDistance = true))
            loaded.eventSink(TrimEvent.EditExact(start = true, UnitSystem.Metric))
            val opened = awaitUntil { it.exact != null }
            val open = assertNotNull(opened.exact)
            assertFalse(opened.editor!!.byDistance, "a manual entry has no distance to choose by")
            // The UTC offset is unknown, so the device's clock reads the start; ten minutes after it is 600 s.
            val startClock = open.field.text.toString()
            val (h, m) = startClock.split(':').map(String::toInt)
            val later = (h * 60 + m + 10).let { "${(it / 60 % 24).toString().padStart(2, '0')}:${(it % 60).toString().padStart(2, '0')}" }
            open.field.setTextAndPlaceCursorAtEnd(later)
            loaded.eventSink(TrimEvent.ApplyExact(UnitSystem.Metric))
            assertEquals(600, awaitUntil { it.exact == null }.editor!!.from)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun doneKeepsTheSpanForTheDraftAndLeaves() = runTest {
        offer()
        val screen = TrimScreen(draftId = DRAFT)
        val navigator = FakeNavigator(screen)
        presenterTestOf(presenter(screen, navigator)) {
            val loaded = awaitEditor()
            loaded.eventSink(TrimEvent.ApplySuggestion)
            awaitUntil { it.editor?.suggestionApplied == true }.eventSink(TrimEvent.Save)
            navigator.awaitPop()
            assertEquals(KeptSpan(STANDING, RIDE_END), drafts.span(DRAFT).first())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSavedActivityThatCantBeReadFailsToLoad() = runTest {
        val uri = "at://${fixture.did.value}/${Nsid.FEED_ACTIVITY}/3kmissing"
        presenterTestOf(presenter(TrimScreen(uri = uri))) {
            val failed = awaitUntil { it.content.status is LoadStatus.Failed }
            assertNull(failed.editor)
            assertFalse(failed.canSave)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val DRAFT = "recording"
        const val STANDING = 360
        const val RIDE_END = STANDING + 3600
        const val DRIVE_START = RIDE_END + 540
        const val END = DRIVE_START + 240
    }
}
