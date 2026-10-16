package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.PrivateMediaTokens
import io.github.solcott.activitytracker.appview.api.SpaceBlobs
import io.github.solcott.activitytracker.appview.api.StreamSource
import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.atproto.xrpc.XrpcBytes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.XrpcQuery
import io.github.solcott.activitytracker.lexicon.app.ActivityMediaView
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database

/** Space-held media and charts served by tier, and signed photo URLs are short-lived (`spaces/appview-syncer`). */
class SpaceMediaTest {
    private val service = "did:web:localhost%3A8081"
    private val alice = "did:plc:alice"
    private val bob = "did:plc:bob"
    private val carol = "did:plc:carol"
    private val keys = listOf(alice, bob, carol).associateWith { TestAccountKey() }

    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val indexer = Indexer(db, { did -> did.value.substringAfterLast(':') + ".test" })
    private val config = AppViewConfig.fromEnv(emptyMap())
    private val verifier = ServiceAuthVerifier(service, keys = { keys[it.value]?.multibase })

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

    private fun follow(author: String, subject: String) =
        commit(
            author,
            Nsid.GRAPH_FOLLOW,
            "3kf${author.last()}${subject.last()}",
            """{"subject":"$subject","createdAt":"2026-10-01T09:00:00Z"}""",
        )

    private fun record(day: Int): String {
        val date = "2026-10-${day.toString().padStart(2, '0')}"
        return """{"sport":"run","startedAt":"${date}T07:00:00Z","elapsedTime":1800,"createdAt":"${date}T07:31:00Z"}"""
    }

    private fun space(tier: String) = SpaceUri.of(Did(alice), Nsid.SPACE_ATHLETE, tier)

    private fun uri(rkey: String) = "at://$alice/${Nsid.FEED_ACTIVITY}/$rkey"

    private suspend fun HttpClient.raw(nsid: String, viewer: String?, query: String): HttpResponse =
        get("/xrpc/$nsid$query") {
            viewer?.let { header(HttpHeaders.Authorization, "Bearer ${keys.getValue(it).serviceJwt(it, service, nsid)}") }
        }

    private suspend fun <P, O> HttpClient.query(method: XrpcQuery<P, O>, viewer: String?, query: String): O {
        val response = raw(method.nsid, viewer, query)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return AtJson.decodeFromString(method.output, response.bodyAsText())
    }

    private val photoCid = "bafkreihwzzyxpay7qndclffr6usq5te3of4fra7yucklue2b5ckxhq7aeq"
    private val photoBytes = byteArrayOf(1, 2, 3, 4)
    private val fetchedFrom = mutableListOf<Pair<SpaceUri, String>>()
    private val spaceBlobs = SpaceBlobs { space, repo, cid ->
        fetchedFrom += space to cid
        if (repo.value == alice && cid == photoCid) XrpcBytes(photoBytes, "image/jpeg") else null
    }

    private fun withPhoto(day: Int = 1) =
        record(day).removeSuffix("}") +
            ""","media":[{"${'$'}type":"${Nsid.FEED_ACTIVITY}#image","image":{"${'$'}type":"blob","ref":{"${'$'}link":"$photoCid"},""" +
            """"mimeType":"image/jpeg","size":4}}]}"""

    private val mediaClock = MutableClock(Instant.parse("2026-10-15T12:00:01Z"))

    private fun ApplicationTestBuilder.startSigning(streams: StreamSource = StreamSource { _, _, _ -> null }) {
        application {
            appView(
                config.copy(signingKey = TEST_SIGNING_KEY),
                db,
                verifier,
                streamSource = streams,
                spaceBlobs = spaceBlobs,
                mediaTokens = PrivateMediaTokens(TEST_SIGNING_KEY, mediaClock),
            )
        }
    }

    private fun postSpace(rkey: String, tier: String, json: String) = runBlocking {
        indexer.spaceWrite(space(tier), Nsid.FEED_ACTIVITY, rkey, "bafy$rkey", AtJson.parseToJsonElement(json).jsonObject)
    }

    @Test
    fun followerViewsAPhotoOfAFollowersActivity() = testApplication {
        startSigning()
        follow(bob, alice)
        postSpace("3kpic", Visibility.FOLLOWERS, withPhoto())
        val url = client.query(GetActivity.method, bob, "?uri=${uri("3kpic").encodeURLParameter()}").activity.media!!.single()
        val fullsize = (url as ActivityMediaView.Image).fullsize
        assertTrue(fullsize.contains("viewer=${bob.encodeURLParameter()}"), fullsize)
        // Signed at 12:00:01, so it expires at 12:30:00; fetched 10 minutes later, it may be cached for the 19:59 left.
        assertTrue(fullsize.contains("exp=${Instant.parse("2026-10-15T12:30:00Z").epochSeconds}"), fullsize)
        mediaClock.now += 10.minutes
        val response = client.get(fullsize.removePrefix(config.publicUrl))
        assertEquals(HttpStatusCode.OK, response.status)
        assertContentEquals(photoBytes, response.readRawBytes())
        assertEquals("private, max-age=${(19.minutes + 59.seconds).inWholeSeconds}", response.headers[HttpHeaders.CacheControl])
        assertEquals(listOf(space(Visibility.FOLLOWERS) to photoCid), fetchedFrom)
    }

    @Test
    fun aFullTrackInTheSelfSpaceIsNeitherStoredNorServed() = testApplication {
        startSigning()
        postSpace("3ktrk", Visibility.SELF, record(1))
        val track =
            """{"activity":"${uri("3ktrk")}","createdAt":"2026-10-01T07:31:00Z",""" +
                """"track":{"${'$'}type":"blob","ref":{"${'$'}link":"$photoCid"},"mimeType":"application/gpx+xml","size":4}}"""
        runBlocking {
            indexer.spaceWrite(
                space(Visibility.SELF),
                Nsid.FEED_ACTIVITY_TRACK,
                "3ktrk",
                "bafytrk",
                AtJson.parseToJsonElement(track).jsonObject,
            )
        }

        assertTrue(Nsid.FEED_ACTIVITY_TRACK !in Nsid.indexedCollections)
        assertTrue(listOf(Visibility.SELF, AthleteSpace.INTERACTIONS).none { Nsid.FEED_ACTIVITY_TRACK in Indexer.spaceCollections(it) })
        val response = client.raw(GetActivity.method.nsid, alice, "?uri=${uri("3ktrk").encodeURLParameter()}")
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(photoCid in response.bodyAsText(), "the athlete's own view doesn't link the track either")
    }

    @Test
    fun privacyZonesAndTheirTrimMarkerInTheSelfSpaceAreIgnored() = testApplication {
        startSigning()
        postSpace("3kzon", Visibility.SELF, record(1))
        val zones =
            """{"revision":"3l2abcdefgh22","hideEndsMeters":200,""" +
                """"zones":[{"latitudeE7":515123457,"longitudeE7":-1275000,"radiusMeters":400,"label":"Home"}]}"""
        val trim = """{"revision":"3l2abcdefgh22","updatedAt":"2026-10-01T07:31:00Z"}"""
        runBlocking {
            indexer.spaceWrite(
                space(Visibility.SELF),
                Nsid.ACTOR_PRIVACY_ZONES,
                "self",
                "bafyzon",
                AtJson.parseToJsonElement(zones).jsonObject,
            )
            indexer.spaceWrite(space(Visibility.SELF), Nsid.ACTOR_ROUTE_TRIM, "self", "bafytrm", AtJson.parseToJsonElement(trim).jsonObject)
        }

        for (collection in listOf(Nsid.ACTOR_PRIVACY_ZONES, Nsid.ACTOR_ROUTE_TRIM)) {
            assertTrue(collection !in Nsid.indexedCollections)
            assertTrue(
                listOf(Visibility.FOLLOWERS, Visibility.MUTUALS, Visibility.SELF, AthleteSpace.INTERACTIONS).none {
                    collection in Indexer.spaceCollections(it)
                }
            )
        }
        val response = client.raw(GetActivity.method.nsid, alice, "?uri=${uri("3kzon").encodeURLParameter()}")
        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse("515123457" in response.bodyAsText())
    }

    @Test
    fun expiredPhotoUrlIsRefused() = testApplication {
        startSigning()
        follow(bob, alice)
        postSpace("3kpic", Visibility.FOLLOWERS, withPhoto())
        val url =
            (client.query(GetActivity.method, bob, "?uri=${uri("3kpic").encodeURLParameter()}").activity.media!!.single()
                    as ActivityMediaView.Image)
                .fullsize
                .removePrefix(config.publicUrl)
        mediaClock.now += 30.minutes
        assertEquals(HttpStatusCode.NotFound, client.get(url).status, "bob may still see it, but this URL has expired")
        assertEquals(emptyList(), fetchedFrom)
    }

    @Test
    fun photoUrlsAreServedOnlyToViewersWhoMaySeeTheActivity() = testApplication {
        startSigning()
        follow(bob, alice)
        postSpace("3kpic", Visibility.FOLLOWERS, withPhoto())
        val bobs =
            (client.query(GetActivity.method, bob, "?uri=${uri("3kpic").encodeURLParameter()}").activity.media!!.single()
                    as ActivityMediaView.Image)
                .fullsize
                .removePrefix(config.publicUrl)
        val bare = "/img/media/$alice/$photoCid"
        assertEquals(HttpStatusCode.NotFound, client.get(bare).status, "unsigned: no redirect to the PDS either")
        assertEquals(HttpStatusCode.NotFound, client.get(bobs.replace(bob.encodeURLParameter(), carol.encodeURLParameter())).status)
        assertEquals(HttpStatusCode.NotFound, client.get(bobs.dropLast(2) + "xx").status, "bad signature")
        // Bob unfollows: his URL stops working at once, signature or not.
        commit(bob, Nsid.GRAPH_FOLLOW, "3kf${bob.last()}${alice.last()}", null)
        assertEquals(HttpStatusCode.NotFound, client.get(bobs).status)
        assertEquals(emptyList(), fetchedFrom, "nothing fetched for a refused request")
    }

    @Test
    fun publicPhotosStillRedirect() = testApplication {
        startSigning()
        commit(alice, Nsid.FEED_ACTIVITY, "3kpub", withPhoto())
        val media = client.query(GetActivity.method, null, "?uri=${uri("3kpub").encodeURLParameter()}").activity.media!!.single()
        val fullsize = (media as ActivityMediaView.Image).fullsize
        assertEquals("${config.publicUrl}/img/media/$alice/$photoCid", fullsize)
    }

    @Test
    fun spaceHeldStreamsComeFromTheSpaceByTier() = testApplication {
        val origins = mutableListOf<String>()
        startSigning(
            StreamSource { _, _, origin ->
                origins += origin
                """{"time":[0,1],"heartRate":[120,121]}""".encodeToByteArray()
            }
        )
        follow(bob, alice)
        postSpace("3kfol", Visibility.FOLLOWERS, record(1))
        runBlocking {
            indexer.spaceWrite(
                space(Visibility.FOLLOWERS),
                Nsid.FEED_ACTIVITY_STREAMS,
                "3kfol",
                "bafystreams",
                AtJson.parseToJsonElement(
                        """{"activity":"${uri("3kfol")}","visibility":"public","channels":["heartRate"],""" +
                            """"createdAt":"2026-10-01T07:31:00Z","data":{"${'$'}type":"blob","ref":{"${'$'}link":"$photoCid"},""" +
                            """"mimeType":"application/json","size":40}}"""
                    )
                    .jsonObject,
            )
        }
        val streamsUri = "?uri=${uri("3kfol").encodeURLParameter()}"
        val bobs = client.query(GetActivityStreams.method, bob, streamsUri)
        assertEquals(Visibility.FOLLOWERS, bobs.visibility, "capped at the tier")
        assertEquals(listOf(120, 121), bobs.data.heartRate)
        assertEquals(listOf(space(Visibility.FOLLOWERS).value), origins)
        assertEquals(HttpStatusCode.NotFound, client.raw(GetActivityStreams.method.nsid, carol, streamsUri).status)
    }

    @Test
    fun publicStreamsOfAnOnlyMeActivityStayHidden() = testApplication {
        startSigning(StreamSource { _, _, _ -> """{"time":[0]}""".encodeToByteArray() })
        postSpace("3kme", Visibility.SELF, record(1))
        commit(
            alice,
            Nsid.FEED_ACTIVITY_STREAMS,
            "3kme",
            """{"activity":"${uri("3kme")}","visibility":"public","channels":[],"createdAt":"2026-10-01T07:31:00Z",""" +
                """"data":{"${'$'}type":"blob","ref":{"${'$'}link":"$photoCid"},"mimeType":"application/json","size":12}}""",
        )
        val streamsUri = "?uri=${uri("3kme").encodeURLParameter()}"
        assertEquals(HttpStatusCode.NotFound, client.raw(GetActivityStreams.method.nsid, carol, streamsUri).status)
        assertEquals(HttpStatusCode.OK, client.raw(GetActivityStreams.method.nsid, alice, streamsUri).status)
    }
}
