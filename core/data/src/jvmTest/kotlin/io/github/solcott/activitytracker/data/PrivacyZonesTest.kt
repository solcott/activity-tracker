package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityEdit
import io.github.solcott.activitytracker.data.activity.PrivacySettings
import io.github.solcott.activitytracker.data.activity.RouteTrim
import io.github.solcott.activitytracker.data.activity.TooManyZonesException
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.data.activity.ZonesRead
import io.github.solcott.activitytracker.data.activity.ZonesUnreadableException
import io.github.solcott.activitytracker.data.activity.zonesOutcome
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.PrivacyZone
import io.github.solcott.activitytracker.lexicon.app.PrivacyZonesRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.RouteTrimRecord
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.Gpx
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.TrackMath
import io.github.solcott.dataresult.DataError
import io.github.solcott.dataresult.Origin
import io.github.solcott.dataresult.Outcome
import io.github.solcott.dataresult.dataOrNull
import io.ktor.http.HttpStatusCode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Privacy zones (`profile/privacy-zones`): stored in `self`, trimmed into every polyline as it's written, at every visibility, and trimmed
 * again from each activity's track when they change. The fake PDS holds Alice's repo and tier spaces.
 */
class PrivacyZonesTest {
    private val pds = FakeAthletePds()
    private val start = Instant.parse("2026-10-14T07:00:00Z")

    /** About 3.3 km east along the equator from (0, 0), a point every 11 m. */
    private val route =
        (0..300).map { LocationSample(0.0, it * 0.0001, null, speed = null, accuracy = 5.0, time = start + (it * 4).seconds) }

    /** A 200 m zone around the route's start, stored as is (no offset), as another device would have saved it. */
    private val home = PrivacyZone(latitudeE7 = 0, longitudeE7 = 0, radiusMeters = 200, label = "Home")

    private fun draft(choice: VisibilityChoice = VisibilityChoice.PUBLIC, points: List<LocationSample> = route) =
        ActivityDraft(
                type = ActivityType(Sport.RUN),
                title = "Run",
                description = null,
                startedAt = start,
                elapsed = 20.minutes,
                distanceMeters = 3336.0,
                route = points,
                gpx = Gpx.write("Run", Sport.RUN, points).encodeToByteArray(),
            )
            .withVisibility(choice)

    private fun stored(rkey: String, at: String = pds.repo): ActivityRecord =
        AtJson.decodeFromJsonElement(ActivityRecord.serializer(), pds.stored.getValue(at).getValue("${Nsid.FEED_ACTIVITY}/$rkey"))

    /** Meters from (0, 0) to the polyline's first point. */
    private fun startOf(polyline: String?): Double {
        val (lat, lon) = TrackMath.decodePolyline(assertNotNull(polyline)).first()
        return TrackMath.distanceMeters(0.0, 0.0, lat, lon)
    }

    private fun zonesRecord(): PrivacyZonesRecord? =
        pds.stored[Visibility.SELF]?.get("${Nsid.ACTOR_PRIVACY_ZONES}/self")?.let {
            AtJson.decodeFromJsonElement(PrivacyZonesRecord.serializer(), it)
        }

    private fun marker(): String? =
        pds.stored[Visibility.SELF]?.get("${Nsid.ACTOR_ROUTE_TRIM}/self")?.let {
            AtJson.decodeFromJsonElement(RouteTrimRecord.serializer(), it).revision
        }

    private fun view(uri: AtUri) =
        ActivityView(
            uri = uri,
            cid = Cid("bafyrec"),
            author = ProfileViewBasic(pds.did, Handle("alice.test")),
            record = ActivityRecord(sport = Sport.RUN, startedAt = start, elapsedTime = 1200, createdAt = Clock.System.now()),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Clock.System.now(),
        )

    // Storing and reading zones

    @Test
    fun noRecordMeansNoZonesAndAnUntrimmedRoute() = runTest {
        val s = pds.session()
        assertEquals(RouteTrim.Known(PrivacySettings.NONE, null), s.privacy.forPublish().trim)

        val uri = s.activities.publish(draft())
        assertTrue(startOf(stored(uri.rkey!!).polyline) < 1)
    }

    @Test
    fun zonesAreSavedInTheSelfSpaceOnly() = runTest {
        pds.session().privacy.save(PrivacySettings(listOf(home), hideEndsMeters = 400))

        assertEquals(setOf(Visibility.SELF), pds.placesOf(Nsid.ACTOR_PRIVACY_ZONES, "self"))
        assertEquals(listOf(home), zonesRecord()?.zones)
        assertEquals(400, zonesRecord()?.hideEndsMeters)
        assertTrue(pds.stored[pds.repo].orEmpty().keys.none { it.startsWith(Nsid.ACTOR_PRIVACY_ZONES) })
    }

    @Test
    fun anotherDeviceTrimsByTheSavedZones() = runTest {
        pds.session().privacy.save(PrivacySettings(listOf(home)))

        val uri = pds.session().activities.publish(draft())
        assertTrue(startOf(stored(uri.rkey!!).polyline) >= 199)
    }

    @Test
    fun aRecordThatDoesntDecodeIsUnknown() = runTest {
        pds.stored.getOrPut(Visibility.SELF) { mutableMapOf() }["${Nsid.ACTOR_PRIVACY_ZONES}/self"] = buildJsonObject {
            put("zones", JsonPrimitive("home"))
        }

        assertIs<RouteTrim.Unknown>(pds.session().privacy.forPublish().trim)
    }

    @Test
    fun aFailedReadFallsBackToTheLastRead() = runTest {
        val s = pds.session()
        s.privacy.save(PrivacySettings(listOf(home)))
        pds.offline = true

        val trim = s.privacy.forPublish().trim
        assertEquals(listOf(home), assertIs<RouteTrim.Known>(trim).settings.zones)
    }

    @Test
    fun aSessionWithoutTheSpaceScopeDoesntKnowTheZones() = runTest {
        pds.session().privacy.save(PrivacySettings(listOf(home)))

        val old = pds.session(scope = "atproto transition:generic")
        val trim = old.privacy.forPublish()
        assertIs<RouteTrim.Unknown>(trim.trim, "zones saved by another session may exist, and this one can't read them")
        assertIs<ZonesRead.Unreadable>(old.privacy.refresh())

        // No track: writing one needs the self space this session can't use.
        val uri = old.activities.publish(draft().copy(gpx = null))
        old.activities.sync()
        assertNull(stored(uri.rkey!!).polyline, "never an untrimmed route")
    }

    @Test
    fun aPdsWithoutSpacesHasNoZones() = runTest {
        pds.spacesAlpha = null
        val s = pds.session()
        s.clients.resolveSpacesSupport()

        assertEquals(RouteTrim.Known(PrivacySettings.NONE, null), s.privacy.forPublish().trim)
        val uri = s.activities.publish(draft().copy(gpx = null))
        s.activities.sync()
        assertTrue(startOf(stored(uri.rkey!!).polyline) < 1, "nothing to hide: zones can't be stored on this PDS")
    }

    @Test
    fun aSaveOfflineFailsAndKeepsTheEarlierZones() = runTest {
        val s = pds.session()
        s.privacy.save(PrivacySettings(listOf(home)))
        pds.offline = true

        assertFails { s.privacy.save(PrivacySettings.NONE) }
        pds.offline = false
        assertEquals(listOf(home), assertIs<ZonesRead.Known>(s.privacy.refresh()).trim.settings.zones)
        val uri = s.activities.publish(draft())
        assertTrue(startOf(stored(uri.rkey!!).polyline) >= 199)
    }

    @Test
    fun aRadiusOutOfRangeIsClamped() = runTest {
        pds.stored.getOrPut(Visibility.SELF) { mutableMapOf() }["${Nsid.ACTOR_PRIVACY_ZONES}/self"] =
            AtJson.encodeToJsonElement(
                PrivacyZonesRecord.serializer(),
                PrivacyZonesRecord("3l2aaaaaaaa22", listOf(home.copy(radiusMeters = 5))),
            ) as JsonObject

        val zones = assertIs<RouteTrim.Known>(pds.session().privacy.forPublish().trim).settings.zones
        assertEquals(200, zones.single().radiusMeters)
    }

    @Test
    fun anAddedZoneIsOffsetAndThePointChosenIsNeverStored() = runTest {
        val s = pds.session()
        s.privacy.random = Random(7)
        s.privacy.add(latitude = 51.5, longitude = -0.12, radiusMeters = 400, label = "Home")

        val zone = zonesRecord()!!.zones!!.single()
        val distance = TrackMath.distanceMeters(51.5, -0.12, zone.latitudeE7 / 1e7, zone.longitudeE7 / 1e7)
        assertTrue(distance <= 200.0, "was $distance")
        assertTrue(distance > 0.0)
        assertFalse("515000000" in pds.stored.toString(), "the point chosen is in no record")
    }

    @Test
    fun anEleventhZoneIsRefused() = runTest {
        val s = pds.session()
        s.privacy.save(PrivacySettings(List(10) { home.copy(latitudeE7 = it * 1_000_000) }))

        assertFailsWith<TooManyZonesException> { s.privacy.add(10.0, 10.0, 400) }
        assertEquals(10, zonesRecord()!!.zones!!.size)
    }

    // Reading for the zones screen

    @Test
    fun zonesThatCantBeReadSayWhy() = runTest {
        val s = pds.session()
        // The fake PDS answers every request with a server error while offline.
        pds.offline = true

        val reason = assertIs<ZonesRead.Unreadable>(s.privacy.refresh()).reason
        assertEquals("InternalServerError", assertIs<DataError.Api>(reason).code)
        assertEquals(listOf(Outcome.Loading, Outcome.Error(reason, Origin.Network)), s.privacy.zonesOutcome().toList())
        assertFailsWith<ZonesUnreadableException> { s.privacy.add(10.0, 10.0, 400) }
    }

    @Test
    fun aRecordThatDoesntDecodeIsUnreadable() = runTest {
        pds.stored.getOrPut(Visibility.SELF) { mutableMapOf() }["${Nsid.ACTOR_PRIVACY_ZONES}/self"] = buildJsonObject {
            put("zones", JsonPrimitive("not a list"))
        }

        assertEquals(ZonesRead.Unreadable(DataError.Serialization), pds.session().privacy.refresh())
    }

    @Test
    fun aFailedReadAfterOneThisSessionAnswersTheEarlierZones() = runTest {
        val s = pds.session()
        s.privacy.save(PrivacySettings(listOf(home)))
        pds.offline = true

        assertEquals(listOf(home), assertIs<ZonesRead.Known>(s.privacy.refresh()).trim.settings.zones)
    }

    // Trimming on write

    @Test
    fun publicAndOnlyMeActivitiesAreTrimmed() = runTest {
        val s = pds.session()
        s.privacy.save(PrivacySettings(listOf(home)))

        val public = s.activities.publish(draft())
        val onlyMe = s.activities.publish(draft(VisibilityChoice(Visibility.SELF)))

        assertTrue(startOf(stored(public.rkey!!).polyline) >= 199)
        assertTrue(startOf(stored(onlyMe.rkey!!, Visibility.SELF).polyline) >= 199)
    }

    @Test
    fun trimmingLeavesTotalsAndTheFullTrack() = runTest {
        val s = pds.session()
        s.privacy.save(PrivacySettings(listOf(home), hideEndsMeters = 400))

        val uri = s.activities.publish(draft())
        val record = stored(uri.rkey!!)
        assertEquals(3336L, record.distance)
        val track = s.tracks.read(uri)
        assertEquals(route.size, track?.size, "the track in self keeps every point")
        assertEquals(0.0, track!!.first().longitude, 1e-9)
    }

    @Test
    fun theOwnRouteSaysWhenItCantBeReadRatherThanNone() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft())
        val other = AtUri.of(pds.did, Nsid.FEED_ACTIVITY, "3knotrack")

        assertEquals(route.size, s.activities.ownRouteOutcome(uri).last().dataOrNull()?.size)
        assertEquals(Outcome.Data(null, Origin.Network), s.activities.ownRouteOutcome(other).last(), "no track is an answer")
        pds.offline = true
        assertIs<Outcome.Error>(s.activities.ownRouteOutcome(uri).last(), "not taken for no track")
        assertNull(s.tracks.read(uri), "the read that never throws still answers null")
    }

    @Test
    fun aFullyHiddenRouteHasNoPolyline() = runTest {
        val s = pds.session()
        s.privacy.save(PrivacySettings(hideEndsMeters = 1600))

        // 2.8 km: shorter than the 3.2 km hidden from its two ends.
        val uri = s.activities.publish(draft(points = route.take(251)))
        assertNull(stored(uri.rkey!!).polyline)
    }

    @Test
    fun publishedBeforeTheZonesAreReadItHasNoPolylineUntilTheyAre() = runTest {
        pds.session().privacy.save(PrivacySettings(listOf(home)))
        val s = pds.session()
        pds.offline = true

        val uri = s.activities.publish(draft())
        pds.offline = false
        s.activities.sync()

        val polyline = stored(uri.rkey!!).polyline
        assertTrue(startOf(polyline) >= 199)
        assertTrue(pds.writes.count { it == "put ${pds.repo} ${Nsid.FEED_ACTIVITY}/${uri.rkey}" } >= 1)
    }

    @Test
    fun anEditAfterAQueuedReTrimKeepsTheTrimmedPolyline() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft())
        s.privacy.save(PrivacySettings(listOf(home)))
        // A server error, so the re-trim stays queued rather than rejected.
        pds.failPutsOf = Nsid.FEED_ACTIVITY
        pds.failPutsStatus = HttpStatusCode.InternalServerError
        s.activities.repairRoutes()
        pds.failPutsOf = null

        s.manager.edit(view(uri), ActivityEdit(title = "Renamed", description = "", diveSite = null, media = emptyList()))
        val record = stored(uri.rkey!!)
        assertEquals("Renamed", record.title)
        assertTrue(startOf(record.polyline) >= 199)
    }

    // Trimming again when zones change

    @Test
    fun addingAZoneReTrimsEveryVisibility() = runTest {
        val s = pds.session()
        val public = s.activities.publish(draft())
        val onlyMe = s.activities.publish(draft(VisibilityChoice(Visibility.SELF)))
        val before = stored(public.rkey!!)

        val revision = s.privacy.save(PrivacySettings(listOf(home))).let { zonesRecord()!!.revision }
        pds.session().activities.repairRoutes()

        val after = stored(public.rkey!!)
        assertTrue(startOf(after.polyline) >= 199)
        assertTrue(startOf(stored(onlyMe.rkey!!, Visibility.SELF).polyline) >= 199)
        assertEquals(before.copy(polyline = after.polyline), after, "only the polyline changes")
        assertEquals(revision, marker())
    }

    @Test
    fun removingAZoneRestoresTheRoute() = runTest {
        val s = pds.session()
        s.privacy.save(PrivacySettings(listOf(home)))
        val uri = s.activities.publish(draft())

        s.privacy.save(PrivacySettings.NONE)
        s.activities.repairRoutes()

        assertEquals(ActivityDraft.polylineOf(route)?.polyline, stored(uri.rkey!!).polyline)
    }

    @Test
    fun aSingleRouteBreaksWhereItLostItsPositionAfterTrimming() = runTest {
        // The run loses its position for 550 m and resumes 6 minutes later.
        val gapped = route.take(150) + route.drop(200).map { it.copy(time = it.time + 6.minutes) }
        val s = pds.session()
        s.privacy.save(PrivacySettings(listOf(home)))
        val uri = s.activities.publish(draft(points = gapped))
        assertEquals(1, stored(uri.rkey!!).polylineGaps?.size, "trimmed at home, then broken at the gap")

        s.privacy.save(PrivacySettings.NONE)
        s.activities.repairRoutes()
        assertEquals(ActivityDraft.polylineOf(gapped)?.gaps, stored(uri.rkey!!).polylineGaps)
        assertEquals(1, stored(uri.rkey!!).polylineGaps?.size)
    }

    @Test
    fun aManualEntryIsNotRewritten() = runTest {
        val s = pds.session()
        val manual = s.activities.publish(draft(points = emptyList()).copy(gpx = null))
        val writesBefore = pds.writes.count { it.endsWith("${Nsid.FEED_ACTIVITY}/${manual.rkey}") }

        s.privacy.save(PrivacySettings(listOf(home)))
        s.activities.repairRoutes()

        assertEquals(writesBefore, pds.writes.count { it.endsWith("${Nsid.FEED_ACTIVITY}/${manual.rkey}") })
        assertNotNull(marker())
    }

    @Test
    fun anInterruptedReTrimFinishesInALaterSession() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft())
        s.privacy.save(PrivacySettings(listOf(home)))

        // Refused for good (data/outbox): the rewrite is kept as rejected, and a rejected rewrite isn't a sent one.
        pds.failPutsOf = Nsid.FEED_ACTIVITY
        s.activities.repairRoutes()
        assertNull(marker(), "not marked done while a rewrite is unsent")
        assertNotNull(s.db.outbox().pending(pds.did.value).single { it.collection == Nsid.FEED_ACTIVITY }.rejectedAt)
        pds.failPutsOf = null

        val later = pds.session()
        later.activities.repairRoutes()
        assertTrue(startOf(stored(uri.rkey!!).polyline) >= 199)
        assertEquals(zonesRecord()!!.revision, marker())
    }

    @Test
    fun aFinishedReTrimIsntRunAgain() = runTest {
        val s = pds.session()
        s.activities.publish(draft())
        s.privacy.save(PrivacySettings(listOf(home)))
        s.activities.repairRoutes()
        val writes = pds.writes.size

        pds.session().activities.repairRoutes()
        assertEquals(writes, pds.writes.size)
    }

    private suspend fun assertFails(block: suspend () -> Unit) {
        val failed = runCatching { block() }.isFailure
        assertTrue(failed, "expected a failure")
    }
}
