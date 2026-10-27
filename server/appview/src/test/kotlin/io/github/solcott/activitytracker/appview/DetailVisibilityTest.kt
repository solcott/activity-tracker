package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.StreamSource
import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.ActivityDetails
import io.github.solcott.activitytracker.appview.db.ActivityStreams
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.HandleLookup
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.XrpcQuery
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivitiesByStart
import io.github.solcott.activitytracker.lexicon.app.Lap
import io.github.solcott.activitytracker.lexicon.app.StreamChannel
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.effectiveRecord
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * Per-category detail visibility (`feed/detail-visibility`): details records and per-tier streams, each served only to viewers admitted to
 * its tier, merged with the activity's own.
 */
class DetailVisibilityTest {
    private val service = "did:web:localhost%3A8081"
    private val alice = "did:plc:alice"
    private val bob = "did:plc:bob"
    private val carol = "did:plc:carol"
    private val dave = "did:plc:dave"
    private val keys = listOf(alice, bob, carol, dave).associateWith { TestAccountKey() }

    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val indexer = Indexer(db, HandleLookup { did -> did.value.substringAfterLast(':') + ".test" })
    private val config = AppViewConfig.fromEnv(emptyMap())
    private val verifier = ServiceAuthVerifier(service, keys = { keys[it.value]?.multibase })

    private val time = listOf(0, 1, 2)
    private val blobs =
        mapOf(
                "bafkpublic" to
                    ActivityStreamData(
                        time = time,
                        speed = listOf(5, 6, 7),
                        altitude = listOf(100, 101, 102),
                        laps = listOf(Lap(start = 0, elapsed = 3, distance = 20, avgSpeed = 6)),
                    ),
                "bafkfollowers" to
                    ActivityStreamData(
                        time = time,
                        power = listOf(200, 210, 220),
                        laps = listOf(Lap(start = 0, elapsed = 3, avgPower = 210)),
                    ),
                "bafkself" to
                    ActivityStreamData(
                        time = time,
                        heartRate = listOf(120, 130, 140),
                        laps = listOf(Lap(start = 0, elapsed = 3, avgHeartRate = 130)),
                    ),
                "bafkold" to ActivityStreamData(time = time, heartRate = listOf(120, 130, 140), power = listOf(200, 210, 220)),
            )
            .mapValues { (_, d) -> AtJson.encodeToString(ActivityStreamData.serializer(), d).encodeToByteArray() }
    private val source = StreamSource { _, cid, _ -> nameOf(blobs, cid)?.let(blobs::get) }

    private val rkey = "3kride"
    private val activityUri = "at://$alice/${Nsid.FEED_ACTIVITY}/$rkey"

    private fun space(tier: String) = SpaceUri.of(Did(alice), Nsid.SPACE_ATHLETE, tier)

    private fun commit(did: String, collection: String, rkey: String, record: String?) = runBlocking {
        indexer.handle(
            JetstreamEvent.Commit(
                did = did,
                time = "2026-10-14T12:00:00.000000Z",
                operation = if (record == null) "delete" else "create",
                collection = collection,
                rkey = rkey,
                record = record?.let { AtJson.parseToJsonElement(it).jsonObject },
                cid = "bafy$rkey",
            )
        )
    }

    /** Alice's [collection] record [json] at [tier]: in her repo when public, in her tier space otherwise. */
    private fun put(collection: String, tier: String, json: String, key: String = rkey) {
        if (tier == Visibility.PUBLIC) commit(alice, collection, key, json)
        else runBlocking { indexer.spaceWrite(space(tier), collection, key, "bafy$tier$key", AtJson.parseToJsonElement(json).jsonObject) }
    }

    private fun remove(collection: String, tier: String) {
        if (tier == Visibility.PUBLIC) commit(alice, collection, rkey, null)
        else runBlocking { indexer.spaceDelete(space(tier), collection, rkey) }
    }

    private fun follow(author: String, subject: String) =
        commit(
            author,
            Nsid.GRAPH_FOLLOW,
            "3kf${author.last()}${subject.last()}",
            """{"subject":"$subject","createdAt":"2026-10-01T09:00:00Z"}""",
        )

    private fun activity(tier: String = Visibility.PUBLIC, startedAt: String = "2026-10-05T00:00:00Z", key: String = rkey) =
        put(
            Nsid.FEED_ACTIVITY,
            tier,
            """{"sport":"ride","startedAt":"$startedAt","elapsedTime":3600,"distance":30000,"avgSpeed":8000,"utcOffset":0,""" +
                """"createdAt":"2026-10-05T08:00:00Z"}""",
            key,
        )

    private fun details(tier: String, fields: String, activity: String = activityUri) =
        put(Nsid.FEED_ACTIVITY_DETAILS, tier, """{"activity":"$activity",$fields"createdAt":"2026-10-05T08:00:00Z"}""")

    private fun streams(tier: String, blob: String, channels: List<String>) =
        put(
            Nsid.FEED_ACTIVITY_STREAMS,
            tier,
            """{"activity":"$activityUri","channels":[${channels.joinToString(",") { "\"$it\"" }}],""" +
                """"data":{"${'$'}type":"blob","ref":{"${'$'}link":"${blobs[blob]?.let(::blobCid) ?: blob}"},""" +
                """"mimeType":"application/json","size":100},"createdAt":"2026-10-05T08:00:00Z"}""",
        )

    /** A Public ride with Power at Followers and Heart rate and Start time at Only me. */
    private fun splitRide() {
        activity()
        details(Visibility.FOLLOWERS, """"avgPower":210,"maxPower":400,""")
        details(Visibility.SELF, """"avgHeartRate":130,"maxHeartRate":170,"startedAt":"2026-10-05T06:12:00Z",""")
        streams(Visibility.PUBLIC, "bafkpublic", listOf(StreamChannel.SPEED, StreamChannel.ALTITUDE, StreamChannel.LAPS))
        streams(Visibility.FOLLOWERS, "bafkfollowers", listOf(StreamChannel.POWER, StreamChannel.LAPS))
        streams(Visibility.SELF, "bafkself", listOf(StreamChannel.HEART_RATE, StreamChannel.LAPS))
    }

    private suspend fun HttpClient.raw(nsid: String, viewer: String?, query: String): HttpResponse =
        get("/xrpc/$nsid$query") {
            viewer?.let { header(HttpHeaders.Authorization, "Bearer ${keys.getValue(it).serviceJwt(it, service, nsid)}") }
        }

    private suspend fun <P, O> HttpClient.query(method: XrpcQuery<P, O>, viewer: String?, query: String): O {
        val response = raw(method.nsid, viewer, query)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return AtJson.decodeFromString(method.output, response.bodyAsText())
    }

    private suspend fun HttpClient.view(viewer: String?) =
        query(GetActivity.method, viewer, "?uri=${activityUri.encodeURLParameter()}").activity

    private suspend fun HttpClient.streamsFor(viewer: String?) =
        query(GetActivityStreams.method, viewer, "?uri=${activityUri.encodeURLParameter()}")

    private fun ApplicationTestBuilder.start() {
        application { appView(config, db, verifier, streamSource = source) }
        // Bob is a mutual, Carol a follower, Dave a stranger.
        follow(alice, bob)
        follow(bob, alice)
        follow(carol, alice)
    }

    @Test
    fun eachViewerGetsTheDetailsOfTheTiersTheyAreAdmittedTo() = testApplication {
        start()
        splitRide()

        val own = client.view(alice).effectiveRecord()
        assertEquals(210, own.avgPower)
        assertEquals(130, own.avgHeartRate)
        assertEquals(Instant.parse("2026-10-05T06:12:00Z"), own.startedAt)
        assertEquals(listOf(Visibility.FOLLOWERS, Visibility.SELF), client.view(alice).details?.map { it.visibility })

        for (viewer in listOf(bob, carol)) {
            val record = client.view(viewer).effectiveRecord()
            assertEquals(210, record.avgPower, viewer)
            assertNull(record.avgHeartRate, viewer)
            assertEquals(Instant.parse("2026-10-05T00:00:00Z"), record.startedAt, viewer)
        }
        for (viewer in listOf(dave, null)) {
            val view = client.view(viewer)
            assertNull(view.details, viewer)
            assertNull(view.effectiveRecord().avgPower, viewer)
            assertEquals(8000L, view.effectiveRecord().avgSpeed, viewer)
        }
    }

    @Test
    fun feedsCarryDetailsToo() = testApplication {
        start()
        splitRide()
        val page = client.query(GetAuthorActivities.method, carol, "?actor=$alice").activities
        assertEquals(listOf(Visibility.FOLLOWERS), page.single().details?.map { it.visibility })
    }

    @Test
    fun detailsInTheRepoAreIgnored() = testApplication {
        start()
        activity()
        details(Visibility.PUBLIC, """"avgHeartRate":130,""")
        assertNull(client.view(alice).details)
        assertEquals(0L, runBlocking { db.tx { ActivityDetails.selectAll().count() } })
    }

    @Test
    fun detailsOfAnActivityTheViewerCannotSeeAreNotServed() = testApplication {
        start()
        // A Mutuals activity with Only-me details; Carol (a follower) is admitted to followers rows, not to this activity.
        activity(Visibility.MUTUALS)
        details(Visibility.FOLLOWERS, """"avgPower":210,""")
        assertEquals(
            HttpStatusCode.BadRequest,
            client.raw(GetActivity.method.nsid, carol, "?uri=${activityUri.encodeURLParameter()}").status,
        )
        val page = client.query(GetAuthorActivities.method, carol, "?actor=$alice").activities
        assertEquals(emptyList(), page)
    }

    @Test
    fun detailsOutsideTheirActivitysRkeyAreDropped() = testApplication {
        start()
        activity()
        details(Visibility.SELF, """"avgHeartRate":130,""", activity = "at://$alice/${Nsid.FEED_ACTIVITY}/3kother")
        details(Visibility.FOLLOWERS, """"avgHeartRate":130,""", activity = "at://$bob/${Nsid.FEED_ACTIVITY}/$rkey")
        assertNull(client.view(alice).details)
    }

    @Test
    fun deletedDetailsGoOnlyFromTheirOwnTier() = testApplication {
        start()
        splitRide()
        remove(Nsid.FEED_ACTIVITY_DETAILS, Visibility.SELF)
        assertEquals(listOf(Visibility.FOLLOWERS), client.view(alice).details?.map { it.visibility })
        runBlocking { indexer.dropSpace(space(Visibility.FOLLOWERS)) }
        assertNull(client.view(alice).details)
    }

    @Test
    fun streamsMergeTheTiersEachViewerIsAdmittedTo() = testApplication {
        start()
        splitRide()

        val own = client.streamsFor(alice)
        assertEquals(listOf(Visibility.PUBLIC, Visibility.FOLLOWERS, Visibility.SELF), own.parts?.map { it.visibility })
        assertEquals(Visibility.PUBLIC, own.visibility)
        assertEquals(listOf(120, 130, 140), own.data.heartRate)
        assertEquals(listOf(200, 210, 220), own.data.power)
        assertEquals(listOf(5, 6, 7), own.data.speed)
        assertEquals(Lap(start = 0, elapsed = 3, distance = 20, avgSpeed = 6, avgPower = 210, avgHeartRate = 130), own.data.laps?.single())

        val follower = client.streamsFor(carol)
        assertEquals(listOf(Visibility.PUBLIC, Visibility.FOLLOWERS), follower.parts?.map { it.visibility })
        assertEquals(listOf(200, 210, 220), follower.data.power)
        assertNull(follower.data.heartRate)
        assertNull(follower.data.laps?.single()?.avgHeartRate)
        assertEquals(210, follower.data.laps?.single()?.avgPower)

        for (viewer in listOf(dave, null)) {
            val stranger = client.streamsFor(viewer)
            assertEquals(listOf(Visibility.PUBLIC), stranger.parts?.map { it.visibility })
            assertNull(stranger.data.power)
            assertNull(stranger.data.heartRate)
            assertEquals(listOf(StreamChannel.ALTITUDE, StreamChannel.SPEED, StreamChannel.LAPS), stranger.channels)
        }
    }

    @Test
    fun onlyNarrowTiersMeansTheWidestAdmittedIsTheVisibility() = testApplication {
        start()
        activity()
        streams(Visibility.FOLLOWERS, "bafkfollowers", listOf(StreamChannel.POWER, StreamChannel.LAPS))
        streams(Visibility.SELF, "bafkself", listOf(StreamChannel.HEART_RATE, StreamChannel.LAPS))
        assertEquals(Visibility.FOLLOWERS, client.streamsFor(alice).visibility)
        assertEquals(
            HttpStatusCode.NotFound,
            client.raw(GetActivityStreams.method.nsid, dave, "?uri=${activityUri.encodeURLParameter()}").status,
        )
    }

    @Test
    fun twoTiersStreamsCoexistAndAreDeletedIndependently() = testApplication {
        start()
        splitRide()
        assertEquals(3L, runBlocking { db.tx { ActivityStreams.selectAll().count() } })
        remove(Nsid.FEED_ACTIVITY_STREAMS, Visibility.FOLLOWERS)
        assertEquals(listOf(Visibility.PUBLIC, Visibility.SELF), client.streamsFor(alice).parts?.map { it.visibility })
        remove(Nsid.FEED_ACTIVITY_STREAMS, Visibility.PUBLIC)
        assertEquals(listOf(Visibility.SELF), client.streamsFor(alice).parts?.map { it.visibility })
        assertNull(client.streamsFor(alice).data.speed)
    }

    @Test
    fun anOldSingleRecordActivityIsServedAsBefore() = testApplication {
        start()
        activity()
        streams(Visibility.SELF, "bafkold", listOf(StreamChannel.HEART_RATE, StreamChannel.POWER))
        assertEquals(8000L, client.view(dave).record.avgSpeed)
        val own = client.streamsFor(alice)
        assertEquals(Visibility.SELF, own.visibility)
        assertEquals(listOf(Visibility.SELF), own.parts?.map { it.visibility })
        assertEquals(listOf(120, 130, 140), own.data.heartRate)
        assertEquals(
            HttpStatusCode.NotFound,
            client.raw(GetActivityStreams.method.nsid, bob, "?uri=${activityUri.encodeURLParameter()}").status,
        )
    }

    @Test
    fun dateFiltersSeeOnlyTheRecordsStart() = testApplication {
        start()
        splitRide()
        suspend fun byStart(since: String, until: String) =
            client.query(GetAuthorActivitiesByStart.method, alice, "?actor=$alice&since=$since&until=$until").activities.map { it.uri.rkey }
        suspend fun filtered(since: String, until: String) =
            client.query(GetAuthorActivities.method, alice, "?actor=$alice&since=$since&until=$until").activities.map { it.uri.rkey }

        // The exact start (06:12) is only in the self details: a range around it, inside the day, doesn't match.
        assertEquals(emptyList(), byStart("2026-10-05T06:00:00Z", "2026-10-05T07:00:00Z"))
        assertEquals(emptyList(), filtered("2026-10-05T06:00:00Z", "2026-10-05T07:00:00Z"))
        // The day matches by its date.
        assertEquals(listOf(rkey), byStart("2026-10-05T00:00:00Z", "2026-10-06T00:00:00Z"))
        assertEquals(listOf(rkey), filtered("2026-10-05T00:00:00Z", "2026-10-06T00:00:00Z"))
    }
}
