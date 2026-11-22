package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.fixtures.Fixed
import io.github.solcott.activitytracker.app.fixtures.fakeLeaderboardEntry
import io.github.solcott.activitytracker.app.fixtures.fakeProfileBasic
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.GetPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What a Trail row's leader column and the viewer's line say (segments/path-page, dev/shared-display-text). */
class PathLeaderTextsTest {
    private val kate = fakeProfileBasic(Fixed.CAROL, displayName = "Kate")
    private val leader =
        GetPath.SegmentLeader(
            AtUri("at://did:plc:importer/app.activitytracker.feed.segment/3ka"),
            "ride",
            fakeLeaderboardEntry(1, kate, 768),
        )

    @Test
    fun theColumnShowsTheFastestTimeAndWhoHoldsIt() {
        assertEquals(AppText.written("12:48"), PathTexts.leaderTime(leader))
        assertEquals(AppText.written("Kate"), PathTexts.leaderName(leader))
        assertEquals(
            AppText.written(fakeProfileBasic(Fixed.CAROL).handle.value),
            PathTexts.leaderName(leader.copy(leader = fakeLeaderboardEntry(1, fakeProfileBasic(Fixed.CAROL, displayName = null), 768))),
            "the handle when they have no display name",
        )
    }

    @Test
    fun theViewersLineReadsAsTheLeaderboardCard() {
        assertNull(PathTexts.yours(leader), "not on the board")
        val ranked = leader.copy(viewer = fakeLeaderboardEntry(12, fakeProfileBasic(), 842))
        assertEquals(
            AppText.named("segment_leaderboard_yours", AppText.written("14:02"), AppText.written("12")),
            PathTexts.yours(ranked),
        )
    }
}
