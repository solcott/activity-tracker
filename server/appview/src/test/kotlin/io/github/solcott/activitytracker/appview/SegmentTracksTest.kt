package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.db.SegmentMatchJobs
import io.github.solcott.activitytracker.appview.db.SegmentPrefs
import io.github.solcott.activitytracker.appview.db.SegmentTracks
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.db.usableSegmentTracks
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * Segment tracks and the segment matching setting (`segments/segment-track`, `spaces/appview-syncer`): indexed from the athlete's `self`
 * space only, used only while matching is on, dropped when it goes off, and never served.
 */
class SegmentTracksTest {
    private val service = "did:web:localhost%3A8081"
    private val alice = "did:plc:alice"
    private val bob = "did:plc:bob"

    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val indexer = Indexer(db, { did -> did.value.substringAfterLast(':') + ".test" })
    private val config = AppViewConfig.fromEnv(emptyMap())
    private val verifier = ServiceAuthVerifier(service, keys = { null })

    private fun space(skey: String, did: String = alice) = SpaceUri.of(Did(did), Nsid.SPACE_ATHLETE, skey)

    private fun activityUri(rkey: String, did: String = alice) = "at://$did/${Nsid.FEED_ACTIVITY}/$rkey"

    @Suppress("LongParameterList") // The fields a test varies.
    private fun segmentTrack(
        rkey: String,
        activity: String = activityUri(rkey),
        data: Boolean = true,
        dataCid: String = "bafkdata$rkey",
        revision: String = "3l2abcdefgh22",
        excluded: Boolean? = null,
    ) =
        """{"${'$'}type":"${Nsid.FEED_SEGMENT_TRACK}","activity":"$activity","zonesRevision":"$revision",""" +
            (if (data) """"data":{"${'$'}type":"blob","ref":{"${'$'}link":"$dataCid"},"mimeType":"application/json","size":10},"""
            else "") +
            excluded?.let { """"excludeFromLeaderboards":$it,""" }.orEmpty() +
            """"createdAt":"2026-10-15T12:00:00Z"}"""

    private fun prefs(matching: Boolean, hidden: Boolean? = null) =
        """{"${'$'}type":"${Nsid.ACTOR_SEGMENT_PREFS}","matching":$matching,""" +
            hidden?.let { """"hideFromLeaderboards":$it,""" }.orEmpty() +
            """"updatedAt":"2026-10-15T12:00:00Z"}"""

    private fun write(space: SpaceUri, collection: String, rkey: String, json: String) = runBlocking {
        indexer.spaceWrite(space, collection, rkey, "bafy$rkey", AtJson.parseToJsonElement(json).jsonObject)
    }

    private fun putTrack(rkey: String, skey: String = AthleteSpace.SELF) =
        write(space(skey), Nsid.FEED_SEGMENT_TRACK, rkey, segmentTrack(rkey))

    private fun setMatching(on: Boolean, skey: String = AthleteSpace.SELF) = write(space(skey), Nsid.ACTOR_SEGMENT_PREFS, "self", prefs(on))

    private fun trackRows(): List<String> = runBlocking { db.tx { SegmentTracks.selectAll().map { it[SegmentTracks.uri] } } }

    private fun trackJobs(): List<String> = runBlocking {
        db.tx { SegmentMatchJobs.selectAll().where { SegmentMatchJobs.kind eq SegmentJobs.TRACK }.map { it[SegmentMatchJobs.subjectUri] } }
    }

    private fun excluded(rkey: String): Boolean = runBlocking {
        db.tx { SegmentTracks.selectAll().where { SegmentTracks.uri eq trackUri(rkey) }.single()[SegmentTracks.leaderboardExcluded] }
    }

    private fun hidden(): Boolean = runBlocking {
        db.tx { SegmentPrefs.selectAll().where { SegmentPrefs.did eq alice }.single()[SegmentPrefs.hideFromLeaderboards] }
    }

    private fun prefsRows(): Long = runBlocking { db.tx { SegmentPrefs.selectAll().count() } }

    private fun usable(): List<String> = runBlocking { db.tx { usableSegmentTracks().map { it[SegmentTracks.uri] } } }

    private fun trackUri(rkey: String) = "at://$alice/${Nsid.FEED_SEGMENT_TRACK}/$rkey"

    @Test
    fun segmentTracksAndTheSettingAreIndexedFromSelf() {
        setMatching(true)
        putTrack("3ka")

        assertEquals(listOf(trackUri("3ka")), trackRows())
        assertEquals(1L, prefsRows())
        assertEquals(listOf(trackUri("3ka")), usable())
    }

    @Test
    fun segmentTracksOutsideSelfAreIgnored() {
        setMatching(true, skey = Visibility.FOLLOWERS)
        putTrack("3ka", skey = Visibility.FOLLOWERS)
        setMatching(true, skey = AthleteSpace.INTERACTIONS)
        putTrack("3kb", skey = AthleteSpace.INTERACTIONS)
        putTrack("3kc", skey = Visibility.MUTUALS)

        assertEquals(emptyList(), trackRows())
        assertEquals(0L, prefsRows())
    }

    @Test
    fun theFullTrackAndZonesInSelfAreStillNotStored() {
        val selfSpace = space(AthleteSpace.SELF)
        val track =
            """{"activity":"${activityUri("3ka")}","track":{"${'$'}type":"blob","ref":{"${'$'}link":"bafkgpx"},""" +
                """"mimeType":"application/gpx+xml","size":10},"createdAt":"2026-10-15T12:00:00Z"}"""
        write(selfSpace, Nsid.FEED_ACTIVITY_TRACK, "3ka", track)
        write(selfSpace, Nsid.ACTOR_PRIVACY_ZONES, "self", """{"revision":"3l2abcdefgh22","zones":[]}""")

        assertFalse(Indexer.spaceCollections(AthleteSpace.SELF).contains(Nsid.FEED_ACTIVITY_TRACK))
        assertFalse(Indexer.spaceCollections(AthleteSpace.SELF).contains(Nsid.ACTOR_PRIVACY_ZONES))
        assertEquals(emptyList(), trackRows())
    }

    @Test
    fun aSegmentTrackStoredWhileMatchingIsOffIsntUsable() {
        putTrack("3ka")
        assertEquals(listOf(trackUri("3ka")), trackRows(), "stored, since the setting may arrive later")
        assertEquals(emptyList(), usable(), "no setting: off")

        setMatching(true)
        assertEquals(listOf(trackUri("3ka")), usable())
    }

    @Test
    fun turningMatchingOffDropsEverySegmentTrack() {
        setMatching(true)
        putTrack("3ka")
        putTrack("3kb")

        setMatching(false)
        assertEquals(emptyList(), trackRows(), "dropped before the deletes arrive")

        putTrack("3kc")
        assertEquals(emptyList(), usable(), "a stray track while off is never used")
    }

    @Test
    fun theLeaderboardFlagsAreStored() {
        write(space(AthleteSpace.SELF), Nsid.ACTOR_SEGMENT_PREFS, "self", prefs(matching = true, hidden = true))
        write(space(AthleteSpace.SELF), Nsid.FEED_SEGMENT_TRACK, "3ka", segmentTrack("3ka", excluded = true))
        putTrack("3kb")
        assertTrue(hidden())
        assertTrue(excluded("3ka"))
        assertFalse(excluded("3kb"))

        setMatching(true)
        assertFalse(hidden())
    }

    @Test
    fun onlyAChangedTrackIsMatchedAgain() {
        setMatching(true)
        putTrack("3ka")
        runBlocking { db.tx { SegmentMatchJobs.deleteAll() } }

        write(space(AthleteSpace.SELF), Nsid.FEED_SEGMENT_TRACK, "3ka", segmentTrack("3ka", excluded = true))
        assertTrue(excluded("3ka"))
        assertEquals(emptyList(), trackJobs())

        write(space(AthleteSpace.SELF), Nsid.FEED_SEGMENT_TRACK, "3ka", segmentTrack("3ka", dataCid = "bafknew", excluded = true))
        assertEquals(listOf(trackUri("3ka")), trackJobs())
        runBlocking { db.tx { SegmentMatchJobs.deleteAll() } }

        write(
            space(AthleteSpace.SELF),
            Nsid.FEED_SEGMENT_TRACK,
            "3ka",
            segmentTrack("3ka", dataCid = "bafknew", revision = "3l2abcdefgh33"),
        )
        assertEquals(listOf(trackUri("3ka")), trackJobs())
        assertFalse(excluded("3ka"))
    }

    @Test
    fun deletingTheSettingResetsTheOptOut() {
        write(space(AthleteSpace.SELF), Nsid.ACTOR_SEGMENT_PREFS, "self", prefs(matching = true, hidden = true))
        runBlocking { indexer.spaceDelete(space(AthleteSpace.SELF), Nsid.ACTOR_SEGMENT_PREFS, "self") }
        setMatching(true)
        assertFalse(hidden())
    }

    @Test
    fun deletingTheSettingCountsAsOff() {
        setMatching(true)
        putTrack("3ka")

        runBlocking { indexer.spaceDelete(space(AthleteSpace.SELF), Nsid.ACTOR_SEGMENT_PREFS, "self") }
        assertEquals(0L, prefsRows())
        assertEquals(emptyList(), trackRows())
    }

    @Test
    fun deletingASegmentTrackRemovesOnlyIt() {
        setMatching(true)
        putTrack("3ka")
        putTrack("3kb")

        runBlocking { indexer.spaceDelete(space(AthleteSpace.SELF), Nsid.FEED_SEGMENT_TRACK, "3ka") }
        assertEquals(listOf(trackUri("3kb")), trackRows())
    }

    @Test
    fun malformedSegmentTracksAreDropped() {
        setMatching(true)
        write(space(AthleteSpace.SELF), Nsid.FEED_SEGMENT_TRACK, "3ka", segmentTrack("3ka", data = false))
        write(space(AthleteSpace.SELF), Nsid.FEED_SEGMENT_TRACK, "3kb", segmentTrack("3kb", activity = activityUri("3kb", did = bob)))
        write(space(AthleteSpace.SELF), Nsid.FEED_SEGMENT_TRACK, "3kc", segmentTrack("3kc", activity = activityUri("3kother")))
        write(space(AthleteSpace.SELF), Nsid.FEED_SEGMENT_TRACK, "3kd", """{"activity":3}""")
        write(space(AthleteSpace.SELF), Nsid.ACTOR_SEGMENT_PREFS, "other", prefs(true))

        assertEquals(emptyList(), trackRows())
        assertEquals(1L, prefsRows(), "only the setting under rkey self")
    }

    @Test
    fun replayingIsIdempotent() {
        repeat(2) {
            setMatching(true)
            putTrack("3ka")
        }
        assertEquals(listOf(trackUri("3ka")), trackRows())
        assertEquals(1L, prefsRows())
    }

    @Test
    fun deletingTheSelfSpaceDropsBoth() {
        setMatching(true)
        putTrack("3ka")

        runBlocking { indexer.dropSpace(space(Visibility.FOLLOWERS)) }
        assertEquals(listOf(trackUri("3ka")), trackRows(), "another tier's deletion leaves them")

        runBlocking { indexer.dropSpace(space(AthleteSpace.SELF)) }
        assertEquals(emptyList(), trackRows())
        assertEquals(0L, prefsRows())
    }

    @Test
    fun aDeletedAccountsSegmentDataIsGone() {
        setMatching(true)
        putTrack("3ka")
        runBlocking {
            indexer.handle(
                JetstreamEvent.Account(
                    did = alice,
                    time = "2026-10-15T12:00:00.000000Z",
                    account = JetstreamEvent.Account.Body(alice, active = false, status = "deleted"),
                )
            )
        }
        assertEquals(emptyList(), trackRows())
        assertEquals(0L, prefsRows())
    }

    private val activityRecord =
        """{"sport":"run","startedAt":"2026-10-01T07:00:00Z","elapsedTime":1800,"createdAt":"2026-10-01T07:31:00Z"}"""

    @Test
    fun viewsOfAnActivityHoldNothingFromItsSegmentTrack() = testApplication {
        application { appView(config, db, verifier) }
        runBlocking {
            indexer.handle(
                JetstreamEvent.Commit(
                    did = alice,
                    time = "2026-10-14T12:00:00.000000Z",
                    operation = "create",
                    collection = Nsid.FEED_ACTIVITY,
                    rkey = "3ka",
                    record = AtJson.parseToJsonElement(activityRecord).jsonObject,
                    cid = "bafy3ka",
                )
            )
        }
        setMatching(true)
        putTrack("3ka")

        val uri = activityUri("3ka").encodeURLParameter()
        val activity = client.get("/xrpc/${GetActivity.method.nsid}?uri=$uri")
        assertEquals(HttpStatusCode.OK, activity.status, activity.bodyAsText())
        val streams = client.get("/xrpc/${GetActivityStreams.method.nsid}?uri=$uri")
        for (body in listOf(activity.bodyAsText(), streams.bodyAsText())) {
            assertFalse("bafkdata3ka" in body || Nsid.FEED_SEGMENT_TRACK in body || "zonesRevision" in body, body)
        }
    }
}
