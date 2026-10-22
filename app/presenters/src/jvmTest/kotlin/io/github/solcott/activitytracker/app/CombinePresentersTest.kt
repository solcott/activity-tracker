package io.github.solcott.activitytracker.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withCompositionLocals
import com.slack.circuit.retained.LocalRetainedStateRegistry
import com.slack.circuit.retained.RetainedStateRegistry
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.detail.CombinePickerEvent
import io.github.solcott.activitytracker.app.detail.CombinePickerScreen
import io.github.solcott.activitytracker.app.detail.CombinePickerState
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.uistate.errorOrNull
import io.github.solcott.uistate.hasLoaded
import io.github.solcott.uistate.isLoading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

/** Combining from an activity's page (`feed/combined-activities`): the picker, and the combined activity's own actions. */
class CombinePresentersTest : CombineTestBase() {
    @Test
    fun thePickerOffersOnlyWhatCanBeCombined() = runTest {
        day()
        presenterTestOf(picker(uri("3kwatch"), FakeNavigator(CombinePickerScreen(uri("3kwatch"))))) {
            val state = awaitUntil { it.content.hasLoaded }
            // Not itself, not a combined activity, not another one's member; oldest first.
            assertEquals(listOf("3kedge", "3kevening"), state.candidates.map { it.uri.rkey })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun nothingToCombineIsAnEmptyAnswer() = runTest {
        records["3kwatch"] = ride("2026-10-14T09:00:00Z")
        presenterTestOf(picker(uri("3kwatch"), FakeNavigator(CombinePickerScreen(uri("3kwatch"))))) {
            val state = awaitUntil { it.content.hasLoaded && !it.content.isLoading }
            assertTrue(state.candidates.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun comingBackShowsTheCandidatesAtOnce() = runTest {
        day()
        val pickerOf = picker(uri("3kwatch"), FakeNavigator(CombinePickerScreen(uri("3kwatch"))))
        val registry = RetainedStateRegistry()
        var shown by mutableStateOf(true)
        presenterTestOf({
            withCompositionLocals(LocalRetainedStateRegistry provides registry) {
                // A stand-in for the screen it opened while it's on the back stack.
                if (shown) pickerOf() else CombinePickerState()
            }
        }) {
            awaitUntil { it.content.hasLoaded && !it.content.isLoading }
            registry.saveAll()
            shown = false
            awaitUntil { !it.content.hasLoaded }
            shown = true
            assertEquals(2, awaitItem().candidates.size, "no loading state in between")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedLoadSaysWhyAndRetries() = runTest {
        day()
        failByStart = true
        presenterTestOf(picker(uri("3kwatch"), FakeNavigator(CombinePickerScreen(uri("3kwatch"))))) {
            val failed = awaitUntil { it.content.errorOrNull != null }
            assertFalse(failed.content.hasLoaded)
            assertNull(failed.error, "a load failure isn't the combine's")
            failByStart = false
            failed.eventSink(CombinePickerEvent.Retry)
            assertEquals(2, awaitUntil { it.content.hasLoaded && it.content.errorOrNull == null }.candidates.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun pickingCombinesOnceAndOpensTheCombinedActivity() = runTest {
        day()
        gate = CompletableDeferred()
        val navigator = FakeNavigator(ActivityDetailScreen(uri("3kwatch")), CombinePickerScreen(uri("3kwatch")))
        presenterTestOf(picker(uri("3kwatch"), navigator)) {
            val loaded = awaitUntil { it.content.hasLoaded }
            loaded.eventSink(CombinePickerEvent.Pick(AtUri(uri("3kedge"))))
            awaitUntil { it.combining != null }
            loaded.eventSink(CombinePickerEvent.Pick(AtUri(uri("3kedge"))))
            gate.complete(Unit)

            navigator.awaitPop()
            navigator.awaitPop()
            val opened = assertIs<ActivityDetailScreen>(navigator.awaitNextScreen())
            val combined = opened.uri.substringAfterLast('/')
            assertEquals(setOf(uri("3kwatch"), uri("3kedge")), records.getValue(combined).membersOf().toSet())
            assertEquals(1, writes.count { it == "put ${Nsid.FEED_ACTIVITY}/$combined" }, "a second tap does nothing")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedCombineIsTheActionsErrorAndKeepsTheCandidates() = runTest {
        day()
        // The members can't be read from the PDS to build it from.
        failReads = true
        presenterTestOf(picker(uri("3kwatch"), FakeNavigator(CombinePickerScreen(uri("3kwatch"))))) {
            awaitUntil { it.content.hasLoaded }.eventSink(CombinePickerEvent.Pick(AtUri(uri("3kedge"))))
            val failed = awaitUntil { it.error != null }
            assertEquals(UiError.Kind.CombineActivity, failed.error?.kind)
            assertNull(failed.content.errorOrNull)
            assertEquals(listOf("3kedge", "3kevening"), failed.candidates.map { it.uri.rkey })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theCombineActionOpensThePicker() = runTest {
        day()
        val navigator = FakeNavigator(ActivityDetailScreen(uri("3kwatch")))
        presenterTestOf(detail(uri("3kwatch"), navigator)) {
            val loaded = awaitLoaded()
            assertTrue(loaded.manage?.canCombine == true)
            loaded.eventSink(ActivityDetailEvent.Combine)
            assertEquals(CombinePickerScreen(uri("3kwatch")), navigator.awaitNextScreen())
            loaded.eventSink(ActivityDetailEvent.AddRecording)
            assertEquals(ImportActivityScreen(combineInto = uri("3kwatch")), navigator.awaitNextScreen(), "a file to combine with it")
            cancelAndIgnoreRemainingEvents()
        }
        presenterTestOf(detail(uri("3kother"), FakeNavigator(ActivityDetailScreen(uri("3kother"))))) {
            assertFalse(awaitLoaded().manage?.canCombine == true, "a member is combined through its combined activity")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun splittingLeavesThePage() = runTest {
        day()
        val navigator = FakeNavigator(ActivityDetailScreen(uri("3kday")))
        presenterTestOf(detail(uri("3kday"), navigator)) {
            val loaded = awaitLoaded()
            assertEquals(2, loaded.manage?.members)
            loaded.eventSink(ActivityDetailEvent.Split)
            navigator.awaitPop()
            assertTrue("delete ${Nsid.FEED_ACTIVITY}/3kday" in writes)
            assertTrue("3kother" in records && "3kmorning" in records, "members stay")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun removingAMemberRebuildsOrSplits() = runTest {
        day()
        records["3knoon"] = ride("2026-10-14T06:10:00Z", minutes = 40)
        records["3kday"] = ride("2026-10-14T06:00:00Z", members = listOf("3kother", "3kmorning", "3knoon"))
        val navigator = FakeNavigator(ActivityDetailScreen(uri("3kday")))
        presenterTestOf(detail(uri("3kday"), navigator)) {
            awaitLoaded().eventSink(ActivityDetailEvent.RemoveMember(AtUri(uri("3knoon"))))
            val rebuilt = awaitUntil { it.page()?.manage?.members == 2 }
            assertEquals(listOf(uri("3kother"), uri("3kmorning")), records.getValue("3kday").membersOf())

            rebuilt.eventSink(ActivityDetailEvent.RemoveMember(AtUri(uri("3kmorning"))))
            navigator.awaitPop()
            assertFalse("3kday" in records, "one member left: split")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
