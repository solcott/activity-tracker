package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.ActivityStreamsViews
import io.github.solcott.activitytracker.appview.api.StreamSource
import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.ActivityStreamData as StreamDataCache
import io.github.solcott.activitytracker.appview.db.ActivityStreamFetchFailures
import io.github.solcott.activitytracker.appview.db.ActivityZones
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.HandleLookup
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamsRecord
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.DiveAlert
import io.github.solcott.activitytracker.lexicon.app.DiveAlertKind
import io.github.solcott.activitytracker.lexicon.app.GasSwitch
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.Lap
import io.github.solcott.activitytracker.lexicon.app.LapIntensity
import io.github.solcott.activitytracker.lexicon.app.Leg
import io.github.solcott.activitytracker.lexicon.app.Length
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.StreamChannel
import io.github.solcott.activitytracker.lexicon.app.SwimStroke
import io.github.solcott.activitytracker.lexicon.app.TankPressure
import io.github.solcott.activitytracker.lexicon.app.TrainingLoad
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.WorkoutSet
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
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
        nameOf(blobs, cid)?.let(blobs::get)?.takeIf { did.value == alice }
    }

    /** The real CID of the blob named [name] in [blobs] or [fetchAnswers]; [name] itself for a blob no test holds. */
    private fun link(name: String): String = (blobs[name] ?: fetchAnswers[name])?.let(::blobCid) ?: name

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
    @Suppress("LongParameterList") // Each part of the record a test varies.
    private fun streams(
        tier: String,
        rkey: String = "3kride",
        activity: String = activityUri,
        blob: String = "bafkdata",
        claim: String? = null,
        channels: List<String> = listOf(StreamChannel.HEART_RATE, StreamChannel.POWER),
    ) {
        val tokens = channels.joinToString(",") { "\"$it\"" }
        val record =
            """{"activity":"$activity",${claim?.let { """"visibility":"$it",""" }.orEmpty()}"channels":[$tokens],""" +
                """"data":{"${'$'}type":"blob","ref":{"${'$'}link":"${link(blob)}"},"mimeType":"application/json","size":100},""" +
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

    /** A source for [views] that answers [cid] from [answers] by default, counting calls per CID; [down] throws, [gates] wait. */
    private inner class FetchFake {
        val calls = java.util.concurrent.ConcurrentHashMap<String, Int>()
        val down = mutableSetOf<String>()
        val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        val source = StreamSource { _, cid, _ ->
            val name = nameOf(fetchAnswers, cid) ?: cid
            calls.merge(name, 1, Int::plus)
            gates[name]?.await()
            if (name in down) throw java.io.IOException("unreachable")
            fetchAnswers[name]
        }

        fun calls(cid: String) = calls[cid] ?: 0
    }

    private val followersData = ActivityStreamData(time = listOf(0, 1, 2), cadence = listOf(80, 82, 84))

    /** What [FetchFake] answers, by blob name. */
    private val fetchAnswers by lazy {
        mapOf(
            "bafkdata" to AtJson.encodeToString(ActivityStreamData.serializer(), data).encodeToByteArray(),
            "bafkfollow" to AtJson.encodeToString(ActivityStreamData.serializer(), followersData).encodeToByteArray(),
            "bafkbad" to "not stream data".encodeToByteArray(),
            "bafkhuge" to ByteArray(ActivityStreamsRecord.MAX_DATA_BYTES.toInt() + 1) { ' '.code.toByte() },
        )
    }
    private val clock = MutableClock(Instant.parse("2026-10-27T12:00:00Z"))

    private fun views(fake: FetchFake) = ActivityStreamsViews(db, fake.source, clock)

    private suspend fun ActivityStreamsViews.of(viewer: String?) =
        streams(viewer?.let(::Did), GetActivityStreams.Params(AtUri(activityUri)))

    private fun mutuals() {
        follow(alice, bob)
        follow(bob, alice)
    }

    @Test
    fun anUnreachablePartIsFetchedOncePerPause() = runBlocking {
        mutuals()
        streams(Visibility.PUBLIC)
        streams(Visibility.FOLLOWERS, blob = "bafkfollow", channels = listOf(StreamChannel.CADENCE))
        val fake = FetchFake().apply { down += "bafkfollow" }
        val views = views(fake)

        repeat(10) { _ -> assertEquals(listOf(Visibility.PUBLIC), views.of(bob).parts!!.map { it.visibility }) }
        assertEquals(1, fake.calls("bafkfollow"))

        clock.now += 61.seconds
        views.of(bob)
        assertEquals(2, fake.calls("bafkfollow"), "fetched again after the first pause")
        clock.now += 61.seconds
        views.of(bob)
        assertEquals(2, fake.calls("bafkfollow"), "the second pause is longer")

        fake.down.clear()
        clock.now += 60.seconds
        val back = views.of(bob)
        assertEquals(listOf(Visibility.PUBLIC, Visibility.FOLLOWERS), back.parts!!.map { it.visibility })
        assertEquals(followersData.cadence, back.data.cadence)
        views.of(bob)
        assertEquals(3, fake.calls("bafkfollow"), "held once fetched")
        assertEquals(0L, db.tx { ActivityStreamFetchFailures.selectAll().count() }, "the failure is forgotten")
    }

    @Test
    fun bytesThatDontMatchTheirCidAreNeitherCachedNorMarkedBad() = runBlocking {
        mutuals()
        streams(Visibility.FOLLOWERS, blob = "bafkfollow", channels = listOf(StreamChannel.CADENCE))
        val forged = AtJson.encodeToString(ActivityStreamData.serializer(), followersData.copy(cadence = listOf(1, 1, 1)))
        val fake = FetchFake()
        val views = views(fake)
        // Another PDS answers the same CID with forged data, then with junk: neither is the blob the CID names.
        val impostor = StreamSource { _, _, _ -> forged.encodeToByteArray() }
        val forging = ActivityStreamsViews(db, impostor, clock)
        assertNull(runCatching { forging.of(bob) }.getOrNull()?.data?.cadence, "forged data is never served")
        clock.now += 1.hours
        val junk = ActivityStreamsViews(db, StreamSource { _, _, _ -> "junk".encodeToByteArray() }, clock)
        assertNull(runCatching { junk.of(bob) }.getOrNull()?.data?.cadence)
        assertEquals(0L, db.tx { StreamDataCache.selectAll().count() }, "nothing cached under the CID")

        clock.now += 1.hours
        assertEquals(followersData.cadence, views.of(bob).data.cadence, "the genuine blob is still fetched and served")
        assertEquals(1, fake.calls("bafkfollow"))
    }

    @Test
    fun dataThatIsntStreamDataIsNeverFetchedAgain() = runBlocking {
        mutuals()
        streams(Visibility.PUBLIC)
        streams(Visibility.FOLLOWERS, blob = "bafkbad", channels = listOf(StreamChannel.CADENCE))
        streams(Visibility.MUTUALS, blob = "bafkhuge", channels = listOf(StreamChannel.TEMPERATURE))
        val fake = FetchFake()
        val views = views(fake)

        repeat(3) { _ ->
            assertEquals(listOf(Visibility.PUBLIC), views.of(bob).parts!!.map { it.visibility })
            clock.now += 2.hours
        }
        assertEquals(1, fake.calls("bafkbad"))
        assertEquals(1, fake.calls("bafkhuge"))
    }

    @Test
    fun anActivitysPartsAreFetchedTogether() = runBlocking {
        mutuals()
        streams(Visibility.PUBLIC)
        streams(Visibility.FOLLOWERS, blob = "bafkfollow", channels = listOf(StreamChannel.CADENCE))
        val gate = CompletableDeferred<Unit>()
        val fake = FetchFake().apply { gates += mapOf("bafkdata" to gate, "bafkfollow" to gate) }

        val output = async { views(fake).of(bob) }
        withTimeout(5.seconds) { while (fake.calls.size < 2) delay(10) }
        gate.complete(Unit)
        assertEquals(listOf(Visibility.PUBLIC, Visibility.FOLLOWERS), output.await().parts!!.map { it.visibility })
    }

    @Test
    fun viewersAskingAtOnceShareOneFetch() = runBlocking {
        streams(Visibility.PUBLIC)
        val gate = CompletableDeferred<Unit>()
        val fake = FetchFake().apply { gates["bafkdata"] = gate }
        val views = views(fake)

        val first = async { views.of(null) }
        val second = async { views.of(carol) }
        withTimeout(5.seconds) { while (fake.calls("bafkdata") < 1) delay(10) }
        // Long enough for the second view to reach the fetch the first one started.
        delay(200)
        gate.complete(Unit)
        assertEquals(data, first.await().data)
        assertEquals(data, second.await().data)
        assertEquals(1, fake.calls("bafkdata"))
    }

    @Test
    fun aSharedFetchServesNoOneItsTierDoesntAdmit() = runBlocking {
        mutuals()
        streams(Visibility.PUBLIC)
        streams(Visibility.FOLLOWERS, blob = "bafkfollow", channels = listOf(StreamChannel.CADENCE))
        val gate = CompletableDeferred<Unit>()
        val fake = FetchFake().apply { gates["bafkfollow"] = gate }
        val views = views(fake)

        val follower = async { views.of(bob) }
        withTimeout(5.seconds) { while (fake.calls("bafkfollow") < 1) delay(10) }
        val stranger = views.of(null)
        assertEquals(listOf(Visibility.PUBLIC), stranger.parts!!.map { it.visibility })
        assertNull(stranger.data.cadence)
        gate.complete(Unit)
        assertEquals(followersData.cadence, follower.await().data.cadence)
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
    private fun zones(tier: String, rkey: String = "3kride", activity: String = activityUri, trainingLoad: String = "") {
        val record =
            """{"activity":"$activity","powerZones":{"model":"coggan6","lows":[0,138,188,225,263,300]}$trainingLoad,""" +
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

    private fun zoneRows() = runBlocking { db.tx { ActivityZones.selectAll().count() } }

    @Test
    fun aDeletedAccountsZonesAreGone() = testApplication {
        start()
        zones(Visibility.PUBLIC)
        zones(Visibility.MUTUALS, rkey = "3kother", activity = "at://$alice/${Nsid.FEED_ACTIVITY}/3kother")
        assertEquals(2L, zoneRows())
        runBlocking {
            indexer.handle(
                JetstreamEvent.Account(
                    did = alice,
                    time = "2026-10-15T12:00:00.000000Z",
                    account = JetstreamEvent.Account.Body(alice, active = false, status = "deleted"),
                )
            )
        }
        assertEquals(0L, zoneRows())
    }

    @Test
    fun aDroppedSpacesZonesAreGone() = testApplication {
        start()
        zones(Visibility.PUBLIC)
        zones(Visibility.MUTUALS, rkey = "3kother", activity = "at://$alice/${Nsid.FEED_ACTIVITY}/3kother")
        runBlocking { indexer.dropSpace(SpaceUri.of(Did(alice), Nsid.SPACE_ATHLETE, Visibility.MUTUALS)) }
        assertEquals(1L, zoneRows(), "the repo's zones stay")
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
    fun newChannelsSwitchesTanksAndLapsAreServed() = testApplication {
        start()
        val dive =
            ActivityStreamData(
                time = listOf(0, 10, 20),
                depth = listOf(0, 1800, 2100),
                ppO2 = listOf(210, 588, 610),
                decoStopDepth = listOf(null, 600, 300),
                gasSwitches = listOf(GasSwitch(0, 21, 0), GasSwitch(20, 50, 0)),
                tankPressures = listOf(TankPressure(listOf(200_000, null, 190_000))),
                laps = listOf(Lap(start = 0, elapsed = 20)),
            )
        blobs["bafkdive"] = AtJson.encodeToString(ActivityStreamData.serializer(), dive).encodeToByteArray()
        streams(Visibility.PUBLIC, blob = "bafkdive", channels = dive.channels)

        val output = client.streamsFor(alice).output()
        assertEquals(dive, output.data)
        assertEquals(dive.channels, output.channels)
        assertTrue(StreamChannel.PP_O2 in output.channels)
    }

    @Test
    fun diveAlertsAreServedAndCapped() = testApplication {
        start()
        val dive =
            ActivityStreamData(
                time = listOf(0, 10, 20),
                depth = listOf(0, 1800, 2100),
                diveAlerts = listOf(DiveAlert(10, DiveAlertKind.NDL_REACHED), DiveAlert(20, "x".repeat(100))),
            )
        blobs["bafkalerts"] = AtJson.encodeToString(ActivityStreamData.serializer(), dive).encodeToByteArray()
        streams(Visibility.PUBLIC, blob = "bafkalerts", channels = dive.channels)

        val output = client.streamsFor(alice).output()
        assertEquals(DiveAlert(10, DiveAlertKind.NDL_REACHED), output.data.diveAlerts!!.first())
        assertEquals(DiveAlert.MAX_KIND_LENGTH, output.data.diveAlerts!![1].kind.length, "a kind is cut to the lexicon's length")
        assertTrue(StreamChannel.DIVE_ALERTS in output.channels)

        val many = ActivityStreamsViews.bounded(data.copy(diveAlerts = List(600) { DiveAlert(it, DiveAlertKind.PO2_WARN) }))
        assertEquals(ActivityStreamsViews.MAX_DIVE_ALERTS, many.diveAlerts!!.size)
    }

    @Test
    fun trainingLoadServesOnlyViewersTheZonesAdmit() = testApplication {
        start()
        streams(Visibility.PUBLIC)
        zones(Visibility.SELF, trainingLoad = ""","trainingLoad":{"ftp":250,"trainingStressScore":1124,"aerobicTrainingEffect":99}""")

        // A training effect out of range is dropped.
        assertEquals(TrainingLoad(ftp = 250, trainingStressScore = 1124), client.streamsFor(alice).output().trainingLoad)
        assertNull(client.streamsFor(bob).output().trainingLoad)
        assertNull(client.streamsFor(null).output().trainingLoad)
    }

    @Test
    fun trainingLoadServesItsHrvToViewersTheZonesAdmit() = testApplication {
        start()
        streams(Visibility.PUBLIC)
        zones(Visibility.SELF, trainingLoad = ""","trainingLoad":{"ftp":250,"hrvRmssd":42}""")

        assertEquals(TrainingLoad(ftp = 250, hrvRmssd = 42), client.streamsFor(alice).output().trainingLoad)
        assertNull(client.streamsFor(bob).output().trainingLoad)
    }

    @Test
    fun trainingLoadServesGarminPhysiologyWithinBounds() = testApplication {
        start()
        streams(Visibility.PUBLIC)
        zones(
            Visibility.SELF,
            trainingLoad = ""","trainingLoad":{"exerciseLoad":187,"staminaStart":150,"staminaEnd":70,"staminaMin":48,"sweatLoss":764}""",
        )

        // A stamina over 100% is dropped; the rest stands.
        assertEquals(
            TrainingLoad(exerciseLoad = 187, staminaEnd = 70, staminaMin = 48, sweatLoss = 764),
            client.streamsFor(alice).output().trainingLoad,
        )
        assertNull(client.streamsFor(bob).output().trainingLoad)
    }

    @Test
    fun anHrvOutOfRangeIsDropped() = testApplication {
        start()
        streams(Visibility.PUBLIC)
        zones(Visibility.SELF, trainingLoad = ""","trainingLoad":{"hrvRmssd":90000}""")

        assertNull(client.streamsFor(alice).output().trainingLoad, "a load with nothing left in range is none")
    }

    @Test
    fun theNewChannelsAreAlignedLikeTheRest() {
        val bounded =
            ActivityStreamsViews.bounded(
                data.copy(
                    torqueEffectivenessLeft = listOf(8000, 8100, 7900),
                    smO2 = listOf(655),
                    legSpringStiffness = listOf(950, 951, 952),
                )
            )
        assertEquals(listOf(8000, 8100, 7900), bounded.torqueEffectivenessLeft)
        assertEquals(listOf(950, 951, 952), bounded.legSpringStiffness)
        assertNull(bounded.smO2, "misaligned")
    }

    @Test
    fun misalignedTankSeriesKeepTheirPlaceEmpty() {
        val bounded =
            ActivityStreamsViews.bounded(data.copy(tankPressures = listOf(TankPressure(listOf(1)), TankPressure(listOf(1, 2, 3)))))
        assertEquals(listOf(TankPressure(listOf(null, null, null)), TankPressure(listOf(1, 2, 3))), bounded.tankPressures)
    }

    @Test
    fun misalignedChannelsAreDropped() {
        val bounded = ActivityStreamsViews.bounded(data.copy(power = listOf(1)))
        assertNull(bounded.power)
        assertEquals(data.heartRate, bounded.heartRate)
    }

    @Test
    fun lengthsSetsLegsAndLapDetailsAreServed() = testApplication {
        start()
        val workout =
            data.copy(
                laps = listOf(Lap(0, 20, calories = 12, intensity = LapIntensity.REST, trigger = "distance")),
                lengths = listOf(Length(0, 30, SwimStroke.FREESTYLE, 15), Length(30, 20, rest = true)),
                sets = listOf(WorkoutSet(0, 40, category = "bench_press", exercise = "barbell_bench_press", reps = 8, weight = 60_000)),
                legs = listOf(Leg(0, 1800, Sport.SWIM, distance = 1500), Leg(1800, 120, Sport.MULTISPORT, transition = true)),
            )
        blobs["bafkworkout"] = AtJson.encodeToString(ActivityStreamData.serializer(), workout).encodeToByteArray()
        streams(Visibility.PUBLIC, blob = "bafkworkout", channels = workout.channels)

        val output = client.streamsFor(alice).output()
        assertEquals(workout, output.data, "a valid blob is served unchanged")
        assertTrue(
            StreamChannel.LENGTHS in output.channels && StreamChannel.SETS in output.channels && StreamChannel.LEGS in output.channels
        )
    }

    @Test
    fun oversizedOrMalformedStructureIsBounded() {
        val long = "x".repeat(500)
        val bounded =
            ActivityStreamsViews.bounded(
                data.copy(
                    laps = listOf(Lap(0, 20, calories = -5, intensity = long, trigger = long)),
                    lengths = List(ActivityStreamsViews.MAX_LENGTHS + 10) { Length(it, -1, long, strokes = -3) },
                    sets =
                        List(ActivityStreamsViews.MAX_SETS + 10) {
                            WorkoutSet(it, -1, category = long, exercise = long, reps = -1, weight = -1)
                        },
                    legs =
                        List(ActivityStreamsViews.MAX_LEGS + 10) { Leg(it, -1, long, subSport = long, avgHeartRate = 999, calories = -1) },
                )
            )
        val lap = bounded.laps!!.single()
        assertEquals(0, lap.calories)
        assertEquals(32, lap.intensity!!.length)
        assertEquals(32, lap.trigger!!.length)
        assertEquals(ActivityStreamsViews.MAX_LENGTHS, bounded.lengths!!.size)
        assertEquals(Length(0, 0, "x".repeat(32), strokes = 0), bounded.lengths!!.first())
        assertEquals(ActivityStreamsViews.MAX_SETS, bounded.sets!!.size)
        assertEquals(WorkoutSet(0, 0, category = "x".repeat(64), exercise = "x".repeat(64), reps = 0, weight = 0), bounded.sets!!.first())
        assertEquals(ActivityStreamsViews.MAX_LEGS, bounded.legs!!.size)
        val leg = bounded.legs!!.first()
        assertEquals(64, leg.sport.length)
        assertEquals(300, leg.avgHeartRate)
        assertEquals(0, leg.elapsed)
        assertEquals(0, leg.calories)
    }
}
