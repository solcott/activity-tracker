package io.github.solcott.activitytracker.app

import co.touchlab.kermit.Logger
import com.slack.circuit.test.CircuitReceiveTurbine
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.segment.TrailPhotosScreen
import io.github.solcott.activitytracker.app.segment.TrailReportsEvent
import io.github.solcott.activitytracker.app.segment.TrailReportsPresenter
import io.github.solcott.activitytracker.app.segment.TrailReportsScreen
import io.github.solcott.activitytracker.app.segment.TrailReportsState
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.moderation.ReportRepository
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.data.segments.TrailReports
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityMediaView
import io.github.solcott.activitytracker.lexicon.app.GetTrailReports
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.TrailCondition
import io.github.solcott.activitytracker.lexicon.app.TrailReportView
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.runTest

/** A segment's report list (segments/trail-reports): newest first in two sections, paged, the viewer's own deleted, others' reported. */
class TrailReportsPresenterTest {
    private val alice = Did("did:plc:alice")
    private val bob = Did("did:plc:bob")
    private val carol = Did("did:plc:carol")
    private val segment = AtUri.of(Did("did:plc:importer"), Nsid.FEED_SEGMENT, "anns")
    private val now = Clock.System.now()

    private fun report(author: Did, rkey: String, age: Duration, photos: Int = 0, tags: List<String> = listOf(TrailCondition.MUDDY)) =
        TrailReportView(
            uri = AtUri.of(author, Nsid.FEED_TRAIL_REPORT, rkey),
            cid = Cid("bafy$rkey"),
            segment = segment,
            author = ProfileViewBasic(author, Handle("${author.value.substringAfterLast(':')}.test")),
            conditions = tags,
            images = (1..photos).map { ActivityMediaView.Image("https://cdn.test/$rkey/$it.webp") },
            indexedAt = now - age,
            current = age < 14.days,
        )

    private val alices = report(alice, "a1", 2.hours, photos = 2)
    private val bobs = report(bob, "b1", 1.days, photos = 1)
    private val carols = report(carol, "c1", 20.days, photos = 2)
    private val older = report(bob, "b0", 30.days)

    /** The pages the AppView answers, by cursor (null: the first). */
    private val pages =
        mapOf(
            null to GetTrailReports.Output(listOf(alices, bobs, carols), cursor = "p2"),
            "p2" to GetTrailReports.Output(listOf(older)),
        )

    /** The paths of the writes sent to the PDS. */
    private val writes: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                val method = request.url.encodedPath.removePrefix("/xrpc/")
                when {
                    method == "com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    method == Nsid.FEED_GET_TRAIL_REPORTS -> {
                        val page = pages.getValue(request.url.parameters["cursor"])
                        respond(AtJson.encodeToString(GetTrailReports.Output.serializer(), page), headers = json)
                    }
                    method.startsWith("com.atproto.repo.") -> {
                        writes += method
                        respond("""{"uri":"${alices.uri.value}","cid":"bafyrec"}""", headers = json)
                    }
                    else -> respond("", HttpStatusCode.NotFound)
                }
            },
        ) {
            expectSuccess = false
        }

    private val screen = TrailReportsScreen(segment.value, "Ann's")

    private suspend fun test(
        navigator: FakeNavigator = FakeNavigator(screen),
        block: suspend CircuitReceiveTurbine<TrailReportsState>.(TrailReports) -> Unit,
    ) {
        val (db, _, clients) = signedInTestSession(http, did = alice)
        val feed = FeedRepository(db, clients, LocalLikes(), Logger)
        val writer = OutboxWriter(db, clients, Clock.System)
        val outbox = OutboxProcessor(db, clients, Logger)
        val reports = TrailReports(clients, writer, outbox, Clock.System, feed)
        val blocks = BlockRepository(clients, writer, outbox, feed, Clock.System)
        val segments = SegmentRepository(clients)
        presenterTestOf({ TrailReportsPresenter(screen, navigator, reports, segments, ReportRepository(clients), blocks, Clock.System) }) {
            block(reports)
        }
    }

    @Test
    fun listsNewestFirstUnderLast14DaysThenOlderAndMarksTheViewersOwn() = runTest {
        test { _ ->
            val loaded = awaitUntil { it.reports.items.isNotEmpty() }
            val rows = loaded.reports.items
            assertEquals(listOf(alices, bobs, carols).map { it.uri.value }, rows.map { it.uri })
            assertEquals(listOf(true, true, false), rows.map { it.current })
            assertEquals(listOf(true, false, true), rows.indices.map { loaded.heading(it) != null })
            assertEquals(AppText.named("trail_reports_recent"), loaded.heading(0))
            assertNull(loaded.heading(1))
            assertEquals(AppText.named("trail_reports_older"), loaded.heading(2))
            assertEquals(listOf(true, false, false), rows.map { it.own })
            assertEquals("Ann's", loaded.name)
            assertTrue(loaded.reports.canLoadMore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun loadMoreAppendsTheNextPageByCursor() = runTest {
        test { _ ->
            awaitUntil { it.reports.items.isNotEmpty() }.eventSink(TrailReportsEvent.LoadMore)
            val all = awaitUntil { it.reports.items.size == 4 && it.reports.endReached }
            assertEquals(listOf(alices, bobs, carols, older).map { it.uri.value }, all.reports.items.map { it.uri })
            assertNull(all.heading(3), "the next page's older report continues the Older section")
            assertFalse(all.reports.canLoadMore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deletingTheViewersOwnReportRemovesItAtOnce() = runTest {
        test { reports ->
            awaitUntil { it.reports.items.isNotEmpty() }.eventSink(TrailReportsEvent.Delete(alices.uri.value))
            val after = awaitUntil { it.reports.items.size == 2 }
            assertEquals(listOf(bobs, carols).map { it.uri.value }, after.reports.items.map { it.uri })
            assertNotNull(after.heading(0), "the next report leads the Last 14 days section")
            assertEquals(setOf(alices.uri.value), reports.changes.value[segment.value]?.deleted)
            assertNull(after.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteIsRefusedForSomeoneElsesReportAndReportIsRefusedForTheViewersOwn() = runTest {
        test { reports ->
            val loaded = awaitUntil { it.reports.items.isNotEmpty() }
            loaded.eventSink(TrailReportsEvent.Delete(bobs.uri.value))
            loaded.eventSink(TrailReportsEvent.Report(alices.uri.value))
            // A later event that does change the state, so the refused ones had their turn.
            loaded.eventSink(TrailReportsEvent.Report(bobs.uri.value))
            val open = awaitUntil { it.reporting.sheet != null }
            assertEquals(3, open.reports.items.size)
            assertTrue(reports.changes.value.isEmpty())
            assertEquals(bob, open.reporting.sheet?.target?.author)
            assertTrue(writes.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun reportOnAnothersReportOpensTheSheetForThatReport() = runTest {
        test { _ ->
            awaitUntil { it.reports.items.isNotEmpty() }.eventSink(TrailReportsEvent.Report(bobs.uri.value))
            val sheet = awaitUntil { it.reporting.sheet != null }.reporting.sheet!!
            assertEquals(ReportTarget.TrailReport(bobs.uri, bobs.cid, bob), sheet.target)
            assertEquals("bob.test", sheet.authorName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun openPhotoOpensTheViewerAtThatPhotoAcrossTheList() = runTest {
        val navigator = FakeNavigator(screen)
        test(navigator) { _ ->
            val loaded = awaitUntil { it.reports.items.isNotEmpty() }
            // Alice's two and Bob's one come before Carol's second.
            loaded.eventSink(TrailReportsEvent.OpenPhoto(carols.uri.value, 2))
            assertEquals(TrailPhotosScreen(segment = segment.value, start = 4), navigator.awaitNextScreen())
            loaded.eventSink(TrailReportsEvent.OpenPhoto(alices.uri.value, 1))
            assertEquals(TrailPhotosScreen(segment = segment.value, start = 0), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
