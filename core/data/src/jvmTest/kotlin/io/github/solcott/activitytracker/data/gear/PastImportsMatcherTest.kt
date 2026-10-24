package io.github.solcott.activitytracker.data.gear

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.activityfile.ImportedDevice
import io.github.solcott.activitytracker.activityfile.ImportedDevices
import io.github.solcott.activitytracker.activityfile.SensorKind
import io.github.solcott.activitytracker.data.FakeAthletePds
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityPlaces
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.solcott.activitytracker.lexicon.app.GearMount
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SensorKey
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject

/** Applying a new sensor link to earlier imports (gear/gear-sensors). */
class PastImportsMatcherTest {
    private val pds = FakeAthletePds()
    private val did = pds.did
    private val created = Instant.parse("2026-01-01T00:00:00Z")
    private val firstRide = Instant.parse("2026-04-01T08:00:00Z")

    private fun gearUri(rkey: String) = GearItem.uriOf(did, rkey)

    private val tarmac = gearUri("3tarmac")
    private val stumpjumper = gearUri("3stumpy")
    private val crank = gearUri("3crank")
    private val key = SensorKey(manufacturer = "SRAM", serialNumber = "3236233426")

    private val withMeter =
        ImportedDevices(
            listOf(
                ImportedDevice(recorder = true, manufacturer = "Garmin", model = "Edge 840", serialNumber = "3510682071"),
                ImportedDevice(
                    recorder = false,
                    kind = SensorKind.Power,
                    manufacturer = "SRAM",
                    model = "1052",
                    serialNumber = "3236233426",
                ),
            )
        )
    private val withoutMeter = ImportedDevices(listOf(ImportedDevice(recorder = true, manufacturer = "Garmin", model = "Fenix 7")))

    private fun storeGear(rkey: String, record: GearItemRecord, place: String = pds.repo) {
        pds.stored.getOrPut(place) { mutableMapOf() }["${Nsid.GEAR_ITEM}/$rkey"] =
            AtJson.encodeToJsonElement(GearItemRecord.serializer(), record) as JsonObject
    }

    private fun storeAllGear() {
        storeGear("3tarmac", GearItemRecord("Tarmac", GearType.BIKE, created))
        storeGear("3stumpy", GearItemRecord("Stumpjumper", GearType.BIKE, created))
        storeGear(
            "3crank",
            GearItemRecord(
                "SRAM Power meter",
                GearType.COMPONENT,
                created,
                kind = "powerMeter",
                mounts = listOf(GearMount(tarmac, created)),
            ),
            Visibility.SELF,
        )
    }

    private fun draft(day: Int, devices: ImportedDevices, gear: List<AtUri> = emptyList()) =
        ActivityDraft(
                type = ActivityType(Sport.RIDE),
                title = "Ride $day",
                description = null,
                startedAt = firstRide + day.days,
                elapsed = 30.minutes,
                devices = devices,
                gear = gear,
            )
            .withVisibility(VisibilityChoice.PUBLIC)

    /** What the AppView serves for [uri], built from its repo record. */
    private fun serve(uri: AtUri) {
        val record =
            AtJson.decodeFromJsonElement(
                ActivityRecord.serializer(),
                pds.stored.getValue(pds.repo).getValue("${Nsid.FEED_ACTIVITY}/${uri.rkey}"),
            )
        pds.servedActivities[uri.value] =
            ActivityView(
                uri = uri,
                cid = Cid("bafyrec"),
                author = ProfileViewBasic(did, Handle("alice.test")),
                record = record,
                likeCount = 0,
                commentCount = 0,
                indexedAt = Clock.System.now(),
            )
    }

    /** The gear [uri] lists now, from every place it's stored. */
    private fun gearOf(uri: AtUri): List<AtUri> {
        val details = pds.stored[Visibility.SELF]?.get("${Nsid.FEED_ACTIVITY_DETAILS}/${uri.rkey}")
        val record = pds.stored.getValue(pds.repo).getValue("${Nsid.FEED_ACTIVITY}/${uri.rkey}")
        return details?.let { AtJson.decodeFromJsonElement(ActivityDetailsRecord.serializer(), it).gear }
            ?: AtJson.decodeFromJsonElement(ActivityRecord.serializer(), record).gear.orEmpty()
    }

    private suspend fun setUp(): Pair<FakeAthletePds.Session, List<AtUri>> {
        storeAllGear()
        val session = pds.session()
        // Twelve rides name the power meter; two of them already list a bike. One more ride doesn't name it.
        val rides =
            (0 until 12).map { day ->
                session.activities.publish(draft(day, withMeter, gear = if (day < 2) listOf(stumpjumper) else emptyList()))
            }
        session.activities.publish(draft(20, withoutMeter))
        rides.forEach(::serve)
        return session to rides
    }

    private fun matcher(session: FakeAthletePds.Session) =
        PastImportsMatcher(
            session.devices,
            ActivityPlaces(session.clients, Clock.System, Logger),
            GearRepository(session.clients, Clock.System, session.feed, GearCollections(session.clients, Clock.System), Logger),
            session.feed,
            session.manager,
        )

    @Test
    fun aCrankOnTheTarmacIsAddedToTheTenRidesWithoutABike() = runTest {
        val (session, rides) = setUp()
        val matcher = matcher(session)

        val found = matcher.find(key, crank)
        assertEquals(rides.drop(2), found.map { it.activity }, "ten rides, not the two with a bike or the one without the meter")
        assertEquals(setOf(tarmac), found.map { it.gear }.toSet(), "the bike the crank was on, not the crank")

        val progress = mutableListOf<Int>()
        assertEquals(10, matcher.apply(found) { progress += it })
        assertEquals((1..10).toList(), progress)
        rides.drop(2).forEach { assertEquals(listOf(tarmac), gearOf(it), "$it") }
        rides.take(2).forEach { assertEquals(listOf(stumpjumper), gearOf(it), "$it is unchanged") }
        assertEquals(emptyList(), matcher.find(key, crank), "nothing is left to add")
    }

    @Test
    fun aResumedPassSkipsWhatTheFirstOneDid() = runTest {
        val (session, _) = setUp()
        val matcher = matcher(session)
        val found = matcher.find(key, crank)

        // The first pass stopped after four rides.
        assertEquals(4, matcher.apply(found.take(4)))
        assertEquals(6, matcher.apply(found), "running it again changes only the other six")
        found.forEach { assertEquals(listOf(tarmac), gearOf(it.activity)) }
    }

    @Test
    fun theImportJustSavedIsLeftOut() = runTest {
        val (session, rides) = setUp()
        assertEquals(9, matcher(session).find(key, crank, except = setOf(rides.last())).size)
    }
}
