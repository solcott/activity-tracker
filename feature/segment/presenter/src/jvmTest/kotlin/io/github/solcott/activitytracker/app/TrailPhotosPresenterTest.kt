package io.github.solcott.activitytracker.app

import co.touchlab.kermit.Logger
import com.slack.circuit.test.CircuitReceiveTurbine
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.segment.TrailPhotosEvent
import io.github.solcott.activitytracker.app.segment.TrailPhotosPresenter
import io.github.solcott.activitytracker.app.segment.TrailPhotosScreen
import io.github.solcott.activitytracker.app.segment.TrailPhotosState
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.moderation.ReportRepository
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.data.segments.TrailReports
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityMediaView
import io.github.solcott.activitytracker.lexicon.app.GetTrailPhotos
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.TrailCondition
import io.github.solcott.activitytracker.lexicon.app.TrailPhotoView
import io.github.solcott.activitytracker.lexicon.app.TrailReportRecord
import io.github.solcott.activitytracker.lexicon.atproto.GetRecord
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject

/** Trail photos full screen (segments/trail-reports): paged as the viewer swipes, others' reported, the viewer's own removed at once. */
class TrailPhotosPresenterTest {
    private val alice = Did("did:plc:alice")
    private val bob = Did("did:plc:bob")
    private val segment = AtUri.of(Did("did:plc:importer"), Nsid.FEED_SEGMENT, "anns")
    private val now = Clock.System.now()

    private fun photo(author: Did, rkey: String, position: Int) =
        TrailPhotoView(
            image = ActivityMediaView.Image("https://cdn.test/$rkey/$position.webp"),
            report = AtUri.of(author, Nsid.FEED_TRAIL_REPORT, rkey),
            cid = Cid("bafy$rkey"),
            position = position,
            segment = segment,
            author = ProfileViewBasic(author, Handle("${author.value.substringAfterLast(':')}.test")),
            conditions = listOf(TrailCondition.MUDDY),
            indexedAt = now - position.hours,
        )

    private val alices = listOf(photo(alice, "a1", 1), photo(alice, "a1", 2))
    private val bobs = listOf(photo(bob, "b1", 1), photo(bob, "b1", 2), photo(bob, "b1", 3))
    private val later = listOf(photo(bob, "b0", 1), photo(bob, "b0", 2))

    /** The pages the AppView answers, by cursor (null: the first). */
    private val pages = mapOf(null to GetTrailPhotos.Output(alices + bobs, cursor = "p2"), "p2" to GetTrailPhotos.Output(later))

    /** The cursors the viewer asked for photos with. */
    private val asked: MutableList<String?> = Collections.synchronizedList(mutableListOf())

    /** Alice's report as her PDS holds it: tagged, so removing a photo rewrites it rather than deleting it. */
    private val alicesRecord =
        TrailReportRecord(StrongRef(segment, Cid("bafyanns")), listOf(TrailCondition.MUDDY), createdAt = now - 1.hours)

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                val method = request.url.encodedPath.removePrefix("/xrpc/")
                when (method) {
                    "com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
                    Nsid.FEED_GET_TRAIL_PHOTOS -> {
                        val cursor = request.url.parameters["cursor"]
                        asked += cursor
                        respond(AtJson.encodeToString(GetTrailPhotos.Output.serializer(), pages.getValue(cursor)), headers = json)
                    }
                    "com.atproto.repo.getRecord" -> {
                        val value = AtJson.encodeToJsonElement(TrailReportRecord.serializer(), alicesRecord).jsonObject
                        val output = GetRecord.Output(alices[0].report, Cid("bafya1"), value)
                        respond(AtJson.encodeToString(GetRecord.Output.serializer(), output), headers = json)
                    }
                    else ->
                        if (method.startsWith("com.atproto.repo.")) {
                            respond("""{"uri":"${alices[0].report.value}","cid":"bafyrec"}""", headers = json)
                        } else {
                            respond("", HttpStatusCode.NotFound)
                        }
                }
            },
        ) {
            expectSuccess = false
        }

    private suspend fun test(screen: TrailPhotosScreen, block: suspend CircuitReceiveTurbine<TrailPhotosState>.() -> Unit) {
        val (db, _, clients) = signedInTestSession(http, did = alice)
        val feed = FeedRepository(db, clients, LocalLikes(), Logger)
        val writer = OutboxWriter(db, clients, Clock.System)
        val outbox = OutboxProcessor(db, clients, Logger)
        val reports = TrailReports(clients, writer, outbox, Clock.System, feed)
        val blocks = BlockRepository(clients, writer, outbox, feed, Clock.System)
        presenterTestOf(
            { TrailPhotosPresenter(screen, FakeNavigator(screen), reports, ReportRepository(clients), blocks, Clock.System) },
            block = block,
        )
    }

    @Test
    fun showsNOfTotalOnceEveryPageHasLoadedAndShowNearTheEndLoadsTheNext() = runTest {
        test(TrailPhotosScreen(segment = segment.value, start = 1)) {
            val first = awaitUntil { it.content.data != null }
            assertEquals(1, first.index)
            assertEquals(5, first.pages.size)
            assertNull(first.total, "the total isn't known while another page may follow")
            assertEquals(listOf(true, true, false, false, false), first.pages.map { it.own })
            // Far from the end: nothing more is asked for.
            first.eventSink(TrailPhotosEvent.Show(1))
            // Within three of the last read: the next page.
            first.eventSink(TrailPhotosEvent.Show(2))
            val all = awaitUntil { it.total != null }
            assertEquals(2, all.index)
            assertEquals(7, all.total)
            assertEquals(7, all.pages.size)
            assertEquals(AppText.named("trail_photo_position", AppText.whole(3), AppText.whole(7)), all.position)
            assertEquals(listOf(null, "p2"), asked.toList())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun reportOpensTheSheetForThePhotoAndItsPosition() = runTest {
        test(TrailPhotosScreen(segment = segment.value, start = 3)) {
            val loaded = awaitUntil { it.content.data != null }
            assertEquals(3, loaded.index)
            loaded.eventSink(TrailPhotosEvent.Report)
            val sheet = awaitUntil { it.reporting.sheet != null }.reporting.sheet!!
            val shown = bobs[1]
            assertEquals(ReportTarget.TrailPhoto(ReportTarget.TrailReport(shown.report, shown.cid, bob), 2), sheet.target)
            assertEquals("bob.test", sheet.authorName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun removingTheViewersOwnPhotoLeavesItOutAtOnce() = runTest {
        test(TrailPhotosScreen(segment = segment.value, start = 1)) {
            val loaded = awaitUntil { it.content.data != null }
            assertTrue(loaded.pages[1].own)
            loaded.eventSink(TrailPhotosEvent.Remove)
            val after = awaitUntil { it.pages.size == 4 }
            assertEquals((listOf(alices[0]) + bobs).map { it.image.fullsize }, after.pages.map { it.url })
            assertNull(after.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun removeIsRefusedForSomeoneElsesPhoto() = runTest {
        test(TrailPhotosScreen(segment = segment.value, start = 2)) {
            val loaded = awaitUntil { it.content.data != null }
            loaded.eventSink(TrailPhotosEvent.Remove)
            // A later event that does change the state, so the refused one had its turn.
            loaded.eventSink(TrailPhotosEvent.Report)
            val open = awaitUntil { it.reporting.sheet != null }
            assertEquals(5, open.pages.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
