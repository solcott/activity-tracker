package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestLogWriter
import co.touchlab.kermit.loggerConfigInit
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalKudos
import io.github.solcott.activitytracker.data.sport.RecentActivityTypes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ActivityViewerState
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ActivityRepositoryTest {
    private val did = Did("did:plc:alice")
    private var failWrites = false
    private val putBodies = mutableListOf<String>()
    private val deleteBodies = mutableListOf<String>()
    private val logs = TestLogWriter(Severity.Verbose)
    private val logger = Logger(loggerConfigInit(logs))

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                when (request.url.encodedPath) {
                    "/xrpc/com.atproto.repo.uploadBlob" -> {
                        respond(
                            """{"blob":{"${'$'}type":"blob","ref":{"${'$'}link":"bafyblob"},"mimeType":"application/gpx+xml","size":10}}""",
                            headers = json,
                        )
                    }

                    "/xrpc/com.atproto.repo.putRecord" -> {
                        if (failWrites) {
                            respond(
                                """{"error":"InternalServerError"}""",
                                HttpStatusCode.InternalServerError,
                                json,
                            )
                        } else {
                            putBodies += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                            respond("""{"uri":"at://$did/x/y","cid":"bafyrec"}""", headers = json)
                        }
                    }

                    "/xrpc/com.atproto.repo.deleteRecord" -> {
                        deleteBodies += (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                        respond("{}", headers = json)
                    }

                    else -> {
                        respond("", HttpStatusCode.NotFound)
                    }
                }
            }
        ) {
            expectSuccess = false
        }

    private suspend fun repository(): ActivityRepository {
        val (db, _, clients) = signedInTestSession(http, did = did, logger = logger)
        val outbox = OutboxProcessor(db, clients, logger)
        val localKudos = LocalKudos()
        val feed = FeedRepository(db, clients, localKudos, logger)
        return ActivityRepository(db, clients, outbox, feed, localKudos, RecentActivityTypes(db, clients, feed), Clock.System)
    }

    private fun activityBy(author: Did) =
        ActivityView(
            uri = AtUri.of(author, Nsid.FEED_ACTIVITY, "3kact1"),
            cid = Cid("bafyact"),
            author = ProfileViewBasic(author, Handle("someone.test")),
            record =
                ActivityRecord(
                    sport = "run",
                    startedAt = Instant.parse("2026-09-30T07:00:00Z"),
                    elapsedTime = 1800,
                    createdAt = Instant.parse("2026-09-30T07:31:00Z"),
                ),
            kudosCount = 0,
            commentCount = 0,
            indexedAt = Instant.parse("2026-09-30T07:32:00Z"),
            viewer = ActivityViewerState(),
        )

    @Test
    fun publishUploadsTrackThenWritesRecordAndRetriesAfterFailure() = runTest {
        val repo = repository()

        val draft =
            ActivityDraft(
                type = ActivityType(Sport.RUN, SubSport.TRAIL),
                title = "Morning run",
                description = null,
                startedAt = Instant.parse("2026-09-30T07:00:00Z"),
                elapsed = 30.minutes,
                distanceMeters = 5012.4,
                gpx = "<gpx/>".encodeToByteArray(),
            )

        failWrites = true
        val uri = repo.publish(draft)
        assertEquals(1, repo.pendingWrites.first())
        logs.assertCount(1)
        logs.assertLast { severity == Severity.Warn && tag == "OutboxProcessor" && "1 pending" in message }

        failWrites = false
        assertEquals(0, repo.sync())
        assertEquals(0, repo.pendingWrites.first())

        val put = AtJson.parseToJsonElement(putBodies.single()).jsonObject
        assertEquals(uri.rkey, put["rkey"]?.jsonPrimitive?.content)
        val record = put["record"]!!.jsonObject
        assertEquals(Nsid.FEED_ACTIVITY, record["\$type"]?.jsonPrimitive?.content)
        assertEquals("5012", record["distance"]?.jsonPrimitive?.content)
        assertEquals(SubSport.TRAIL, record["subSport"]?.jsonPrimitive?.content)
        assertTrue("bafyblob" in record["track"].toString())
    }

    @Test
    fun rapidKudosTapsOnAStaleSnapshotNeverWriteTwoKudos() = runTest {
        val repo = repository()
        // Every tap carries the same snapshot, taken before the first kudos landed.
        val stale = activityBy(Did("did:plc:bob"))

        listOf(launch { repo.toggleKudos(stale) }, launch { repo.toggleKudos(stale) }).joinAll()
        repo.toggleKudos(stale)

        assertEquals(2, putBodies.size)
        assertEquals(1, deleteBodies.size)
        val rkeys = putBodies.map { AtJson.parseToJsonElement(it).jsonObject["rkey"]?.jsonPrimitive?.content }
        assertEquals(rkeys.first(), AtJson.parseToJsonElement(deleteBodies.single()).jsonObject["rkey"]?.jsonPrimitive?.content)
        assertTrue(putBodies.all { Nsid.FEED_KUDOS in it })
    }

    @Test
    fun ownActivitiesCannotGetKudos() = runTest {
        val repo = repository()

        repo.toggleKudos(activityBy(did))

        assertTrue(putBodies.isEmpty())
        assertEquals(0, repo.pendingWrites.first())
    }
}
