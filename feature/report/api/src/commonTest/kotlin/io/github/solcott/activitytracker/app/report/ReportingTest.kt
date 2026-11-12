package io.github.solcott.activitytracker.app.report

import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** What a comment's Report opens (dev/shared-display-text, "A comment's actions are decided once"). */
class ReportingTest {
    @Test
    fun aCommentsReportOpensTheSheetForThatCommentNamingItsAuthor() {
        val carol = Did("did:plc:carol")
        val uri = AtUri("at://did:plc:carol/app.activitytracker.feed.comment/3kc1")
        val comment =
            CommentView(uri, Cid("bafyc1"), ProfileViewBasic(carol, Handle("carol.test")), "Nice", Instant.parse("2026-11-05T10:00:00Z"))
        assertEquals(
            ReportingEvent.Open(ReportTarget.Comment(uri, Cid("bafyc1"), carol), "@carol.test", offerBlock = true),
            Reporting(available = true).reportComment(comment, "@carol.test"),
        )
    }
}
