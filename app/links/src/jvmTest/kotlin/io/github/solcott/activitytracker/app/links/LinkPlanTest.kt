package io.github.solcott.activitytracker.app.links

import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.edit.EditActivityScreen
import io.github.solcott.activitytracker.app.feed.FeedScreen
import io.github.solcott.activitytracker.app.invites.AddPeopleScreen
import io.github.solcott.activitytracker.app.profile.FollowListKind
import io.github.solcott.activitytracker.app.profile.FollowListScreen
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.segment.SegmentLeaderboardScreen
import io.github.solcott.activitytracker.app.segment.SegmentMayorsScreen
import io.github.solcott.activitytracker.app.segment.SegmentScreen
import io.github.solcott.activitytracker.app.settings.SettingsScreen
import io.github.solcott.activitytracker.app.settings.ThresholdHistoryScreen
import io.github.solcott.activitytracker.app.settings.TrainingZonesScreen
import io.github.solcott.activitytracker.lexicon.Nsid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/** The tab and stack a link builds (ui/deep-links: tabs, parents, handles, owner-only pages) and how a stack takes it. */
class LinkPlanTest {
    private val links = AppLinks("appview.example.com")
    private val bobActivity = "at://$BOB/${Nsid.FEED_ACTIVITY}/3kact"
    private val segment = "at://did:plc:carol/${Nsid.FEED_SEGMENT}/3kseg"

    private suspend fun open(url: String): LinkPlan? =
        AppLinkRoutes.plan(links.parse(url)!!, VIEWER) {
            when (it) {
                VIEWER_HANDLE -> VIEWER
                "bob.test" -> BOB
                else -> null
            }
        }

    @Test
    fun ownSettingsPageOpensOnYouWithItsParents() = runTest {
        assertEquals(
            LinkPlan(LinkTab.You, listOf(SettingsScreen, TrainingZonesScreen, ThresholdHistoryScreen)),
            open("/settings/training-zones/history"),
        )
    }

    @Test
    fun someoneElsesActivityOpensOnTheShownTab() = runTest {
        assertEquals(LinkPlan(LinkTab.Current, listOf(ActivityDetailScreen(bobActivity))), open("/profile/$BOB/activity/3kact"))
    }

    @Test
    fun ownProfileIsTheYouTabAlone() = runTest {
        assertEquals(LinkPlan(LinkTab.You, emptyList()), open("/profile/$VIEWER"))
        assertEquals(LinkPlan(LinkTab.You, emptyList()), open("/profile/$VIEWER_HANDLE"), "by handle too")
        assertEquals(
            LinkPlan(LinkTab.You, listOf(FollowListScreen(VIEWER, FollowListKind.Followers))),
            open("/profile/$VIEWER/followers"),
        )
    }

    @Test
    fun tabRootsReturnToTheirTab() = runTest {
        assertEquals(LinkPlan(LinkTab.Feed, emptyList()), open("/"))
        assertEquals(LinkPlan(LinkTab.Feed, emptyList()), open("https://appview.example.com"))
        assertEquals(LinkPlan(LinkTab.Search, emptyList()), open("/search"))
    }

    @Test
    fun aLeaderboardHasItsSegmentUnderIt() = runTest {
        assertEquals(
            LinkPlan(LinkTab.Current, listOf(SegmentScreen(segment), SegmentLeaderboardScreen(segment))),
            open("/segment/did:plc:carol/3kseg/leaderboard"),
        )
    }

    @Test
    fun handlesResolveAndAnUnknownOneOpensItsProfile() = runTest {
        assertEquals(LinkPlan(LinkTab.Current, listOf(ActivityDetailScreen(bobActivity))), open("/profile/bob.test/activity/3kact"))
        assertEquals(LinkPlan(LinkTab.Current, listOf(ProfileScreen("nobody.test"))), open("/profile/nobody.test/activity/3kact"))
        assertEquals(LinkPlan(LinkTab.Current, listOf(ProfileScreen("nobody.test"))), open("/segment/nobody.test/3kseg"))
        assertEquals(LinkPlan(LinkTab.Current, listOf(ProfileScreen(BOB))), open("/profile/bob.test"), "written with the DID")
    }

    @Test
    fun ownerOnlyPagesFallBackToTheActivity() = runTest {
        assertEquals(LinkPlan(LinkTab.Current, listOf(ActivityDetailScreen(bobActivity))), open("/profile/$BOB/activity/3kact/edit"))
        assertEquals(LinkPlan(LinkTab.Current, listOf(ActivityDetailScreen(bobActivity))), open("/profile/$BOB/activity/3kact/people"))
        val own = "at://$VIEWER/${Nsid.FEED_ACTIVITY}/3kown"
        assertEquals(
            LinkPlan(LinkTab.Current, listOf(ActivityDetailScreen(own), EditActivityScreen(own))),
            open("/profile/$VIEWER_HANDLE/activity/3kown/edit"),
        )
        assertEquals(
            LinkPlan(LinkTab.Current, listOf(ActivityDetailScreen(own), AddPeopleScreen(own))),
            open("/profile/$VIEWER/activity/3kown/people"),
        )
    }

    @Test
    fun aStackKeepsWhatTheChainAlreadyHolds() {
        val seg = SegmentScreen(segment)
        val mayors = SegmentMayorsScreen(segment)
        assertEquals(StackEdit(2, listOf(mayors)), StackEdit.of(listOf(FeedScreen, seg), listOf(seg, mayors)), "segment already open")
        assertEquals(
            StackEdit(2, listOf(seg, mayors)),
            StackEdit.of(listOf(FeedScreen, ActivityDetailScreen(bobActivity)), listOf(seg, mayors)),
            "pushed above what's shown",
        )
        assertEquals(StackEdit(1, emptyList()), StackEdit.of(listOf(FeedScreen, seg), emptyList()), "back to the tab's first screen")
        assertEquals(StackEdit(3, emptyList()), StackEdit.of(listOf(FeedScreen, seg, mayors), listOf(seg, mayors)), "already showing")
        assertEquals(
            StackEdit(2, listOf(mayors)),
            StackEdit.of(listOf(FeedScreen, seg, ActivityDetailScreen(bobActivity)), listOf(seg, mayors)),
            "what was above the kept screen goes",
        )
        assertEquals(StackEdit(1, listOf(seg)), StackEdit.of(listOf(FeedScreen), listOf(FeedScreen, seg)), "the root is kept")
    }

    @Test
    fun aPageOpenedInTheAppMatchesItsLinkWhateverItWasOpenedWith() {
        val seg = SegmentScreen(segment)
        val board = SegmentLeaderboardScreen(segment)
        val titled = SegmentLeaderboardScreen(segment, name = "Hill")
        assertEquals(
            StackEdit(3, emptyList()),
            StackEdit.of(listOf(FeedScreen, seg, titled), listOf(seg, board)),
            "the titled board is kept",
        )
        val uploaded = ActivityDetailScreen(bobActivity, suggestOnOpen = true)
        assertEquals(
            StackEdit(2, emptyList()),
            StackEdit.of(listOf(FeedScreen, uploaded), listOf(ActivityDetailScreen(bobActivity))),
            "an activity opened to suggest segments",
        )
        assertEquals(
            StackEdit(2, listOf(SegmentLeaderboardScreen(segment, board = "run"))),
            StackEdit.of(listOf(FeedScreen, seg, titled), listOf(seg, SegmentLeaderboardScreen(segment, board = "run"))),
            "another board is another page",
        )
    }
}
