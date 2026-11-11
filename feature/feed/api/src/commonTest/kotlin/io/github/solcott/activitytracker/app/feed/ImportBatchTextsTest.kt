package io.github.solcott.activitytracker.app.feed

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/** The batch page's months (feed/import-cards, dev/shared-display-text). */
class ImportBatchTextsTest {
    private fun activity(rkey: String, startedAt: String) =
        ActivityView(
            uri = AtUri("at://did:plc:bob/app.activitytracker.feed.activity/$rkey"),
            cid = Cid("bafyrei$rkey"),
            author = ProfileViewBasic(Did("did:plc:bob"), Handle("bob.test")),
            record =
                ActivityRecord(
                    sport = "ride",
                    startedAt = Instant.parse(startedAt),
                    elapsedTime = 60,
                    createdAt = Instant.parse(startedAt),
                ),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Instant.parse(startedAt),
        )

    @Test
    fun monthsInTheViewersZoneNewestFirst() {
        val nov = activity("a", "2025-11-05T12:00:00Z")
        // 03:00 UTC on 1 November is still 31 October in Denver.
        val lateOct = activity("b", "2025-11-01T03:00:00Z")
        val months = ImportBatchTexts.months(listOf(nov, lateOct), TimeZone.of("America/Denver"))
        assertEquals(listOf("month-2025-11", "month-2025-10"), months.map { it.key })
        assertEquals(AppText.named("import_batch_month", AppText.named("month_short_11"), AppText.written("2025")), months[0].heading)
        assertEquals(listOf(lateOct), months[1].activities)
    }
}
