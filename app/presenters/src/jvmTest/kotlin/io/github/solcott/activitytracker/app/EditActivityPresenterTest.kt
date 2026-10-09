package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import app.cash.turbine.ReceiveTurbine
import co.touchlab.kermit.Logger
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.edit.DetailsEvent
import io.github.solcott.activitytracker.app.edit.EditActivityEvent
import io.github.solcott.activitytracker.app.edit.EditActivityPresenter
import io.github.solcott.activitytracker.app.edit.EditActivityScreen
import io.github.solcott.activitytracker.app.edit.EditActivityState
import io.github.solcott.activitytracker.app.edit.EditMedia
import io.github.solcott.activitytracker.app.edit.PickedPhoto
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.PdsActivityStreamsStore
import io.github.solcott.activitytracker.data.activity.cid
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalKudos
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.jsonObject

class EditActivityPresenterTest {
    private val alice = Did("did:plc:alice")
    private val uri = "at://${alice.value}/${Nsid.FEED_ACTIVITY}/3kdive"

    private val record =
        """{"sport":"dive","startedAt":"2026-09-30T10:00:00Z","elapsedTime":1800,"createdAt":"2026-09-30T10:31:00Z",""" +
            """"title":"Blue Hole","description":"Sharks","dive":{"maxDepth":3000,"site":"Dahab"},""" +
            """"track":{"${'$'}type":"blob","ref":{"${'$'}link":"bafytrack"},"mimeType":"application/gpx+xml","size":10},""" +
            """"media":[${photo("bafkone")},${photo("bafktwo")}]}"""

    private fun photo(cid: String) =
        """{"${'$'}type":"app.activitytracker.feed.activity#image","image":{"${'$'}type":"blob","ref":{"${'$'}link":"$cid"},""" +
            """"mimeType":"image/webp","size":10},"aspectRatio":{"width":4,"height":3}}"""

    private val view =
        """{"uri":"$uri","cid":"bafyact","author":{"did":"${alice.value}","handle":"alice.test"},"record":$record,""" +
            """"kudosCount":0,"commentCount":0,"indexedAt":"2026-09-30T10:31:01Z","viewer":{},"media":[""" +
            listOf("bafkone", "bafktwo").joinToString(",") {
                """{"${'$'}type":"app.activitytracker.feed.defs#imageView","fullsize":"https://appview/img/media/${alice.value}/$it"}"""
            } +
            "]}"

    /** Every upload and write the PDS took, in order. */
    private val writes = mutableListOf<String>()
    private val putRecords = mutableListOf<ActivityRecord>()
    private var failUpload = false

    /** Holds each `putRecord` until completed, so the test sees the save in flight. */
    private var gate = CompletableDeferred(Unit)

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)

                    "/xrpc/${Nsid.FEED_GET_ACTIVITY}" -> respond("""{"activity":$view,"comments":[]}""", headers = json)

                    "/xrpc/com.atproto.repo.getRecord" -> respond("""{"uri":"$uri","cid":"bafyact","value":$record}""", headers = json)

                    "/xrpc/com.atproto.repo.uploadBlob" ->
                        if (failUpload) {
                            respond("""{"error":"PayloadTooLarge"}""", HttpStatusCode.PayloadTooLarge, json)
                        } else {
                            writes += "upload"
                            respond(
                                """{"blob":{"${'$'}type":"blob","ref":{"${'$'}link":"bafknew"},"mimeType":"image/webp","size":3}}""",
                                headers = json,
                            )
                        }

                    "/xrpc/com.atproto.repo.putRecord" -> {
                        gate.await()
                        val body = AtJson.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
                        putRecords += AtJson.decodeFromJsonElement(ActivityRecord.serializer(), body.jsonObject.getValue("record"))
                        writes += "put"
                        respond("""{"uri":"$uri","cid":"bafyrec"}""", headers = json)
                    }

                    else -> respond("""{"error":"NotFound"}""", HttpStatusCode.NotFound, json)
                }
            }
        ) {
            expectSuccess = false
        }

    private val picked = PickedPhoto(ByteString(1, 2, 3), "image/webp", width = 3, height = 4)

    private suspend fun presenter(navigator: FakeNavigator): @Composable () -> EditActivityState {
        val (db, _, clients) = signedInTestSession(http, did = alice)
        val feed = FeedRepository(db, clients, LocalKudos(), Logger)
        val outbox = OutboxProcessor(db, clients, Logger)
        val writer = OutboxWriter(db, clients, Clock.System)
        val manager = ActivityManager(db, clients, outbox, writer, PdsActivityStreamsStore(writer, clients, Clock.System), feed)
        return { EditActivityPresenter(EditActivityScreen(uri), navigator, feed, manager) }
    }

    private suspend fun ReceiveTurbine<EditActivityState>.awaitLoaded() = awaitUntil { it.details != null }

    @Test
    fun seedsTheFieldsAndSavesWhatChangedKeepingTheRest() = runTest {
        gate = CompletableDeferred()
        val navigator = FakeNavigator(EditActivityScreen(uri))
        presenterTestOf(presenter(navigator)) {
            val loaded = awaitLoaded()
            val details = assertNotNull(loaded.details)
            assertEquals("Blue Hole", loaded.title.text.toString())
            assertEquals("Sharks", details.description.text.toString())
            assertEquals("Dahab", details.diveSite?.text.toString())
            assertEquals(listOf("bafkone", "bafktwo"), details.media.map { (it as EditMedia.Existing).cid })

            details.eventSink(DetailsEvent.RemoveMedia(0))
            details.eventSink(DetailsEvent.PhotosPicked(persistentListOf(picked)))
            details.description.setTextAndPlaceCursorAtEnd("Sharks and turtles")
            val edited = awaitUntil { it.details?.media?.lastOrNull() is EditMedia.Picked }
            edited.eventSink(EditActivityEvent.Save)
            awaitUntil { it.saving }
            edited.eventSink(EditActivityEvent.Save)

            gate.complete(Unit)
            navigator.awaitPop()
            assertEquals(listOf("upload", "put"), writes, "a second tap while saving does nothing")
            val saved = putRecords.single()
            assertEquals("Sharks and turtles", saved.description)
            assertEquals("Dahab", saved.dive?.site)
            assertEquals(3000, saved.dive?.maxDepth)
            assertEquals("bafytrack", saved.track?.ref?.link)
            assertEquals(listOf("bafktwo", "bafknew"), saved.media.orEmpty().map { it.cid })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedUploadKeepsTheScreenOpenWithTheError() = runTest {
        failUpload = true
        val navigator = FakeNavigator(EditActivityScreen(uri))
        presenterTestOf(presenter(navigator)) {
            val details = assertNotNull(awaitLoaded().details)
            details.eventSink(DetailsEvent.PhotosPicked(persistentListOf(picked)))
            awaitUntil { it.details?.media?.size == 3 }.eventSink(EditActivityEvent.Save)
            val failed = awaitUntil { it.error != null && !it.saving }
            assertEquals(UiError.Kind.SaveActivity, failed.error?.kind)
            assertTrue(putRecords.isEmpty())
            navigator.assertPopIsEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun backAsksBeforeDroppingChanges() = runTest {
        val navigator = FakeNavigator(EditActivityScreen(uri))
        presenterTestOf(presenter(navigator)) {
            val loaded = awaitLoaded()
            loaded.title.setTextAndPlaceCursorAtEnd("Renamed")
            loaded.eventSink(EditActivityEvent.Back)
            val asking = awaitUntil { it.confirmDiscard }
            navigator.assertPopIsEmpty()
            asking.eventSink(EditActivityEvent.Discard)
            assertIs<FakeNavigator.PopEvent>(navigator.awaitPop())
            assertNull(putRecords.firstOrNull())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
