package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.FakeAthletePds.Session
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityStreams
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.StreamChannel
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Where an activity's detail categories are stored (`feed/detail-visibility`): split by tier when published, moved by tier when saved, and
 * finished at session start after an interrupted save. The fake PDS holds Alice's repo and tier spaces.
 */
class DetailPlacesTest {
    private val pds = FakeAthletePds()
    private val did = pds.did
    private val repo = pds.repo
    private val stored = pds.stored
    private val writes = pds.writes

    private fun placesOf(collection: String, rkey: String) = pds.placesOf(collection, rkey)

    private suspend fun session(scope: String? = null) = pds.session(scope)

    private val run = ActivityType(Sport.RUN)

    private fun view(uri: AtUri, visibility: String) =
        ActivityView(
            uri = uri,
            cid = Cid("bafyrec"),
            author = ProfileViewBasic(did, Handle("alice.test")),
            record = ActivityRecord(Sport.RUN, Instant.parse("2026-10-14T07:00:00Z"), 1800, Clock.System.now()),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Clock.System.now(),
            visibility = visibility,
        )

    /** A run with heart rate, power, speed and laps that started at 06:12 UTC, at [choice]. */
    private fun splitRun(choice: VisibilityChoice) =
        ActivityDraft(
                type = run,
                title = "Run",
                description = null,
                startedAt = Instant.parse("2026-10-14T06:12:00Z"),
                elapsed = 30.minutes,
                distanceMeters = 5_000.0,
                avgHeartRate = 150,
                maxHeartRate = 180,
                avgPowerWatts = 250,
                calories = 400,
                utcOffsetMinutes = 0,
                streams =
                    ActivityStreamData(
                        time = listOf(0, 1),
                        altitude = listOf(10, 11),
                        speed = listOf(3000, 3100),
                        heartRate = listOf(140, 150),
                        power = listOf(240, 260),
                    ),
            )
            .withVisibility(choice)

    private fun record(place: String, collection: String, rkey: String) = stored.getValue(place).getValue("$collection/$rkey")

    private fun streamsChannels(place: String, rkey: String) =
        record(place, Nsid.FEED_ACTIVITY_STREAMS, rkey).getValue("channels").jsonArray.map { it.jsonPrimitive.content }

    private val hiddenHeartRatePowerAndStart =
        VisibilityChoice(
            Visibility.PUBLIC,
            mapOf(
                DetailCategory.HEART_RATE to Visibility.SELF,
                DetailCategory.POWER to Visibility.FOLLOWERS,
                DetailCategory.START_TIME to Visibility.SELF,
            ),
        )

    @Test
    fun publishingSplitsTheActivityByTier() = runTest {
        val s = session()
        val rkey = s.activities.publish(splitRun(hiddenHeartRatePowerAndStart)).rkey!!

        val activity = record(repo, Nsid.FEED_ACTIVITY, rkey)
        assertFalse("avgHeartRate" in activity || "maxHeartRate" in activity || "avgPower" in activity, activity.toString())
        assertEquals("2026-10-14T00:00:00Z", activity.getValue("startedAt").jsonPrimitive.content, "only the date")
        assertTrue("calories" in activity)
        assertEquals(setOf(Visibility.FOLLOWERS, Visibility.SELF), placesOf(Nsid.FEED_ACTIVITY_DETAILS, rkey), "never in the repo")
        val self = record(Visibility.SELF, Nsid.FEED_ACTIVITY_DETAILS, rkey)
        assertEquals(150, self.getValue("avgHeartRate").jsonPrimitive.content.toInt())
        assertEquals("2026-10-14T06:12:00Z", self.getValue("startedAt").jsonPrimitive.content)
        assertEquals(250, record(Visibility.FOLLOWERS, Nsid.FEED_ACTIVITY_DETAILS, rkey).getValue("avgPower").jsonPrimitive.content.toInt())

        assertEquals(listOf(StreamChannel.ALTITUDE, StreamChannel.SPEED), streamsChannels(repo, rkey))
        assertEquals(listOf(StreamChannel.POWER), streamsChannels(Visibility.FOLLOWERS, rkey))
        assertEquals(listOf(StreamChannel.HEART_RATE), streamsChannels(Visibility.SELF, rkey))
        // Each tier's record has its own blob, holding only its channels.
        val repoBlob =
            record(repo, Nsid.FEED_ACTIVITY_STREAMS, rkey).getValue("data").jsonObject.getValue("ref").jsonObject.getValue("\$link")
        val data = pds.blobs.getValue(repoBlob.jsonPrimitive.content).decodeToString()
        assertFalse("heartRate" in data || "power" in data, data)
        // Narrowest first: every details record before the activity record.
        val order = writes.filter { "${Nsid.FEED_ACTIVITY}/" in it || Nsid.FEED_ACTIVITY_DETAILS in it }
        assertEquals("put repo ${Nsid.FEED_ACTIVITY}/$rkey", order.last(), order.toString())
    }

    @Test
    fun aRidesGearGoesWhereItsCategoryIs() = runTest {
        val s = session()
        val tarmac = AtUri("at://${did.value}/${Nsid.GEAR_ITEM}/3tarmac")
        val helmet = AtUri("at://${did.value}/${Nsid.GEAR_ITEM}/3helmet")
        // Gear never set: Only me, so the public record names no gear.
        val rkey = s.activities.publish(splitRun(VisibilityChoice.PUBLIC).copy(gear = listOf(tarmac, helmet))).rkey!!
        assertFalse("gear" in record(repo, Nsid.FEED_ACTIVITY, rkey))
        assertEquals(
            listOf(tarmac.value, helmet.value),
            record(Visibility.SELF, Nsid.FEED_ACTIVITY_DETAILS, rkey).getValue("gear").jsonArray.map { it.jsonPrimitive.content },
        )

        val public = VisibilityChoice(Visibility.PUBLIC, mapOf(DetailCategory.GEAR to Visibility.PUBLIC))
        val other = s.activities.publish(splitRun(public).copy(gear = listOf(tarmac))).rkey!!
        assertEquals(
            listOf(tarmac.value),
            record(repo, Nsid.FEED_ACTIVITY, other).getValue("gear").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(emptySet(), placesOf(Nsid.FEED_ACTIVITY_DETAILS, other))
    }

    @Test
    fun anEditRemovingTheOnlyBikeLeavesNoGear() = runTest {
        val s = session()
        val tarmac = AtUri("at://${did.value}/${Nsid.GEAR_ITEM}/3tarmac")
        val uri = s.activities.publish(splitRun(VisibilityChoice.PUBLIC).copy(gear = listOf(tarmac)))
        val rkey = uri.rkey!!
        s.manager.setVisibility(view(uri, Visibility.PUBLIC), VisibilityChoice.PUBLIC, gear = emptyList())
        assertFalse("gear" in record(repo, Nsid.FEED_ACTIVITY, rkey))
        assertEquals(emptySet(), placesOf(Nsid.FEED_ACTIVITY_DETAILS, rkey), "the self details record held only the gear")

        // Adding it back with Gear at Followers puts it in the followers tier only.
        val followers = VisibilityChoice(Visibility.PUBLIC, mapOf(DetailCategory.GEAR to Visibility.FOLLOWERS))
        s.manager.setVisibility(view(uri, Visibility.PUBLIC), followers, gear = listOf(tarmac))
        assertEquals(setOf(Visibility.FOLLOWERS), placesOf(Nsid.FEED_ACTIVITY_DETAILS, rkey))
        assertFalse("gear" in record(repo, Nsid.FEED_ACTIVITY, rkey))
    }

    @Test
    fun aRideWithoutGearNeedsNoDetailsGrant() = runTest {
        val scope =
            "atproto transition:generic space:${Nsid.SPACE_ATHLETE}?collection=${Nsid.FEED_ACTIVITY}" +
                "&collection=${Nsid.FEED_ACTIVITY_STREAMS}&collection=${Nsid.FEED_ACTIVITY_ZONES}"
        val s = session(scope = scope)
        // Gear is Only me by default, but with no gear there's nothing to put in a details record.
        s.activities.publish(splitRun(VisibilityChoice.PUBLIC))
        assertTrue(writes.isNotEmpty())
    }

    @Test
    fun aGrantWithoutDetailsRefusesNarrowerCategoriesUpFront() = runTest {
        val scope =
            "atproto transition:generic space:${Nsid.SPACE_ATHLETE}?collection=${Nsid.FEED_ACTIVITY}" +
                "&collection=${Nsid.FEED_ACTIVITY_STREAMS}&collection=${Nsid.FEED_ACTIVITY_ZONES}"
        val s = session(scope = scope)
        val e = assertFailsWith<XrpcException> { s.activities.publish(splitRun(hiddenHeartRatePowerAndStart)) }
        assertEquals(SpaceErrors.SCOPE_MISSING, e.error)
        assertEquals(emptyList(), writes)
        // Every category at the activity's visibility needs no details record.
        s.activities.publish(splitRun(VisibilityChoice(Visibility.FOLLOWERS)))
        assertTrue(writes.isNotEmpty())
    }

    @Test
    fun anInterruptedNarrowingLeavesEverythingStoredAndARetryFinishesIt() = runTest {
        val s = session()
        val uri = s.activities.publish(splitRun(VisibilityChoice.PUBLIC))
        val rkey = uri.rkey!!
        val narrowed = VisibilityChoice(Visibility.PUBLIC, mapOf(DetailCategory.HEART_RATE to Visibility.SELF))

        // The repo record's rewrite fails after the self details and streams were written.
        pds.failPutsOf = Nsid.FEED_ACTIVITY
        assertFailsWith<XrpcException> { s.manager.setVisibility(view(uri, Visibility.PUBLIC), narrowed) }
        assertTrue("avgHeartRate" in record(Visibility.SELF, Nsid.FEED_ACTIVITY_DETAILS, rkey))
        assertTrue(StreamChannel.HEART_RATE in streamsChannels(Visibility.SELF, rkey))
        assertTrue("avgHeartRate" in record(repo, Nsid.FEED_ACTIVITY, rkey), "still in the repo for now")

        pds.failPutsOf = null
        s.manager.setVisibility(view(uri, Visibility.PUBLIC), narrowed)
        assertFalse("avgHeartRate" in record(repo, Nsid.FEED_ACTIVITY, rkey))
        assertFalse(StreamChannel.HEART_RATE in streamsChannels(repo, rkey))
        assertEquals(listOf(StreamChannel.HEART_RATE), streamsChannels(Visibility.SELF, rkey))
        assertEquals(150, record(Visibility.SELF, Nsid.FEED_ACTIVITY_DETAILS, rkey).getValue("avgHeartRate").jsonPrimitive.content.toInt())
    }

    @Test
    fun wideningACategoryMovesItOntoTheActivityRecord() = runTest {
        val s = session()
        val uri = s.activities.publish(splitRun(VisibilityChoice(Visibility.PUBLIC, mapOf(DetailCategory.CALORIES to Visibility.SELF))))
        val rkey = uri.rkey!!
        assertFalse("calories" in record(repo, Nsid.FEED_ACTIVITY, rkey))

        s.manager.setVisibility(view(uri, Visibility.PUBLIC), VisibilityChoice.PUBLIC)
        assertEquals(400, record(repo, Nsid.FEED_ACTIVITY, rkey).getValue("calories").jsonPrimitive.content.toInt())
        assertEquals(emptySet(), placesOf(Nsid.FEED_ACTIVITY_DETAILS, rkey), "the details record held only calories")
    }

    @Test
    fun narrowingTheWholeActivityDragsEveryCategoryAndLeavesNoDetails() = runTest {
        val s = session()
        val uri = s.activities.publish(splitRun(hiddenHeartRatePowerAndStart))
        val rkey = uri.rkey!!
        s.manager.setVisibility(view(uri, Visibility.PUBLIC), hiddenHeartRatePowerAndStart.withActivity(Visibility.SELF))
        assertEquals(setOf(Visibility.SELF), placesOf(Nsid.FEED_ACTIVITY, rkey))
        assertEquals(emptySet(), placesOf(Nsid.FEED_ACTIVITY_DETAILS, rkey))
        assertEquals(setOf(Visibility.SELF), placesOf(Nsid.FEED_ACTIVITY_STREAMS, rkey))
        val activity = record(Visibility.SELF, Nsid.FEED_ACTIVITY, rkey)
        assertEquals("2026-10-14T06:12:00Z", activity.getValue("startedAt").jsonPrimitive.content, "the exact start is back")
        assertTrue("avgHeartRate" in activity && "avgPower" in activity)
    }

    /** An activity from before categories: a Public run whose one streams record, holding every channel, is Only me. */
    private suspend fun Session.oldRun(): AtUri {
        val uri = activities.publish(splitRun(VisibilityChoice.PUBLIC).copy(streams = null))
        val data = splitRun(VisibilityChoice.PUBLIC).streams!!
        streams.put(
            uri,
            ActivityStreams(data, Visibility.PUBLIC, DetailCategory.all.associateWith { Visibility.SELF }, core = Visibility.SELF),
        )
        activities.sync()
        return uri
    }

    @Test
    fun theFirstEditOfAnOldActivityWarnsBeforeWideningItsCoreCharts() = runTest {
        val s = session()
        val uri = s.oldRun()
        assertEquals(setOf(Visibility.SELF), placesOf(Nsid.FEED_ACTIVITY_STREAMS, uri.rkey!!))
        val sameAsServed = VisibilityChoice(Visibility.PUBLIC, DetailCategory.all.associateWith { Visibility.SELF })
        assertTrue(s.manager.coreChartsWiden(view(uri, Visibility.PUBLIC), sameAsServed))
        assertFalse(s.manager.coreChartsWiden(view(uri, Visibility.PUBLIC), sameAsServed.withActivity(Visibility.SELF)))

        s.manager.setVisibility(view(uri, Visibility.PUBLIC), sameAsServed)
        assertEquals(listOf(StreamChannel.ALTITUDE), streamsChannels(repo, uri.rkey!!), "the core charts went with the activity")
        assertFalse(s.manager.coreChartsWiden(view(uri, Visibility.PUBLIC), sameAsServed), "split now")
    }

    @Test
    fun sessionStartRemovesAStaleWideCopy() = runTest {
        val s = session()
        val uri = s.activities.publish(splitRun(VisibilityChoice(Visibility.PUBLIC, mapOf(DetailCategory.HEART_RATE to Visibility.SELF))))
        val rkey = uri.rkey!!
        // An interrupted narrowing on another device left the heart rate in the repo too.
        val repoRecord = stored.getValue(repo)
        repoRecord["${Nsid.FEED_ACTIVITY}/$rkey"] =
            JsonObject(repoRecord.getValue("${Nsid.FEED_ACTIVITY}/$rkey") + ("avgHeartRate" to JsonPrimitive(150)))

        session().activities.repairZones()
        assertFalse("avgHeartRate" in record(repo, Nsid.FEED_ACTIVITY, rkey))
        assertTrue("avgHeartRate" in record(Visibility.SELF, Nsid.FEED_ACTIVITY_DETAILS, rkey))
    }

    @Test
    fun sessionStartLeavesAConsistentSplitActivityAlone() = runTest {
        val s = session()
        s.activities.publish(splitRun(hiddenHeartRatePowerAndStart))
        writes.clear()
        session().activities.repairZones()
        assertEquals(emptyList(), writes.filter { Nsid.FEED_ACTIVITY_ZONES !in it })
    }
}
