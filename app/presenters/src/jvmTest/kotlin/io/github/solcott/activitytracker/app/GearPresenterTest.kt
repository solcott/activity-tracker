package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.gear.EditGearScreen
import io.github.solcott.activitytracker.app.gear.GearEvent
import io.github.solcott.activitytracker.app.gear.GearPresenter
import io.github.solcott.activitytracker.app.gear.GearScreen
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GearTotals
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.GearUsage
import io.github.solcott.activitytracker.lexicon.app.GearView
import io.github.solcott.activitytracker.lexicon.app.GetGear
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.uistate.hasLoaded
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** A gear item's page (gear/gear-usage): the owner's with Edit, a follower's without, and an item the viewer may not see. */
class GearPresenterTest {
    private val fixture = SessionTestFixture()
    private val created = Instant.parse("2026-10-20T12:00:00Z")
    private val totals =
        GearTotals(activities = 1, movingTime = 3_600, distance = 40_000, elevationGain = 300, elevationLoss = 300, days = 1)

    private fun item(author: Did, rkey: String = "3tarmac"): String {
        val uri = AtUri.of(author, Nsid.GEAR_ITEM, rkey)
        fixture.gearUsage[uri.value] =
            GetGear.Output(
                gear = GearView(uri, "Tarmac", GearType.BIKE),
                usage = GearUsage(totals, emptyList(), emptyList()),
                retired = false,
                visibility = Visibility.PUBLIC.takeIf { author == fixture.did },
            )
        return uri.value
    }

    private fun ride(author: Did) =
        ActivityView(
            uri = AtUri.of(author, Nsid.FEED_ACTIVITY, "3ride"),
            cid = Cid("bafyride"),
            author = ProfileViewBasic(author, Handle("${author.value.substringAfterLast(':')}.test")),
            record = ActivityRecord(Sport.RIDE, created, 3_600, created, distance = 40_000),
            likeCount = 0,
            commentCount = 0,
            indexedAt = created,
        )

    @Test
    fun theOwnersPageShowsTheTotalsAndActivitiesAndOpensEdit() = runTest {
        val uri = item(fixture.did)
        fixture.gearActivities = listOf(ride(fixture.did))
        val repos = fixture.repos()
        val navigator = FakeNavigator(GearScreen(uri))
        presenterTestOf({ GearPresenter(GearScreen(uri), navigator, repos.gear, repos.activities) }) {
            val loaded = awaitUntil { it.content.hasLoaded && it.activities.items.isNotEmpty() }
            val page = checkNotNull(loaded.page)
            assertTrue(page.isOwner)
            assertEquals(Visibility.PUBLIC, page.visibility)
            assertEquals(40_000, page.usage.totals.distance)
            assertEquals(listOf("3ride"), loaded.activities.items.map { it.uri.rkey })
            loaded.eventSink(GearEvent.Edit)
            assertEquals(EditGearScreen("3tarmac"), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFollowersPageHasNoEdit() = runTest {
        val bob = Did("did:plc:bob")
        val uri = item(bob)
        fixture.gearActivities = listOf(ride(bob))
        val repos = fixture.repos()
        val navigator = FakeNavigator(GearScreen(uri))
        presenterTestOf({ GearPresenter(GearScreen(uri), navigator, repos.gear, repos.activities) }) {
            val loaded = awaitUntil { it.content.hasLoaded && it.activities.items.isNotEmpty() }
            val page = checkNotNull(loaded.page)
            assertFalse(page.isOwner)
            assertNull(page.visibility)
            loaded.eventSink(GearEvent.Edit)
            navigator.expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anItemTheViewerMayNotSeeIsNotFound() = runTest {
        val uri = AtUri.of(Did("did:plc:bob"), Nsid.GEAR_ITEM, "3private").value
        val repos = fixture.repos()
        presenterTestOf({ GearPresenter(GearScreen(uri), FakeNavigator(GearScreen(uri)), repos.gear, repos.activities) }) {
            val state = awaitUntil { it.notFound }
            assertFalse(state.content.hasLoaded)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
