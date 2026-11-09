package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.settings.SegmentsAndGroupsEvent
import io.github.solcott.activitytracker.app.settings.SegmentsAndGroupsPresenter
import io.github.solcott.activitytracker.app.settings.SegmentsAndGroupsScreen
import io.github.solcott.activitytracker.data.segments.GroupChoice
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Settings' Segments & groups page: segment matching, the leaderboard choices and activity grouping. */
class SegmentsAndGroupsPresenterTest {
    private val fixture = SessionTestFixture()

    @Test
    fun backPops() = runTest {
        val repos = fixture.repos()
        val navigator = FakeNavigator(SegmentsAndGroupsScreen)
        presenterTestOf({ SegmentsAndGroupsPresenter(navigator, repos.segmentPrefs, repos.leaderboardBrackets, repos.groupPrefs) }) {
            awaitItem().eventSink(SegmentsAndGroupsEvent.Back)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun segmentMatchingStartsOffAndTurnsOnAndOff() = runTest {
        val repos = fixture.repos()
        presenterTestOf(
            {
                SegmentsAndGroupsPresenter(
                    FakeNavigator(SegmentsAndGroupsScreen),
                    repos.segmentPrefs,
                    repos.leaderboardBrackets,
                    repos.groupPrefs,
                )
            },
        ) {
            val loaded = awaitUntil { it.segmentMatching != null }
            assertEquals(false, loaded.segmentMatching, "never asked: off")
            assertTrue(loaded.canUseSegments)

            loaded.eventSink(SegmentsAndGroupsEvent.SegmentMatchingChanged(true))
            awaitUntil { it.segmentMatching == true && !it.savingSegments }.eventSink(SegmentsAndGroupsEvent.SegmentMatchingChanged(false))
            val off = awaitUntil { it.segmentMatching == false && !it.savingSegments }
            assertNull(off.error)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("false", AtJson.parseToJsonElement(fixture.segmentPrefs!!).jsonObject.getValue("matching").jsonPrimitive.content)
        assertNotNull(repos.segmentPrefs.saved.value, "each save starts the segment-track sweep for the session")
    }

    @Test
    fun theLeaderboardChoiceShowsOnlyWhileMatchingIsOnAndKeepsIt() = runTest {
        val repos = fixture.repos()
        presenterTestOf(
            {
                SegmentsAndGroupsPresenter(
                    FakeNavigator(SegmentsAndGroupsScreen),
                    repos.segmentPrefs,
                    repos.leaderboardBrackets,
                    repos.groupPrefs,
                )
            },
        ) {
            val off = awaitUntil { it.segmentMatching != null && it.showOnLeaderboards != null }
            assertEquals(true, off.showOnLeaderboards, "shown by default")
            off.eventSink(SegmentsAndGroupsEvent.LeaderboardsChanged(false))
            off.eventSink(SegmentsAndGroupsEvent.SegmentMatchingChanged(true))
            val on = awaitUntil { it.segmentMatching == true && !it.savingSegments }
            val saved = AtJson.parseToJsonElement(fixture.segmentPrefs!!).jsonObject
            assertFalse("hideFromLeaderboards" in saved, "not offered while matching was off, so never saved")
            assertEquals(true, on.showOnLeaderboards)
            on.eventSink(SegmentsAndGroupsEvent.LeaderboardsChanged(false))
            val hidden = awaitUntil { it.showOnLeaderboards == false && !it.savingLeaderboards }
            assertNull(hidden.error)
            val stored = AtJson.parseToJsonElement(fixture.segmentPrefs!!).jsonObject
            assertEquals("true", stored.getValue("hideFromLeaderboards").jsonPrimitive.content)
            assertEquals("true", stored.getValue("matching").jsonPrimitive.content, "matching kept")

            fixture.failWrites = true
            hidden.eventSink(SegmentsAndGroupsEvent.LeaderboardsChanged(true))
            val failed = awaitUntil { it.error != null && !it.savingLeaderboards }
            assertEquals(UiError.Kind.SaveSettings, failed.error?.kind)
            assertEquals(false, failed.showOnLeaderboards, "the saved choice")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun bracketBoardsWriteOnlyBracketsAndAFailureKeepsTheChoice() = runTest {
        fixture.privateDetails = """{"details":{"birthDate":"1984-03-02","weight":70000,"height":1700}}"""
        fixture.segmentPrefs = """{"matching":true,"updatedAt":"2026-10-15T12:00:00Z"}"""
        val repos = fixture.repos()
        presenterTestOf(
            {
                SegmentsAndGroupsPresenter(
                    FakeNavigator(SegmentsAndGroupsScreen),
                    repos.segmentPrefs,
                    repos.leaderboardBrackets,
                    repos.groupPrefs,
                )
            },
        ) {
            val off = awaitUntil { it.segmentMatching == true && it.bracketBoards != null }
            assertEquals(false, off.bracketBoards, "off by default")
            assertTrue(off.canUseBracketBoards)
            off.eventSink(SegmentsAndGroupsEvent.BracketBoardsChanged(true))
            val on = awaitUntil { it.bracketBoards == true && !it.savingBracketBoards }
            assertNull(on.error)
            val stored = AtJson.parseToJsonElement(fixture.leaderboardBrackets!!).jsonObject
            assertEquals("65-74", stored.getValue("weightClass").jsonPrimitive.content)
            assertFalse("1984" in stored.toString() || "70000" in stored.toString(), "brackets only")

            fixture.failWrites = true
            on.eventSink(SegmentsAndGroupsEvent.BracketBoardsChanged(false))
            val failed = awaitUntil { it.error != null && !it.savingBracketBoards }
            assertEquals(UiError.Kind.SaveSettings, failed.error?.kind)
            assertEquals(true, failed.bracketBoards, "the saved choice")

            fixture.failWrites = false
            failed.eventSink(SegmentsAndGroupsEvent.BracketBoardsChanged(false))
            awaitUntil { it.bracketBoards == false && !it.savingBracketBoards }
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(fixture.leaderboardBrackets, "off deletes the record")
    }

    @Test
    fun bracketBoardsArentOfferedWhileMatchingIsOff() = runTest {
        val repos = fixture.repos()
        presenterTestOf(
            {
                SegmentsAndGroupsPresenter(
                    FakeNavigator(SegmentsAndGroupsScreen),
                    repos.segmentPrefs,
                    repos.leaderboardBrackets,
                    repos.groupPrefs,
                )
            },
        ) {
            val loaded = awaitUntil { it.segmentMatching == false && it.bracketBoards != null }
            assertEquals(false, loaded.bracketBoards)
            // Ignored: the UI doesn't offer it, and nothing is saved.
            loaded.eventSink(SegmentsAndGroupsEvent.BracketBoardsChanged(true))
            assertFalse(loaded.savingBracketBoards)
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(fixture.leaderboardBrackets)
    }

    @Test
    fun anOldGrantCantUseBracketBoards() = runTest {
        fixture.scope =
            "atproto transition:generic space:${Nsid.SPACE_ATHLETE}?collection=${Nsid.ACTOR_SEGMENT_PREFS}" +
                "&collection=${Nsid.FEED_SEGMENT_TRACK}"
        fixture.segmentPrefs = """{"matching":true,"updatedAt":"2026-10-15T12:00:00Z"}"""
        val repos = fixture.repos()
        presenterTestOf(
            {
                SegmentsAndGroupsPresenter(
                    FakeNavigator(SegmentsAndGroupsScreen),
                    repos.segmentPrefs,
                    repos.leaderboardBrackets,
                    repos.groupPrefs,
                )
            },
        ) {
            val loaded = awaitUntil { it.segmentMatching == true }
            assertFalse(loaded.canUseBracketBoards)
            assertNull(loaded.bracketBoards)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedSegmentMatchingSaveKeepsItOffAndShowsAnError() = runTest {
        val repos = fixture.repos()
        presenterTestOf(
            {
                SegmentsAndGroupsPresenter(
                    FakeNavigator(SegmentsAndGroupsScreen),
                    repos.segmentPrefs,
                    repos.leaderboardBrackets,
                    repos.groupPrefs,
                )
            },
        ) {
            val loaded = awaitUntil { it.segmentMatching != null }
            fixture.failWrites = true
            loaded.eventSink(SegmentsAndGroupsEvent.SegmentMatchingChanged(true))
            val failed = awaitUntil { it.error != null && !it.savingSegments }
            assertEquals(UiError.Kind.SaveSettings, failed.error?.kind)
            assertEquals(false, failed.segmentMatching)
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(fixture.segmentPrefs)
        assertNull(repos.segmentPrefs.saved.value, "no sweep after a failed save")
    }

    @Test
    fun spacesThatRefuseTheSessionLeaveSegmentMatchingUnknownAndUnsaved() = runTest {
        fixture.scopeMissing = true
        val repos = fixture.repos()
        presenterTestOf(
            {
                SegmentsAndGroupsPresenter(
                    FakeNavigator(SegmentsAndGroupsScreen),
                    repos.segmentPrefs,
                    repos.leaderboardBrackets,
                    repos.groupPrefs,
                )
            },
        ) {
            val state = awaitItem()
            state.eventSink(SegmentsAndGroupsEvent.SegmentMatchingChanged(true))
            val after = awaitUntil { !it.savingSegments }
            assertNull(after.segmentMatching)
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(fixture.segmentPrefs)
    }

    @Test
    fun aSegmentMatchingToggleSentTwiceSavesOnce() = runTest {
        val repos = fixture.repos()
        presenterTestOf(
            {
                SegmentsAndGroupsPresenter(
                    FakeNavigator(SegmentsAndGroupsScreen),
                    repos.segmentPrefs,
                    repos.leaderboardBrackets,
                    repos.groupPrefs,
                )
            },
        ) {
            val loaded = awaitUntil { it.segmentMatching != null }
            loaded.eventSink(SegmentsAndGroupsEvent.SegmentMatchingChanged(true))
            loaded.eventSink(SegmentsAndGroupsEvent.SegmentMatchingChanged(true))
            awaitUntil { it.segmentMatching == true && !it.savingSegments }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, fixture.spacePuts.count { (_, record) -> "matching" in record })
    }

    @Test
    fun choosingGroupActivitiesSavesItInSelfAndAFailureKeepsTheEarlierChoice() = runTest {
        val repos = fixture.repos()
        presenterTestOf(
            {
                SegmentsAndGroupsPresenter(
                    FakeNavigator(SegmentsAndGroupsScreen),
                    repos.segmentPrefs,
                    repos.leaderboardBrackets,
                    repos.groupPrefs,
                )
            },
        ) {
            val loaded = awaitUntil { it.groupActivities != null }
            assertEquals(GroupChoice.NEVER_ASKED, loaded.groupActivities)

            loaded.eventSink(SegmentsAndGroupsEvent.GroupActivitiesChanged(GroupChoice.CONNECTED))
            loaded.eventSink(SegmentsAndGroupsEvent.GroupActivitiesChanged(GroupChoice.CONNECTED))
            val saved = awaitUntil { it.groupActivities == GroupChoice.CONNECTED && !it.savingGroups }
            assertNull(saved.error)
            assertEquals(
                "connected",
                AtJson.parseToJsonElement(assertNotNull(fixture.groupPrefs)).jsonObject["mode"]?.jsonPrimitive?.content,
            )
            assertNotNull(repos.groupPrefs.saved.value, "a save starts the sweep")

            fixture.failWrites = true
            saved.eventSink(SegmentsAndGroupsEvent.GroupActivitiesChanged(GroupChoice.ANYONE))
            val failed = awaitUntil { it.error != null && !it.savingGroups }
            assertEquals(UiError.Kind.SaveSettings, failed.error?.kind)
            assertEquals(GroupChoice.CONNECTED, failed.groupActivities)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anOldGrantCantUseGroupActivities() = runTest {
        fixture.scope =
            "atproto transition:generic space:${Nsid.SPACE_ATHLETE}?collection=${Nsid.ACTOR_SEGMENT_PREFS}" +
                "&collection=${Nsid.FEED_SEGMENT_TRACK}"
        val repos = fixture.repos()
        presenterTestOf(
            {
                SegmentsAndGroupsPresenter(
                    FakeNavigator(SegmentsAndGroupsScreen),
                    repos.segmentPrefs,
                    repos.leaderboardBrackets,
                    repos.groupPrefs,
                )
            },
        ) {
            val loaded = awaitUntil { it.segmentMatching != null }
            assertFalse(loaded.canUseGroups)
            loaded.eventSink(SegmentsAndGroupsEvent.GroupActivitiesChanged(GroupChoice.ANYONE))
            assertFalse(loaded.savingGroups)
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(fixture.groupPrefs)
    }
}
