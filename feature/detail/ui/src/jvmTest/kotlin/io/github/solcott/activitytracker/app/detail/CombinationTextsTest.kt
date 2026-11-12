package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.activity.DataKind
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.MemberView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf

/** The combination editor's words and events, and a combined card's caption, which both UIs ask (dev/shared-display-text). */
class CombinationTextsTest {
    private val edge =
        MemberView(
            AtUri("at://did:plc:a/app.activitytracker.feed.activity/1"),
            Instant.parse("2026-06-01T07:00:00Z"),
            3_600,
            "cycling",
            "Edge 840",
        )
    private val phone =
        MemberView(AtUri("at://did:plc:a/app.activitytracker.feed.activity/2"), Instant.parse("2026-06-01T08:00:00Z"), 600, "cycling")

    @Test
    fun aKindReadsFromItsSource() {
        val row = DataSourceRow(DataKind.Power, included = true, chosen = edge, offered = persistentListOf())
        assertEquals(AppText.named("chart_source_from", AppText.written("Edge 840")), CombinationTexts.from(edge))
        assertEquals(AppText.named("data_source_include", AppText.named("data_kind_power")), CombinationTexts.include(row))
        assertEquals(false, CombinationTexts.offersMenu(row))
        assertEquals(AppText.named("data_source_started", AppText.Time(phone.startedAt.epochSeconds)), CombinationTexts.sourceName(phone))
    }

    @Test
    fun choosingTheChosenMemberSendsNothing() {
        val row = DataSourceRow(DataKind.HeartRate, included = true, chosen = edge, offered = persistentListOf(edge, phone))
        assertTrue(CombinationTexts.offersMenu(row))
        assertNull(CombinationTexts.chooseEvent(row, edge))
        assertEquals(ActivityDetailEvent.ChooseSource(DataKind.HeartRate, phone.uri), CombinationTexts.chooseEvent(row, phone))
        assertEquals(ActivityDetailEvent.RemoveMember(edge.uri), CombinationTexts.removeEvent(edge))
    }

    @Test
    fun aCaptionNamesEachSourceInTurn() {
        val caption = RecordingTexts.sourceCaption(listOf(edge, phone))
        assertEquals(
            AppText.named(
                "chart_source_from",
                AppText.named("chart_source_then", AppText.written("Edge 840"), RecordingTextsSport.cycling),
            ),
            caption,
        )
        assertNull(RecordingTexts.sourceCaption(emptyList()))
    }

    @Test
    fun everyNameTheCombinationTextsChooseHasItsString() {
        val row = DataSourceRow(DataKind.Map, included = false, chosen = null, offered = persistentListOf())
        val texts =
            listOf(
                CombinationTexts.edit,
                CombinationTexts.editDescription,
                CombinationTexts.title,
                CombinationTexts.done,
                CombinationTexts.cancel,
                CombinationTexts.retry,
                CombinationTexts.members,
                CombinationTexts.removeMessage,
                CombinationTexts.dataSources,
                CombinationTexts.leftOut,
                CombinationTexts.remove(edge),
                CombinationTexts.comesFrom(row, edge),
                CombinationTexts.sourceName(phone),
            ) + DataKind.entries.map(CombinationTexts::kind)
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Named -> listOf(text.name) + text.args.flatMap(::names)
            else -> emptyList()
        }

    private object RecordingTextsSport {
        val cycling: AppText = RecordingTexts.memberName(MemberView(AtUri("at://x/y/z"), Instant.DISTANT_PAST, 0, "cycling"))
    }
}
