package io.github.solcott.activitytracker.app.nav

import io.github.solcott.activitytracker.app.nav.HistoryOp.Back
import io.github.solcott.activitytracker.app.nav.HistoryOp.Push
import io.github.solcott.activitytracker.app.nav.HistoryOp.Replace
import io.github.solcott.activitytracker.app.nav.TopLevelDestination.Feed
import io.github.solcott.activitytracker.app.nav.TopLevelDestination.Search
import io.github.solcott.activitytracker.app.nav.TopLevelDestination.You
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The web app's history kept in step with its stack (ui/web-history), without a browser. */
class HistoryMirrorTest {
    private val mirror = HistoryMirror()

    private fun entry(key: String, tab: TopLevelDestination = Feed) = HistoryEntry(key, tab, "/$key")

    private val feed = entry("feed")
    private val activity = entry("activity")
    private val map = entry("map")

    @Test
    fun aLoadedStackIsAnEntryPerScreen() {
        val settings = entry("settings", You)
        val zones = entry("zones", You)
        val history = entry("history", You)
        val you = entry("you", You)

        assertEquals(
            listOf(Replace(0, you), Push(1, settings), Push(2, zones), Push(3, history)),
            mirror.start(listOf(you, settings, zones, history)),
        )
        assertEquals(3, mirror.index)
    }

    @Test
    fun openingScreensPushesAndGoingBackInTheAppGoesBackInTheHistory() {
        mirror.start(listOf(feed))

        assertEquals(listOf(Push(1, activity)), mirror.show(listOf(feed, activity)))
        assertEquals(listOf(Push(2, map)), mirror.show(listOf(feed, activity, map)))
        assertEquals(listOf(Back(1)), mirror.show(listOf(feed, activity)), "the page's own back")
        assertEquals(1, mirror.index)
        // The browser then reports landing where the app already is: nothing to do.
        assertEquals(activity, mirror.landed(1))
        assertEquals(emptyList(), mirror.show(listOf(feed, activity)))
    }

    @Test
    fun browserBackAndForwardLandOnTheEntriesWritten() {
        mirror.start(listOf(feed))
        mirror.show(listOf(feed, activity))
        mirror.show(listOf(feed, activity, map))

        assertEquals(activity, mirror.landed(1), "Back")
        assertEquals(emptyList(), mirror.show(listOf(feed, activity)), "the shell popped to it: the history is already there")
        assertEquals(map, mirror.landed(2), "Forward: the map, which the shell opens again")
        assertEquals(emptyList(), mirror.show(listOf(feed, activity, map)))
    }

    @Test
    fun aTabSwitchIsAnEntryAndBackReturnsToTheTabBefore() {
        mirror.start(listOf(feed))
        mirror.show(listOf(feed, activity))
        val search = entry("search", Search)

        assertEquals(listOf(Push(2, search)), mirror.show(listOf(search)))
        assertEquals(activity, mirror.landed(1), "Back lands on the Feed tab's activity")
        assertEquals(emptyList(), mirror.show(listOf(feed, activity)))
    }

    @Test
    fun aPageReplacedInPlaceRewritesTheCurrentEntry() {
        val settings = entry("settings", You)
        val defaults = entry("defaults", You)
        val zones = entry("privacy", You)
        val you = entry("you", You)
        mirror.start(listOf(you, settings, defaults))

        assertEquals(listOf(Replace(2, zones)), mirror.show(listOf(you, settings, zones)))
        assertEquals(2, mirror.index)
    }

    @Test
    fun goingBackPastWhatTheHistoryHoldsRewritesInstead() {
        val segment = entry("segment")
        val board = entry("board")
        mirror.start(listOf(feed, activity))
        // A link kept the feed and replaced what was above it: the history's entry under the top isn't the segment.
        assertEquals(listOf(Replace(1, board)), mirror.show(listOf(feed, segment, board)))

        assertEquals(listOf(Replace(1, segment)), mirror.show(listOf(feed, segment)))
        assertEquals(1, mirror.index)
    }

    @Test
    fun aLaterSessionNumbersOnAndClaimsNoneOfTheEntriesBelow() {
        mirror.start(listOf(feed))
        mirror.show(listOf(feed, activity))
        mirror.show(listOf(feed, activity, map))
        // Another account in the same page starts where the last left off (BrowserHistorySync keeps the place).
        val next = HistoryMirror()
        assertEquals(listOf(Replace(2, feed)), next.start(listOf(feed), at = mirror.index))
        assertNull(next.landed(1), "not the new session's (BrowserHistorySync stops Back above it)")
    }

    @Test
    fun anEntryFromBeforeAReloadIsTakenOverByTheStackItOpens() {
        mirror.start(listOf(feed, activity), at = 4)

        assertNull(mirror.landed(3), "written before the reload")
        assertEquals(listOf(Replace(3, map)), mirror.show(listOf(feed, map)))
    }
}
