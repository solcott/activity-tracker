package io.github.solcott.activitytracker.app.nav

import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.feed.FeedScreen
import io.github.solcott.activitytracker.app.profile.EditProfileScreen
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.record.ManualActivityScreen
import io.github.solcott.activitytracker.app.record.RecordScreen
import io.github.solcott.activitytracker.app.search.AthleteSearchScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TopLevelDestinationTest {
    private val viewer = "did:plc:viewer"

    @Test
    fun eachTabIsFoundFromItsRoot() {
        TopLevelDestination.entries.forEach { assertEquals(it, TopLevelDestination.of(it.screen(viewer), viewer)) }
        assertEquals(TopLevelDestination.Feed, TopLevelDestination.of(FeedScreen, viewer))
        assertEquals(TopLevelDestination.Search, TopLevelDestination.of(AthleteSearchScreen, viewer))
    }

    @Test
    fun onlyTheViewersOwnProfileIsTheYouTab() {
        assertEquals(TopLevelDestination.You, TopLevelDestination.of(ProfileScreen(viewer), viewer))
        assertNull(TopLevelDestination.of(ProfileScreen("did:plc:someone"), viewer))
    }

    @Test
    fun otherScreensAreNoTabsRoot() {
        assertNull(TopLevelDestination.of(ActivityDetailScreen("at://did:plc:a/app.activitytracker.activity/1"), viewer))
    }

    @Test
    fun navigationHidesOnlyOverFullScreenFlows() {
        assertTrue(TopLevelDestination.showsNavigation(FeedScreen))
        assertTrue(TopLevelDestination.showsNavigation(ProfileScreen("did:plc:someone")))
        assertTrue(TopLevelDestination.showsNavigation(ActivityDetailScreen("at://did:plc:a/app.activitytracker.activity/1")))
        assertFalse(TopLevelDestination.showsNavigation(RecordScreen))
        assertFalse(TopLevelDestination.showsNavigation(ManualActivityScreen))
        assertFalse(TopLevelDestination.showsNavigation(EditProfileScreen))
    }
}
