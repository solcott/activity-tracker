package io.github.solcott.activitytracker.app.links

import io.github.solcott.activitytracker.app.gear.EditGearCollectionScreen
import io.github.solcott.activitytracker.app.gear.EditGearScreen
import io.github.solcott.activitytracker.app.gear.GearCollectionScreen
import io.github.solcott.activitytracker.app.gear.GearListScreen
import io.github.solcott.activitytracker.app.profile.BlockListKind
import io.github.solcott.activitytracker.app.profile.BlockListScreen
import io.github.solcott.activitytracker.app.profile.FollowListKind
import io.github.solcott.activitytracker.app.profile.FollowListScreen
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.settings.SettingsScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/** Every linkable screen's path, written and read back (ui/deep-links "Every linkable page has one path", "Links come in three forms"). */
class LinkRoundTripTest {
    private val links = AppLinks("appview.example.com")

    private suspend fun open(url: String) =
        AppLinkRoutes.plan(assertNotNull(links.parse(url), url), VIEWER) { if (it == VIEWER_HANDLE) VIEWER else null }

    @Test
    fun everySampleWritesItsPathAndReadsBackItsScreen() = runTest {
        for ((name, sample) in linkSamples) {
            assertEquals(sample.path, AppLinkRoutes.linkOf(sample.screen)?.path, "$name's path")
            assertEquals(sample.path, AppLinkRoutes.linkOf(sample.canonical)?.path, "$name's canonical path")
            assertEquals(sample.canonical, open(sample.path)?.chain?.lastOrNull() ?: tabRoot(sample.path), "$name read back")
        }
    }

    /** A link to a tab's first screen opens it with nothing above: the sample's screen stands in for the tab's root. */
    private fun tabRoot(path: String) = linkSamples.values.first { it.path == path }.screen

    @Test
    fun allThreeFormsOpenTheSamePage() = runTest {
        val path = "/segment/did:plc:carol/3kseg/leaderboard?board=run"
        val bare = open(path)
        assertEquals(bare, open("https://appview.example.com$path"))
        assertEquals(bare, open("activitytracker:/$path"))
        assertEquals(bare, open("HTTPS://AppView.Example.com$path"))
    }

    @Test
    fun aDidWithAPortSurvives() = runTest {
        val did = "did:web:localhost%3A8081"
        val profile = ProfileScreen(did)
        val path = assertNotNull(AppLinkRoutes.linkOf(profile)).path
        assertEquals("/profile/did:web:localhost%253A8081", path)
        assertEquals(LinkPlan(LinkTab.Current, listOf(profile)), open(path))
        assertEquals(LinkPlan(LinkTab.Current, listOf(profile)), open("activitytracker:/$path"))
    }

    @Test
    fun otherHostsAndReservedPathsAreNotLinks() {
        assertNull(links.parse("https://example.com/segment/did:plc:carol/3kseg"))
        assertNull(links.parse("http://appview.example.com/segment/did:plc:carol/3kseg"), "only https")
        assertNull(links.parse("https://appview.example.com/oauth/client-metadata.json"))
        assertNull(links.parse("https://appview.example.com/xrpc/app.activitytracker.feed.getSegment"))
        assertNull(links.parse("https://appview.example.com/.well-known/assetlinks.json"))
        assertNull(links.parse("activitytracker://oauth/callback"))
        assertNull(links.parse("/oauth/callback?code=x&state=y"))
        assertNull(links.parse("com.example.appview-dev:/oauth/callback?code=x"))
        assertNull(AppLinks(null).parse("https://appview.example.com/search"), "no link host in this build")
    }

    @Test
    fun unknownPathsOpenNothing() = runTest {
        assertNull(open("activitytracker://nowhere"))
        assertNull(open("/segment/did:plc:carol"))
        assertNull(open("/profile/$BOB/activity/3kact/elsewhere"))
        assertNull(open("/profile/$BOB/activity/3kact/media/x"))
        assertNull(open("/settings/account/more"))
        assertNull(open("/search/more"))
    }

    @Test
    fun lessCommonPathsReadBack() = runTest {
        assertEquals(
            LinkPlan(LinkTab.Current, listOf(ProfileScreen(BOB), FollowListScreen(BOB, FollowListKind.Following))),
            open("/profile/$BOB/following"),
        )
        assertEquals(
            LinkPlan(LinkTab.You, listOf(SettingsScreen, BlockListScreen(BlockListKind.Blocked))),
            open("/settings/blocked"),
        )
        assertEquals(
            LinkPlan(LinkTab.You, listOf(SettingsScreen, GearListScreen, EditGearScreen(mountOn = "at://x", replacing = "at://y"))),
            open("/settings/gear/new?mountOn=at%3A%2F%2Fx&replacing=at%3A%2F%2Fy"),
        )
        assertEquals(
            LinkPlan(LinkTab.You, listOf(SettingsScreen, GearListScreen, GearCollectionScreen("3kcol"), EditGearCollectionScreen("3kcol"))),
            open("/settings/gear/collection/3kcol/edit"),
        )
        assertEquals("/settings/gear/collection/3kcol/edit", AppLinkRoutes.linkOf(EditGearCollectionScreen("3kcol"))?.path)
    }
}
