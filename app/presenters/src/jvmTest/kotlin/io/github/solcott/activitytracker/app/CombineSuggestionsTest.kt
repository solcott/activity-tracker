package io.github.solcott.activitytracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withCompositionLocals
import app.cash.turbine.Event
import com.slack.circuit.retained.LocalRetainedStateRegistry
import com.slack.circuit.retained.RetainedStateRegistry
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.detail.ActivityDetailState
import io.github.solcott.activitytracker.data.activity.CombineCue
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.uistate.isLoading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

/** Suggesting on an activity's page another of the owner's activities to combine it with (`feed/combine-suggestions`). */
class CombineSuggestionsTest : CombineTestBase() {
    @Test
    fun aSaveAsksRightAwayAndNotNowLeavesABanner() = runTest {
        lunch()
        presenterTestOf(detail(uri("3kpm"), FakeNavigator(ActivityDetailScreen(uri("3kpm"), true)), suggestOnOpen = true)) {
            val loaded = awaitUntil { it.suggestion() != null }.page()!!
            val prompt = loaded.combineSuggestion!!
            assertTrue(prompt.asPrompt)
            assertEquals(CombineCue.Break, prompt.cue)
            assertEquals(uri("3kam"), prompt.other.uri.value)

            loaded.eventSink(ActivityDetailEvent.NotNowSuggestion)
            assertFalse(awaitUntil { it.suggestion()?.asPrompt == false }.suggestion()!!.asPrompt, "a banner now")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedLookupShowsNoSuggestionAndNoError() = runTest {
        lunch()
        failByStart = true
        presenterTestOf(detail(uri("3kam"), FakeNavigator(ActivityDetailScreen(uri("3kam"))))) {
            val settled = awaitUntil { it.page() != null && !it.content.isLoading }
            // The lookup is answered on the engine's own thread, after the page has settled.
            byStartAsked.await()
            val latest =
                cancelAndConsumeRemainingEvents().filterIsInstance<Event.Item<ActivityDetailState>>().lastOrNull()?.value ?: settled
            assertNull(latest.suggestion())
            assertNull(latest.page()?.manage?.error)
        }
    }

    @Test
    fun aPutOffPromptStaysABannerWhenComingBack() = runTest {
        lunch()
        val registry = RetainedStateRegistry()
        var shown by mutableStateOf(true)
        val page = detail(uri("3kpm"), FakeNavigator(ActivityDetailScreen(uri("3kpm"), true)), suggestOnOpen = true)
        presenterTestOf({
            withCompositionLocals(LocalRetainedStateRegistry provides registry) {
                if (shown) page() else ActivityDetailState(eventSink = {})
            }
        }) {
            awaitUntil { it.suggestion()?.asPrompt == true }.page()!!.eventSink(ActivityDetailEvent.NotNowSuggestion)
            awaitUntil { it.suggestion()?.asPrompt == false }
            registry.saveAll()
            shown = false
            awaitUntil { it.page() == null }
            shown = true
            val back = assertNotNull(awaitItem().suggestion(), "shown at once")
            assertFalse(back.asPrompt, "still a banner")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun dontCombineStaysHiddenAcrossAReload() = runTest {
        lunch()
        presenterTestOf(detail(uri("3kpm"), FakeNavigator(ActivityDetailScreen(uri("3kpm"))))) {
            awaitUntil { it.suggestion() != null }.page()!!.eventSink(ActivityDetailEvent.DontCombineSuggestion)
            val dismissed = awaitUntil { it.page() != null && it.suggestion() == null }
            // The reload looks again, and the answer stays hidden.
            byStartAsked = CompletableDeferred()
            dismissed.eventSink(ActivityDetailEvent.Retry)
            byStartAsked.await()
            val after = cancelAndConsumeRemainingEvents().filterIsInstance<Event.Item<ActivityDetailState>>().map { it.value }
            assertTrue(after.none { it.suggestion() != null })
        }
    }

    @Test
    fun openingThePageLaterShowsABanner() = runTest {
        lunch()
        presenterTestOf(detail(uri("3kam"), FakeNavigator(ActivityDetailScreen(uri("3kam"))))) {
            val banner = awaitUntil { it.suggestion() != null }.suggestion()!!
            assertFalse(banner.asPrompt)
            assertEquals(uri("3kpm"), banner.other.uri.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun dontCombineHidesItOnBothPages() = runTest {
        lunch()
        val g = graph()
        presenterTestOf(detail(uri("3kpm"), FakeNavigator(ActivityDetailScreen(uri("3kpm"))), shared = g)) {
            (awaitUntil { it.suggestion() != null }.page()!!).eventSink(ActivityDetailEvent.DontCombineSuggestion)
            awaitUntil { it.page() != null && it.suggestion() == null }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(null, g.suggestions.forActivity(g.feed.activity(AtUri(uri("3kam")))), "nor on the morning's page")
        assertEquals(null, g.suggestions.forActivity(g.feed.activity(AtUri(uri("3kpm")))))
    }

    @Test
    fun aFollowerSeesNoSuggestion() = runTest {
        lunch()
        bobs += setOf("3kam", "3kpm")
        presenterTestOf(detail(uri("3kpm"), FakeNavigator(ActivityDetailScreen(uri("3kpm"))))) {
            assertEquals(null, awaitLoaded().combineSuggestion)
            assertEquals(0, byStartQueries, "nothing is looked for on someone else's activity")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun combiningAddsToTheCombinedDayAndOpensIt() = runTest {
        records["3km1"] = ride("2026-10-14T09:00:00Z")
        records["3km2"] = ride("2026-10-14T10:30:00Z")
        records["3kday"] = ride("2026-10-14T09:00:00Z", minutes = 150, members = listOf("3km1", "3km2"))
        records["3klate"] = ride("2026-10-14T12:00:00Z")
        val navigator = FakeNavigator(ActivityDetailScreen(uri("3km2")), ActivityDetailScreen(uri("3klate"), true))
        presenterTestOf(detail(uri("3klate"), navigator, suggestOnOpen = true)) {
            val loaded = awaitUntil { it.suggestion() != null }.page()!!
            assertEquals(uri("3kday"), loaded.combineSuggestion!!.other.uri.value, "offered as the combined day")
            loaded.eventSink(ActivityDetailEvent.CombineSuggested)

            navigator.awaitPop()
            assertEquals(ActivityDetailScreen(uri("3kday")), navigator.awaitNextScreen())
            assertEquals(listOf("3km1", "3km2", "3klate"), records.getValue("3kday").membersOf().map { it.substringAfterLast('/') })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
