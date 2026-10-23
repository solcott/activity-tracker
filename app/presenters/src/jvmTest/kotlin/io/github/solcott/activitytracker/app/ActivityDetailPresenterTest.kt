package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent
import io.github.solcott.activitytracker.app.detail.ActivityDetailPresenter
import io.github.solcott.activitytracker.app.detail.ActivityPage
import io.github.solcott.activitytracker.app.detail.DetailSection
import io.github.solcott.activitytracker.app.detail.FigureKey
import io.github.solcott.activitytracker.app.detail.FigureValue
import io.github.solcott.activitytracker.app.detail.StatsGroupKind
import io.github.solcott.activitytracker.app.detail.StreamKind
import io.github.solcott.activitytracker.app.detail.detailSections
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.GearView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class ActivityDetailPresenterTest : ActivityDetailTestBase() {
    @Test
    fun showsHidesAndOpensLikingAthletes() = runTest {
        val navigator = FakeNavigator(screen)
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                navigator,
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
                r.blocks,
                r.reports,
                r.segments,
            )
        }) {
            val loaded = awaitLoaded()
            assertNull(loaded.likesList)

            loaded.eventSink(ActivityDetailEvent.ToggleLikesList)
            val shown = awaitLikes { it.athletes.isNotEmpty() }
            assertEquals(listOf("carol.test"), shown.likesList?.athletes?.map { it.handle.value })

            shown.eventSink(ActivityDetailEvent.OpenProfile(Did("did:plc:carol")))
            assertEquals(ProfileScreen("did:plc:carol"), navigator.awaitNextScreen())

            shown.eventSink(ActivityDetailEvent.ToggleLikesList)
            var hidden = awaitItem().page()!!
            while (hidden.likesList != null) hidden = awaitItem().page()!!
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun likesAFollowersActivity() = runTest {
        visibility = "followers"
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
                r.blocks,
                r.reports,
                r.segments,
            )
        }) {
            val loaded = awaitLoaded()
            assertEquals(1, loaded.activity.likeCount)
            loaded.eventSink(ActivityDetailEvent.ToggleLike)
            val liked = awaitUntil { it.page()?.activity?.viewer?.like != null }.page()!!
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
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
                r.blocks,
                r.reports,
                r.segments,
            )
        }) {
            val loaded = awaitLoaded()
            assertNull(loaded.likeError)
            loaded.eventSink(ActivityDetailEvent.ToggleLike)
            val refused = awaitUntil { it.page()?.likeError != null }.page()!!
            assertEquals(UiError(UiError.Kind.SignInAgain), refused.likeError)
            assertEquals(1, refused.activity.likeCount, "the like isn't shown")
            assertNull(refused.activity.viewer?.like)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun viewersOwnToggleShowsBeforeTheAppViewCatchesUp() = runTest {
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
                r.blocks,
                r.reports,
                r.segments,
            )
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
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
                r.blocks,
                r.reports,
                r.segments,
            )
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
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
                r.blocks,
                r.reports,
                r.segments,
            )
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
    fun eachChartedMetricIsAnOpenCard() = runTest {
        streams =
            """{"uri":"$activityUri","visibility":"public","channels":["heartRate","power"],""" +
                """"data":{"time":[0,1,2,3],"heartRate":[100,150,160,null],"power":[200,250,300,null]}}"""
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
                r.blocks,
                r.reports,
                r.segments,
            )
        }) {
            var state = awaitLoaded()
            while (state.charts == null) state = awaitLoaded()
            assertEquals(listOf(DetailSection.HeartRate, DetailSection.Power), state.sections)
            // Each is an open card, its chart in it: there's nothing to expand.
            assertEquals(listOf(DetailSection.HeartRate, DetailSection.Power), state.page.cards.map { it.section })
            assertTrue(state.page.cards.all { it.series.isNotEmpty() })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun noStreamsMeansNoCharts() = runTest {
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
                r.blocks,
                r.reports,
                r.segments,
            )
        }) {
            assertNull(awaitLoaded().charts)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theDeviceSectionListsTheGearTheViewerMaySee() {
        val created = Instant.parse("2026-10-05T08:00:00Z")
        val bike = GearView(AtUri("at://did:plc:alice/${Nsid.GEAR_ITEM}/3bike"), "Stumpjumper", GearType.BIKE, kind = "mountain")
        val helmet = GearView(AtUri("at://did:plc:alice/${Nsid.GEAR_ITEM}/3helmet"), "Helmet", GearType.HELMET)
        val view =
            ActivityView(
                uri = AtUri("at://did:plc:alice/${Nsid.FEED_ACTIVITY}/3kride"),
                cid = Cid("bafyact"),
                author = ProfileViewBasic(Did("did:plc:alice"), Handle("alice.test")),
                record = ActivityRecord(Sport.RIDE, created, 3600, created, gear = listOf(bike.uri, helmet.uri)),
                likeCount = 0,
                commentCount = 0,
                indexedAt = created,
                gear = listOf(bike, helmet),
            )
        assertEquals(listOf(DetailSection.Device), detailSections(view, null))
        assertEquals(emptyList(), detailSections(view.copy(gear = null), null), "gear the viewer may not see shows nothing")

        val device = ActivityPage.build(view.record, null, gear = view.gear.orEmpty()).stats.single { it.kind == StatsGroupKind.Device }
        assertEquals(
            listOf(FigureValue.GearItem("Stumpjumper", GearType.BIKE, "mountain"), FigureValue.GearItem("Helmet", GearType.HELMET, null)),
            device.rows.map { it.value },
        )
        assertTrue(device.rows.all { it.key == FigureKey.GearItem })
    }
}
