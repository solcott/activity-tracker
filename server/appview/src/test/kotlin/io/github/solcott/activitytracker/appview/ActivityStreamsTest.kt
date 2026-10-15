package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.ActivityStreamsViews
import io.github.solcott.activitytracker.appview.api.StreamSource
import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.ActivityStreamData as StreamDataCache
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.HandleLookup
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * `getActivityStreams` serves streams by where they're stored: the repo for anyone, a tier space for that tier's viewers. Their zones are a
 * record of their own, served the same way.
 */
class ActivityStreamsTest {
    private val service = "did:web:localhost%3A8081"
    private val alice = "did:plc:alice"
    private val bob = "did:plc:bob"
    private val carol = "did:plc:carol"
    private val keys = mapOf(alice to TestAccountKey(), bob to TestAccountKey(), carol to TestAccountKey())

    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val indexer = Indexer(db, HandleLookup { did -> did.value.substringAfterLast(':') + ".test" })
    private val config = AppViewConfig.fromEnv(emptyMap())
    private val verifier = ServiceAuthVerifier(service, keys = { keys[it.value]?.multibase })

    private val data = ActivityStreamData(time = listOf(0, 1, 2), heartRate = listOf(120, null, 130), power = listOf(200, 210, 220))
    private val blobs = mutableMapOf("bafkdata" to AtJson.encodeToString(ActivityStreamData.serializer(), data).encodeToByteArray())
    private var fetches = 0
    private val source = StreamSource { did, cid, _ ->
        fetches++
        blobs[cid]?.takeIf { did.value == alice }
    }

    private val activityUri = "at://$alice/${Nsid.FEED_ACTIVITY}/3kride"

    private fun commit(did: String, collection: String, rkey: String, record: String?, op: String = "create") = runBlocking {
        indexer.handle(
            JetstreamEvent.Commit(
                did = did,
                time = "2026-09-30T12:00:00.000000Z",
                operation = op,
                collection = collection,
                rkey = rkey,
                record = record?.let { AtJson.parseToJsonElement(it).jsonObject },
                cid = "bafy$rkey",
            )
        )
    }

    /** Alice's streams in her repo when [tier] is public, else in her space for [tier]; [claim] is a stray `visibility` they carry. */
    private fun streams(
        tier: String,
        rkey: String = "3kride",
        activity: String = activityUri,
        blob: String = "bafkdata",
        claim: String? = null,
    ) {
        val record =
            """{"activity":"$activity",${claim?.let { """"visibility":"$it",""" }.orEmpty()}"channels":["heartRate","power"],""" +
                """"data":{"${'$'}type":"blob","ref":{"${'$'}link":"$blob"},"mimeType":"application/json","size":100},""" +
                """"powerZones":{"model":"coggan6","lows":[0,138,188,225,263,300]},"createdAt":"2026-09-30T12:00:00Z"}"""
        if (tier == Visibility.PUBLIC) {
            commit(alice, Nsid.FEED_ACTIVITY_STREAMS, rkey, record)
        } else {
            val space = SpaceUri.of(Did(alice), Nsid.SPACE_ATHLETE, tier)
            runBlocking {
                indexer.spaceWrite(space, Nsid.FEED_ACTIVITY_STREAMS, rkey, "bafy$rkey", AtJson.parseToJsonElement(record).jsonObject)
            }
        }
    }

    private fun follow(author: String, subject: String) =
        commit(
            author,
            Nsid.GRAPH_FOLLOW,
            "3kf${author.last()}${subject.last()}",
            """{"subject":"$subject","createdAt":"2026-09-30T09:00:00Z"}""",
        )

    private fun auth(did: String, nsid: String) = "Bearer ${keys.getValue(did).serviceJwt(did, service, nsid)}"

    private suspend fun HttpClient.streamsFor(viewer: String?, uri: String = activityUri): HttpResponse {
        val nsid = GetActivityStreams.method.nsid
        return get("/xrpc/$nsid?uri=$uri") { viewer?.let { header(HttpHeaders.Authorization, auth(it, nsid)) } }
    }

    private suspend fun HttpResponse.output(): GetActivityStreams.Output {
        assertEquals(HttpStatusCode.OK, status, bodyAsText())
        return AtJson.decodeFromString(GetActivityStreams.method.output, bodyAsText())
    }

    private fun ApplicationTestBuilder.start() {
        application { appView(config, db, verifier, streamSource = source) }
        follow(alice, bob)
        follow(bob, alice)
        // Carol follows Alice, but Alice doesn't follow back: not a mutual.
        follow(carol, alice)
    }

    @Test
    fun publicStreamsServeAnyoneAndAreFetchedOnce() = testApplication {
        start()
        streams(Visibility.PUBLIC)

        assertEquals(data, client.streamsFor(null).output().data)
        assertEquals(data, client.streamsFor(carol).output().data)
        assertEquals(1, fetches)
    }

    @Test
    fun mutualsStreamsServeOnlyTheAthleteAndMutuals() = testApplication {
        start()
        streams(Visibility.MUTUALS)

        assertEquals(Visibility.MUTUALS, client.streamsFor(alice).output().visibility)
        client.streamsFor(bob).output()
        assertEquals(HttpStatusCode.NotFound, client.streamsFor(carol).status)
        assertEquals(HttpStatusCode.NotFound, client.streamsFor(null).status)
    }

    @Test
    fun followersStreamsServeEffectiveFollowersOnly() = testApplication {
        start()
        streams(Visibility.FOLLOWERS)
        // Alice's policy is open: bob and carol both follow her, so both may see them.
        client.streamsFor(alice).output()
        client.streamsFor(bob).output()
        client.streamsFor(carol).output()
        assertEquals(HttpStatusCode.NotFound, client.streamsFor(null).status)

        // With approval on, only the follower she approved still sees them.
        commit(alice, Nsid.ACTOR_PROFILE, "self", """{"followPolicy":"approve"}""")
        commit(alice, Nsid.GRAPH_FOLLOW_APPROVAL, bob, """{"subject":"$bob","createdAt":"2026-09-30T09:00:00Z"}""")
        client.streamsFor(bob).output()
        assertEquals(HttpStatusCode.NotFound, client.streamsFor(carol).status)
    }

    @Test
    fun selfAndUnknownTiersServeOnlyTheAthlete() = testApplication {
        start()
        streams(Visibility.SELF)
        client.streamsFor(alice).output()
        assertEquals(HttpStatusCode.NotFound, client.streamsFor(bob).status)

        val other = "at://$alice/${Nsid.FEED_ACTIVITY}/3kother"
        streams("friendsOfFriends", rkey = "3kother", activity = other)
        assertEquals(Visibility.SELF, client.streamsFor(alice, other).output().visibility)
        assertEquals(HttpStatusCode.NotFound, client.streamsFor(bob, other).status)
    }

    @Test
    fun repoStreamsArePublicWhateverTheyClaim() = testApplication {
        start()
        streams(Visibility.PUBLIC, claim = Visibility.SELF)
        assertEquals(Visibility.PUBLIC, client.streamsFor(null).output().visibility)
        client.streamsFor(carol).output()
    }

    @Test
    fun spaceStreamsTakeTheirTierWhateverTheyClaim() = testApplication {
        start()
        streams(Visibility.MUTUALS, claim = Visibility.PUBLIC)
        assertEquals(Visibility.MUTUALS, client.streamsFor(alice).output().visibility)
        assertEquals(Visibility.MUTUALS, client.streamsFor(bob).output().visibility)
        assertEquals(HttpStatusCode.NotFound, client.streamsFor(carol).status)
        assertEquals(HttpStatusCode.NotFound, client.streamsFor(null).status)
    }

    /** Alice's zones for [activity] in her repo when [tier] is public, else in her space for [tier]. */
    private fun zones(tier: String, rkey: String = "3kride", activity: String = activityUri) {
        val record =
            """{"activity":"$activity","powerZones":{"model":"coggan6","lows":[0,138,188,225,263,300]},""" +
                """"createdAt":"2026-09-30T12:00:00Z"}"""
        if (tier == Visibility.PUBLIC) {
            commit(alice, Nsid.FEED_ACTIVITY_ZONES, rkey, record)
        } else {
            val space = SpaceUri.of(Did(alice), Nsid.SPACE_ATHLETE, tier)
            runBlocking {
                indexer.spaceWrite(space, Nsid.FEED_ACTIVITY_ZONES, rkey, "bafyz$rkey", AtJson.parseToJsonElement(record).jsonObject)
            }
        }
    }

    private fun deleteZones(tier: String, rkey: String = "3kride") {
        if (tier == Visibility.PUBLIC) {
            commit(alice, Nsid.FEED_ACTIVITY_ZONES, rkey, null, op = "delete")
        } else {
            runBlocking { indexer.spaceDelete(SpaceUri.of(Did(alice), Nsid.SPACE_ATHLETE, tier), Nsid.FEED_ACTIVITY_ZONES, rkey) }
        }
    }

    @Test
    fun zonesServeOnlyViewersTheirPlaceAdmits() = testApplication {
        start()
        streams(Visibility.PUBLIC)
        zones(Visibility.MUTUALS)

        assertNotNull(client.streamsFor(alice).output().powerZones)
        assertNotNull(client.streamsFor(bob).output().powerZones)
        // Carol follows Alice one way: she gets the charts, not the zones.
        assertNull(client.streamsFor(carol).output().powerZones)
        assertNull(client.streamsFor(null).output().powerZones)
    }

    @Test
    fun publicZonesServeAnyoneWhoSeesTheCharts() = testApplication {
        start()
        streams(Visibility.PUBLIC)
        zones(Visibility.PUBLIC)
        assertNotNull(client.streamsFor(null).output().powerZones)
        assertNotNull(client.streamsFor(carol).output().powerZones)
    }

    @Test
    fun zonesInAStreamsRecordAreIgnored() = testApplication {
        start()
        // The streams record carries powerZones, but there's no zones record: no one gets zones, the athlete included.
        streams(Visibility.PUBLIC)
        assertNull(client.streamsFor(alice).output().powerZones)
    }

    @Test
    fun theNarrowerCopyOfZonesWinsMidMove() = testApplication {
        start()
        streams(Visibility.PUBLIC)
        zones(Visibility.PUBLIC)
        zones(Visibility.MUTUALS)
        assertNull(client.streamsFor(carol).output().powerZones)
        assertNotNull(client.streamsFor(bob).output().powerZones)

        // The narrower copy goes first: the repo's takes over again until its own delete lands.
        deleteZones(Visibility.MUTUALS)
        assertNotNull(client.streamsFor(carol).output().powerZones)
        deleteZones(Visibility.PUBLIC)
        assertNull(client.streamsFor(alice).output().powerZones)
    }

    @Test
    fun zonesOutsideTheirActivitysRkeyOrInInteractionsAreDropped() = testApplication {
        start()
        streams(Visibility.PUBLIC)
        zones(Visibility.PUBLIC, rkey = "3kother")
        zones(AthleteSpace.INTERACTIONS)
        assertNull(client.streamsFor(alice).output().powerZones)
    }

    @Test
    fun deletedStreamsAreGone() = testApplication {
        start()
        streams(Visibility.PUBLIC)
        client.streamsFor(alice).output()
        commit(alice, Nsid.FEED_ACTIVITY_STREAMS, "3kride", null, op = "delete")
        assertEquals(HttpStatusCode.NotFound, client.streamsFor(alice).status)
        assertEquals(0L, runBlocking { db.tx { StreamDataCache.selectAll().count() } })
    }

    @Test
    fun streamsOutsideTheirActivitysRkeyOrWithoutABlobAreDropped() = testApplication {
        start()
        streams(Visibility.PUBLIC, rkey = "3kother")
        streams(Visibility.PUBLIC, activity = "at://$bob/${Nsid.FEED_ACTIVITY}/3kride")
        assertEquals(HttpStatusCode.NotFound, client.streamsFor(alice).status)
    }

    @Test
    fun garbageBlobsAreNotServed() = testApplication {
        start()
        blobs["bafkjunk"] = "not json".encodeToByteArray()
        streams(Visibility.PUBLIC, blob = "bafkjunk")
        assertEquals(HttpStatusCode.NotFound, client.streamsFor(alice).status)
    }

    @Test
    fun misalignedChannelsAreDropped() {
        val bounded = ActivityStreamsViews.bounded(data.copy(power = listOf(1)))
        assertNull(bounded.power)
        assertEquals(data.heartRate, bounded.heartRate)
    }
}
