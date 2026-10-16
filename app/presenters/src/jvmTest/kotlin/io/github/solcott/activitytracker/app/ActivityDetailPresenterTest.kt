package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent
import io.github.solcott.activitytracker.app.detail.ActivityDetailPresenter
import io.github.solcott.activitytracker.app.detail.ActivityDetailState
import io.github.solcott.activitytracker.app.detail.DetailSection
import io.github.solcott.activitytracker.app.detail.StreamKind
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class ActivityDetailPresenterTest : ActivityDetailTestBase() {
    @Test
    fun showsHidesAndOpensLikingAthletes() = runTest {
        val navigator = FakeNavigator(screen)
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(screen, navigator, r.feed, r.activities, r.profiles, localLikes, r.manager, r.exporter)
        }) {
            val loaded = awaitLoaded()
            assertNull(loaded.likesList)

            loaded.eventSink(ActivityDetailEvent.ToggleLikesList)
            val shown = awaitLikes { it.athletes.isNotEmpty() }
            assertEquals(listOf("carol.test"), shown.likesList?.athletes?.map { it.handle.value })

            shown.eventSink(ActivityDetailEvent.OpenProfile(Did("did:plc:carol")))
            assertEquals(ProfileScreen("did:plc:carol"), navigator.awaitNextScreen())

            shown.eventSink(ActivityDetailEvent.ToggleLikesList)
            var hidden = awaitItem() as ActivityDetailState.Loaded
            while (hidden.likesList != null) hidden = awaitItem() as ActivityDetailState.Loaded
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun likesAFollowersActivity() = runTest {
        visibility = "followers"
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localLikes, r.manager, r.exporter)
        }) {
            val loaded = awaitLoaded()
            assertEquals(1, loaded.activity.likeCount)
            loaded.eventSink(ActivityDetailEvent.ToggleLike)
            val liked = awaitUntil { (it as? ActivityDetailState.Loaded)?.activity?.viewer?.like != null } as ActivityDetailState.Loaded
            assertEquals(2, liked.activity.likeCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSessionWhoseScopeLacksLikesIsAskedToSignInAgain() = runTest {
        visibility = "followers"
        // Signed in before the interactions space's declaration listed likes.
        val r = repos(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}")
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localLikes, r.manager, r.exporter)
        }) {
            val loaded = awaitLoaded()
            assertNull(loaded.likeError)
            loaded.eventSink(ActivityDetailEvent.ToggleLike)
            val refused = awaitUntil { (it as? ActivityDetailState.Loaded)?.likeError != null } as ActivityDetailState.Loaded
            assertEquals(UiError(UiError.Kind.SignInAgain, null), refused.likeError)
            assertEquals(1, refused.activity.likeCount, "the like isn't shown")
            assertNull(refused.activity.viewer?.like)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun viewersOwnToggleShowsBeforeTheAppViewCatchesUp() = runTest {
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localLikes, r.manager, r.exporter)
        }) {
            awaitLoaded().eventSink(ActivityDetailEvent.ToggleLikesList)
            awaitLikes { it.athletes.isNotEmpty() }

            // Alice likes it locally; the AppView still lists only Carol, so Alice is added from her profile.
            localLikes.set(AtUri(activityUri), AtUri("at://did:plc:alice/${Nsid.FEED_LIKE}/3kk1"))
            val given = awaitLikes { it.athletes.size == 2 }
            assertEquals(listOf("Alice", null), given.likesList?.athletes?.map { it.displayName })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun viewersRemovedLikeIsDroppedFromAStaleList() = runTest {
        likesFromAppView = listOf(staleAlice, carol)
        val r = repos()
        localLikes.set(AtUri(activityUri), null)
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localLikes, r.manager, r.exporter)
        }) {
            awaitLoaded().eventSink(ActivityDetailEvent.ToggleLikesList)
            val shown = awaitLikes { it.athletes.isNotEmpty() }
            assertEquals(listOf("carol.test"), shown.likesList?.athletes?.map { it.handle.value })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun chartsTheStreamsTheViewerMaySee() = runTest {
        streams =
            """{"uri":"$activityUri","visibility":"public","channels":["heartRate"],""" +
                """"data":{"time":[0,1,2,3],"heartRate":[100,150,160,null]},""" +
                """"heartRateZones":{"model":"cogganHeartRate","lows":[0,138,154,164,176]}}"""
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localLikes, r.manager, r.exporter)
        }) {
            var state = awaitLoaded()
            while (state.charts == null) state = awaitLoaded()
            val charts = state.charts!!
            assertEquals(listOf(StreamKind.HeartRate), charts.lines.map { it.kind })
            assertEquals(listOf(1, 1, 1, 0, 0), charts.heartRateZones?.map { it.seconds })
            assertFalse(state.chartsMissingZones)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun sectionsStartCollapsedAndToggleOneOrAll() = runTest {
        streams =
            """{"uri":"$activityUri","visibility":"public","channels":["heartRate","power"],""" +
                """"data":{"time":[0,1,2,3],"heartRate":[100,150,160,null],"power":[200,250,300,null]}}"""
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localLikes, r.manager, r.exporter)
        }) {
            var state = awaitLoaded()
            while (state.charts == null) state = awaitLoaded()
            assertEquals(listOf(DetailSection.HeartRate, DetailSection.Power), state.sections)
            assertEquals(emptySet(), state.expanded)

            state.eventSink(ActivityDetailEvent.ToggleSection(DetailSection.HeartRate))
            state = awaitUntil { (it as? ActivityDetailState.Loaded)?.anyExpanded == true } as ActivityDetailState.Loaded
            assertEquals(setOf(DetailSection.HeartRate), state.expanded)

            // Something open, so all collapse; then nothing open, so all expand.
            state.eventSink(ActivityDetailEvent.ToggleAll)
            state = awaitUntil { (it as? ActivityDetailState.Loaded)?.anyExpanded == false } as ActivityDetailState.Loaded
            state.eventSink(ActivityDetailEvent.ToggleAll)
            state = awaitUntil { (it as? ActivityDetailState.Loaded)?.anyExpanded == true } as ActivityDetailState.Loaded
            assertEquals(state.sections.toSet(), state.expanded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun noStreamsMeansNoCharts() = runTest {
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, localLikes, r.manager, r.exporter)
        }) {
            assertNull(awaitLoaded().charts)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
