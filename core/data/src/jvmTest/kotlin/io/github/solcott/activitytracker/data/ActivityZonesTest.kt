package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.FakeAthletePds.Session
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.PendingWritesException
import io.github.solcott.activitytracker.data.activity.ZonesPlacement
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.profile.saveTraining
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.PowerZoneModel
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.ZoneSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Where an activity's zones are stored (`feed/activity-zones`): at the narrower of its charts' and the athlete's training's visibility,
 * following charts moves, narrowed before the training is, and repaired once a session. The fake PDS holds Alice's repo and tier spaces.
 */
class ActivityZonesTest {
    private val pds = FakeAthletePds()
    private val did = pds.did
    private val repo = pds.repo
    private val stored = pds.stored

    private val power = ZoneSnapshot(PowerZoneModel.COGGAN_6, listOf(0, 138, 188, 225, 263, 300))
    private val training = TrainingDetails(functionalThresholdPower = 250)

    private fun zonesAt(rkey: String) = pds.placesOf(Nsid.FEED_ACTIVITY_ZONES, rkey)

    /** Alice's training, saved at [visibility] as the profile screen would store it. */
    private fun trainingAt(visibility: String) {
        val details = """{"training":{"functionalThresholdPower":250}}"""
        if (visibility == Visibility.PUBLIC) {
            stored.getOrPut(repo) { mutableMapOf() }["${Nsid.ACTOR_PROFILE}/self"] =
                json("""{"${'$'}type":"${Nsid.ACTOR_PROFILE}","details":$details,"createdAt":"2026-10-01T00:00:00Z"}""")
        } else {
            stored.getOrPut(visibility) { mutableMapOf() }["${Nsid.ACTOR_PRIVATE_DETAILS}/self"] =
                json("""{"${'$'}type":"${Nsid.ACTOR_PRIVATE_DETAILS}","details":$details}""")
        }
    }

    private fun json(text: String) = AtJson.parseToJsonElement(text).jsonObject

    /** A zones record (and its charts at [charts]) already stored for [rkey] at each of [places]. */
    private fun storedZones(rkey: String, vararg places: String, charts: String? = null) {
        val activity = AtUri.of(did, Nsid.FEED_ACTIVITY, rkey)
        for (place in places) {
            stored.getOrPut(place) { mutableMapOf() }["${Nsid.FEED_ACTIVITY_ZONES}/$rkey"] =
                json(
                    """{"${'$'}type":"${Nsid.FEED_ACTIVITY_ZONES}","activity":"$activity","createdAt":"2026-10-01T00:00:00Z",""" +
                        """"powerZones":{"model":"coggan6","lows":[0,138]}}"""
                )
        }
        if (charts != null) {
            stored.getOrPut(charts) { mutableMapOf() }["${Nsid.FEED_ACTIVITY_STREAMS}/$rkey"] =
                json("""{"${'$'}type":"${Nsid.FEED_ACTIVITY_STREAMS}","activity":"$activity","channels":["power"]}""")
        }
    }

    private fun draft(charts: String) =
        ActivityDraft(
            type = ActivityType(Sport.RIDE),
            title = "Ride",
            description = null,
            startedAt = Instant.parse("2026-10-14T07:00:00Z"),
            elapsed = 30.minutes,
            streams = ActivityStreamData(time = listOf(0, 1), power = listOf(200, 210)),
            streamsVisibility = charts,
            powerZones = power,
        )

    private fun profiles(s: Session) = ProfileRepository(s.clients, Clock.System, Logger, s.zones)

    @Test
    fun targetIsTheNarrowerOfChartsAndTraining() {
        assertEquals(Visibility.PUBLIC, ZonesPlacement.target(Visibility.PUBLIC, Visibility.PUBLIC))
        assertEquals(Visibility.MUTUALS, ZonesPlacement.target(Visibility.PUBLIC, Visibility.MUTUALS))
        assertEquals(Visibility.FOLLOWERS, ZonesPlacement.target(Visibility.FOLLOWERS, Visibility.PUBLIC))
        assertEquals(Visibility.SELF, ZonesPlacement.target(Visibility.PUBLIC, Visibility.SELF))
        // Without training details, never wider than where they are, or Only me.
        assertEquals(Visibility.MUTUALS, ZonesPlacement.target(Visibility.PUBLIC, null, current = Visibility.MUTUALS))
        assertEquals(Visibility.SELF, ZonesPlacement.target(Visibility.PUBLIC, null))
    }

    @Test
    fun publicChartsWithMutualsTrainingPutZonesInTheMutualsSpace() = runTest {
        trainingAt(Visibility.MUTUALS)
        val s = pds.session()
        val uri = s.activities.publish(draft(Visibility.PUBLIC))

        assertEquals(setOf(Visibility.MUTUALS), zonesAt(uri.rkey!!))
        val streams = stored.getValue(repo).getValue("${Nsid.FEED_ACTIVITY_STREAMS}/${uri.rkey}")
        assertFalse("powerZones" in streams, "zones are a record of their own")
    }

    @Test
    fun followersChartsWithPublicTrainingPutZonesInTheFollowersSpace() = runTest {
        trainingAt(Visibility.PUBLIC)
        val uri = pds.session().activities.publish(draft(Visibility.FOLLOWERS).copy(visibility = Visibility.FOLLOWERS))
        assertEquals(setOf(Visibility.FOLLOWERS), zonesAt(uri.rkey!!))
    }

    @Test
    fun everythingPublicPutsZonesInTheRepo() = runTest {
        trainingAt(Visibility.PUBLIC)
        val uri = pds.session().activities.publish(draft(Visibility.PUBLIC))
        assertEquals(setOf(repo), zonesAt(uri.rkey!!))
    }

    @Test
    fun publishingReadsTheTrainingAsSavedNow() = runTest {
        trainingAt(Visibility.MUTUALS)
        val s = pds.session()
        val first = s.activities.publish(draft(Visibility.PUBLIC))
        // Another device narrows the training while this session is signed in.
        stored.getValue(Visibility.MUTUALS).remove("${Nsid.ACTOR_PRIVATE_DETAILS}/self")
        trainingAt(Visibility.SELF)
        val second = s.activities.publish(draft(Visibility.PUBLIC))

        assertEquals(setOf(Visibility.MUTUALS), zonesAt(first.rkey!!))
        assertEquals(setOf(Visibility.SELF), zonesAt(second.rkey!!))
    }

    @Test
    fun narrowingChartsMovesZonesWithThem() = runTest {
        trainingAt(Visibility.PUBLIC)
        storedZones("3kride", repo, charts = repo)
        val s = pds.session()
        s.streams.move(AtUri.of(did, Nsid.FEED_ACTIVITY, "3kride"), Visibility.PUBLIC, Visibility.SELF)
        assertEquals(setOf(Visibility.SELF), zonesAt("3kride"))
    }

    @Test
    fun wideningChartsWidensZonesOnlyUpToTheTraining() = runTest {
        trainingAt(Visibility.FOLLOWERS)
        storedZones("3kride", Visibility.SELF, charts = Visibility.SELF)
        val s = pds.session()
        s.streams.move(AtUri.of(did, Nsid.FEED_ACTIVITY, "3kride"), Visibility.SELF, Visibility.PUBLIC)
        assertEquals(setOf(Visibility.FOLLOWERS), zonesAt("3kride"))
    }

    @Test
    fun narrowingTrainingMovesZonesBeforeTheTraining() = runTest {
        trainingAt(Visibility.FOLLOWERS)
        storedZones("3ka", repo)
        storedZones("3kb", Visibility.FOLLOWERS)
        storedZones("3kc", Visibility.SELF)
        val s = pds.session()

        profiles(s).saveTraining(training, Visibility.SELF)

        assertEquals(setOf(Visibility.SELF), zonesAt("3ka"))
        assertEquals(setOf(Visibility.SELF), zonesAt("3kb"))
        assertEquals(setOf(Visibility.SELF), zonesAt("3kc"))
        val lastZonesWrite = pds.writes.indexOfLast { Nsid.FEED_ACTIVITY_ZONES in it }
        val trainingWrite = pds.writes.indexOfFirst { it == "put ${Visibility.SELF} ${Nsid.ACTOR_PRIVATE_DETAILS}/self" }
        assertTrue(lastZonesWrite in 0..<trainingWrite, "zones move before the training: ${pds.writes}")
    }

    @Test
    fun anInterruptedBackfillKeepsTheTrainingAndFinishesLater() = runTest {
        trainingAt(Visibility.FOLLOWERS)
        storedZones("3ka", repo)
        storedZones("3kb", Visibility.FOLLOWERS)
        val s = pds.session()

        pds.failSpacePuts = true
        assertFailsWith<PendingWritesException> { profiles(s).saveTraining(training, Visibility.SELF) }
        assertTrue("${Nsid.ACTOR_PRIVATE_DETAILS}/self" in stored.getValue(Visibility.FOLLOWERS), "training keeps its visibility")
        assertFalse(stored[Visibility.SELF].orEmpty().containsKey("${Nsid.ACTOR_PRIVATE_DETAILS}/self"))
        assertEquals(setOf(repo), zonesAt("3ka"))

        // The queued moves finish on a later drain, without saving again.
        pds.failSpacePuts = false
        s.activities.sync()
        assertEquals(setOf(Visibility.SELF), zonesAt("3ka"))
        assertEquals(setOf(Visibility.SELF), zonesAt("3kb"))

        // A retry has nothing left to move, and saves the training.
        val before = pds.writes.count { Nsid.FEED_ACTIVITY_ZONES in it }
        profiles(s).saveTraining(training, Visibility.SELF)
        assertEquals(before, pds.writes.count { Nsid.FEED_ACTIVITY_ZONES in it })
        assertTrue("${Nsid.ACTOR_PRIVATE_DETAILS}/self" in stored.getValue(Visibility.SELF))
    }

    @Test
    fun aStuckWriteElsewhereDoesntBlockNarrowingTraining() = runTest {
        trainingAt(Visibility.FOLLOWERS)
        storedZones("3kride", repo)
        val s = pds.session()
        // A write the PDS keeps refusing, queued ahead of the zones moves.
        pds.failPutsOf = Nsid.FEED_LIKE
        OutboxWriter(s.db, s.clients, Clock.System).put(Nsid.FEED_LIKE, "3klike", JsonObject.serializer(), JsonObject(emptyMap()))

        profiles(s).saveTraining(training, Visibility.SELF)

        assertEquals(setOf(Visibility.SELF), zonesAt("3kride"))
        assertTrue("${Nsid.ACTOR_PRIVATE_DETAILS}/self" in stored.getValue(Visibility.SELF))
        assertEquals(1, s.activities.sync(), "the stuck write is still waiting")
    }

    @Test
    fun aPendingZonesWriteStopsTheSaveWithPendingWrites() = runTest {
        trainingAt(Visibility.FOLLOWERS)
        storedZones("3kride", repo)
        val s = pds.session()
        pds.failSpacePuts = true
        assertFailsWith<PendingWritesException> { profiles(s).saveTraining(training, Visibility.SELF) }
    }

    @Test
    fun settingTrainingAgainAfterClearingItNarrowsOldZones() = runTest {
        storedZones("3kride", repo)
        val s = pds.session()
        profiles(s).saveTraining(training, Visibility.SELF)
        assertEquals(setOf(Visibility.SELF), zonesAt("3kride"))
    }

    @Test
    fun aChartsMoveTheSessionCantFollowWithZonesFailsBeforeMovingCharts() = runTest {
        trainingAt(Visibility.PUBLIC)
        storedZones("3kride", repo, charts = repo)
        // Granted the space, but not the zones collection in it.
        val s = pds.session(scope = "atproto transition:generic space:${Nsid.SPACE_ATHLETE}?collection=${Nsid.FEED_ACTIVITY_STREAMS}")
        assertFailsWith<XrpcException> { s.streams.move(AtUri.of(did, Nsid.FEED_ACTIVITY, "3kride"), Visibility.PUBLIC, Visibility.SELF) }
        assertEquals(setOf(repo), pds.placesOf(Nsid.FEED_ACTIVITY_STREAMS, "3kride"), "the charts didn't move past their zones")
    }

    @Test
    fun aBatchReadsTheTrainingOnce() = runTest {
        trainingAt(Visibility.MUTUALS)
        val s = pds.session()
        val before = pds.requests.count { it.endsWith(" ${Nsid.ACTOR_PROFILE}/self") }
        val uris = s.activities.publishAll(List(3) { draft(Visibility.PUBLIC) })
        assertEquals(1, pds.requests.count { it.endsWith(" ${Nsid.ACTOR_PROFILE}/self") } - before)
        uris.forEach { assertEquals(setOf(Visibility.MUTUALS), zonesAt(it.rkey!!)) }
    }

    @Test
    fun wideningOrClearingTrainingLeavesZonesInPlace() = runTest {
        trainingAt(Visibility.SELF)
        storedZones("3kride", Visibility.SELF)
        val s = pds.session()

        profiles(s).saveTraining(training, Visibility.PUBLIC)
        assertEquals(setOf(Visibility.SELF), zonesAt("3kride"))
        profiles(s).saveTraining(null, Visibility.PUBLIC)
        assertEquals(setOf(Visibility.SELF), zonesAt("3kride"))
    }

    @Test
    fun sessionStartMovesZonesLeftWideByAnotherDevice() = runTest {
        trainingAt(Visibility.SELF)
        storedZones("3kride", Visibility.FOLLOWERS, charts = repo)
        pds.session().activities.repairZones()
        assertEquals(setOf(Visibility.SELF), zonesAt("3kride"))
    }

    @Test
    fun sessionStartDeletesALeftoverCopy() = runTest {
        trainingAt(Visibility.PUBLIC)
        storedZones("3kride", Visibility.MUTUALS, Visibility.SELF, charts = repo)
        pds.session().activities.repairZones()
        assertEquals(setOf(Visibility.SELF), zonesAt("3kride"))
    }

    @Test
    fun sessionStartNeverWidensZones() = runTest {
        trainingAt(Visibility.PUBLIC)
        storedZones("3kride", Visibility.SELF, charts = repo)
        val s = pds.session()
        s.activities.repairZones()
        s.activities.repairZones()
        assertEquals(setOf(Visibility.SELF), zonesAt("3kride"))
        assertTrue(pds.writes.none { Nsid.FEED_ACTIVITY_ZONES in it })
    }

    @Test
    fun aSessionWithoutTheSpaceScopeSkipsTheSweep() = runTest {
        storedZones("3kride", Visibility.FOLLOWERS, charts = repo)
        pds.session(scope = "atproto transition:generic").activities.repairZones()
        assertEquals(setOf(Visibility.FOLLOWERS), zonesAt("3kride"))
    }
}
