package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.segments.ReportFacts
import io.github.solcott.activitytracker.appview.segments.summarizeConditions
import io.github.solcott.activitytracker.lexicon.app.TrailConditionTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** The conditions summary (segments/trail-reports): the 14-day window, one report per rider, and how the lead is picked. */
class TrailConditionsTest {
    private val now = 1_800_000_000_000L
    private var n = 0

    private fun report(author: String, ago: Duration, vararg tags: String, note: String? = null) =
        ReportFacts("at://$author/r/${n++}", author, now - ago.inWholeMilliseconds, tags.toList(), note)

    @Test
    fun muddyWins() {
        val summary =
            summarizeConditions(
                listOf(
                    report("a", 2.days, "muddy", "downedTree", note = "Lower half is soup"),
                    report("b", 3.days, "muddy", "downedTree"),
                    report("c", 4.days, "muddy"),
                    report("d", 5.days, "wet"),
                ),
                now,
            )
        assertEquals("muddy", summary.lead)
        assertEquals(3, summary.leadRiders)
        assertEquals(4, summary.riders)
        assertEquals(listOf(TrailConditionTag("downedTree", 2), TrailConditionTag("wet", 1)), summary.tags)
        assertEquals(now - 2.days.inWholeMilliseconds, summary.latestAt)
        assertEquals("at://a/r/0", summary.latestNote)
    }

    @Test
    fun closedByTheNewestReport() {
        val summary =
            summarizeConditions(listOf(report("a", 6.days, "muddy"), report("b", 7.days, "muddy"), report("c", 1.hours, "closed")), now)
        assertEquals("closed", summary.lead)
        assertEquals(1, summary.leadRiders)
    }

    @Test
    fun reopened() {
        val summary = summarizeConditions(listOf(report("c", 2.days, "closed"), report("d", 1.days, "dry")), now)
        assertEquals("dry", summary.lead)
        assertEquals(listOf(TrailConditionTag("closed", 1)), summary.tags)
    }

    @Test
    fun onlyTrailTags() {
        val summary = summarizeConditions(listOf(report("a", 1.days, "downedTree")), now)
        assertNull(summary.lead)
        assertEquals(listOf(TrailConditionTag("downedTree", 1)), summary.tags)
    }

    @Test
    fun oldReport() {
        val summary = summarizeConditions(listOf(report("a", 15.days, "dry")), now)
        assertNull(summary.lead)
        assertEquals(0, summary.riders)
        assertEquals(now - 15.days.inWholeMilliseconds, summary.lastReportAt)
        assertEquals(listOf("dry"), summary.lastReportTags)
    }

    @Test
    fun riderChangesTheirMind() {
        val summary = summarizeConditions(listOf(report("a", 3.days, "muddy"), report("a", 1.days, "dry")), now)
        assertEquals("dry", summary.lead)
        assertEquals(1, summary.riders)
        assertEquals(emptyList(), summary.tags)
    }

    @Test
    fun photoOnlyAfterATaggedReport() {
        val summary = summarizeConditions(listOf(report("a", 3.days, "muddy"), report("a", 1.days)), now)
        assertEquals("muddy", summary.lead)
    }

    @Test
    fun tieGoesToTheNewest() {
        val summary = summarizeConditions(listOf(report("a", 3.days, "wet"), report("b", 1.days, "dry")), now)
        assertEquals("dry", summary.lead)
    }

    @Test
    fun mutedRiderCountsButGivesNoNote() {
        val summary = summarizeConditions(listOf(report("a", 1.days, "muddy", note = "Soup")), now, unlisted = setOf("a"))
        assertEquals("muddy", summary.lead)
        assertNull(summary.latestNote)
    }
}
