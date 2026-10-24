package io.github.solcott.activitytracker.data.gear

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ActivityDevice
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.DeviceRole
import io.github.solcott.activitytracker.lexicon.app.GearAutoAssignRecord
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import io.github.solcott.activitytracker.lexicon.app.GearMount
import io.github.solcott.activitytracker.lexicon.app.GearProfileLink
import io.github.solcott.activitytracker.lexicon.app.GearSensorLink
import io.github.solcott.activitytracker.lexicon.app.GearSportDefault
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.SensorKey
import io.github.solcott.activitytracker.lexicon.app.SensorType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** How an imported activity's gear is filled in from its devices, its profile and the defaults (gear/gear-sensors). */
class GearAutoAssignTest {
    private val did = Did("did:plc:alice")
    private val created = Instant.parse("2026-01-01T00:00:00Z")

    private fun day(date: String) = Instant.parse("${date}T08:00:00Z")

    private fun item(rkey: String, name: String, type: String, kind: String? = null, mounts: List<GearMount>? = null) =
        GearItem(rkey, GearItemRecord(name, type, created, kind = kind, mounts = mounts), Visibility.SELF)

    private fun uri(item: GearItem): AtUri = item.uri(did)

    private val tarmac = item("3tarmac", "Tarmac", GearType.BIKE)
    private val stumpjumper = item("3stumpy", "Stumpjumper", GearType.BIKE)
    private val helmet = item("3helmet", "Helmet", GearType.HELMET)
    private val strap = item("3strap", "Chest strap", GearType.SENSOR, "heartRate")

    // Mounted on the Tarmac from March, moved to the Stumpjumper in June.
    private val crank =
        item(
            "3crank",
            "SRAM Power meter",
            GearType.COMPONENT,
            "powerMeter",
            listOf(GearMount(uri(tarmac), day("2026-03-01"), day("2026-06-01")), GearMount(uri(stumpjumper), day("2026-06-01"))),
        )
    private val stumpyCrank =
        item("3scrank", "Stumpy crank", GearType.COMPONENT, "powerMeter", listOf(GearMount(uri(stumpjumper), day("2026-01-01"))))
    private val pedals = item("3pedals", "Power pedals", GearType.COMPONENT, "pedals", listOf(GearMount(uri(tarmac), day("2026-01-01"))))
    private val speed = item("3speed", "Speed sensor", GearType.SENSOR, "speed", listOf(GearMount(uri(stumpjumper), day("2026-01-01"))))
    private val items = listOf(tarmac, stumpjumper, helmet, strap, crank, stumpyCrank, pedals, speed)

    private val meterKey = SensorKey(manufacturer = "SRAM", serialNumber = "3236233426")
    private val strapKey = SensorKey(manufacturer = "Garmin", model = "HRM 200")
    private val meter =
        ActivityDevice(
            role = DeviceRole.SENSOR,
            kind = SensorType.POWER,
            manufacturer = "SRAM",
            model = "1052",
            serialNumber = "3236233426",
        )
    private val hrm = ActivityDevice(role = DeviceRole.SENSOR, kind = SensorType.HEART_RATE, manufacturer = "Garmin", model = "HRM 200")
    private val edge = ActivityDevice(role = DeviceRole.RECORDER, manufacturer = "Garmin", model = "Edge 840", serialNumber = "3510682071")
    private val speedDevice =
        ActivityDevice(role = DeviceRole.SENSOR, kind = SensorType.SPEED, manufacturer = "Garmin", model = "Speed 2", serialNumber = "77")

    private val ride = ActivityType(Sport.RIDE)
    private val rideDefaults = GearSportDefault(Sport.RIDE, listOf(uri(tarmac), uri(helmet)))

    private fun record(vararg links: GearSensorLink, profiles: List<GearProfileLink>? = null, ignored: List<SensorKey>? = null) =
        GearAutoAssignRecord(created, sports = listOf(rideDefaults), sensors = links.toList(), profiles = profiles, ignored = ignored)

    @Suppress("LongParameterList") // What each scenario varies, with defaults.
    private fun resolve(
        record: GearAutoAssignRecord,
        devices: List<ActivityDevice>,
        at: Instant = day("2026-04-15"),
        profile: String? = null,
        type: ActivityType = ride,
        gear: List<GearItem> = items,
    ) = GearAutoAssign.resolve(type, at, devices, profile, gear, record, did)

    @Test
    fun aLinkedCrankBeatsTheSportDefault() {
        val resolved = resolve(record(GearSensorLink(meterKey, uri(stumpyCrank))), listOf(edge, meter))
        assertEquals(listOf(uri(stumpjumper), uri(helmet)), resolved.gear, "the Stumpjumper, not the Tarmac, and the default helmet")
        assertEquals(emptyMap(), resolved.conflicts)
    }

    @Test
    fun aProfileFillsInWhenNoSensorMatches() {
        val links = record(profiles = listOf(GearProfileLink("MOUNTAIN", uri(stumpjumper))))
        assertEquals(listOf(uri(stumpjumper), uri(helmet)), resolve(links, listOf(edge, meter), profile = "MOUNTAIN").gear)
        assertEquals(listOf(uri(tarmac), uri(helmet)), resolve(links, listOf(edge), profile = "ROAD").gear, "another profile")
    }

    @Test
    fun aSensorBeatsTheProfileForItsGearType() {
        val links = record(GearSensorLink(meterKey, uri(stumpyCrank)), profiles = listOf(GearProfileLink("ROAD", uri(tarmac))))
        assertEquals(listOf(uri(stumpjumper), uri(helmet)), resolve(links, listOf(meter), profile = "ROAD").gear)
    }

    @Test
    fun aMountedCrankBringsTheBikeItWasOnThen() {
        val links = record(GearSensorLink(meterKey, uri(crank)))
        assertEquals(uri(tarmac), resolve(links, listOf(meter), at = day("2026-04-15")).gear.first(), "on the Tarmac since March")
        assertEquals(uri(tarmac), resolve(links, listOf(meter), at = day("2026-05-20")).gear.first(), "moved only in June")
        assertEquals(uri(stumpjumper), resolve(links, listOf(meter), at = day("2026-07-01")).gear.first())
        assertEquals(uri(crank), resolve(links, listOf(meter), at = day("2026-02-01")).gear.first(), "before it was mounted")
    }

    @Test
    fun anUnmountedStrapIsAddedItself() {
        val run = ActivityType(Sport.RUN)
        assertEquals(listOf(uri(strap)), resolve(record(GearSensorLink(strapKey, uri(strap))), listOf(hrm), type = run).gear)
    }

    @Test
    fun sensorsPointingAtTwoBikesAreBothKeptAndFlagged() {
        val links =
            record(
                GearSensorLink(meterKey, uri(pedals)),
                GearSensorLink(SensorKey(manufacturer = "Garmin", serialNumber = "77"), uri(speed)),
            )
        val resolved = resolve(links, listOf(meter, speedDevice))
        assertEquals(listOf(uri(tarmac), uri(stumpjumper), uri(helmet)), resolved.gear)
        assertEquals(mapOf(GearType.BIKE to listOf(uri(tarmac), uri(stumpjumper))), resolved.conflicts)
    }

    @Test
    fun retiredItemsAreSkipped() {
        val retiredStumpy = stumpjumper.copy(record = stumpjumper.record.copy(retiredAt = created))
        val gear = items - stumpjumper + retiredStumpy
        val links = record(GearSensorLink(meterKey, uri(stumpyCrank)))
        assertEquals(listOf(uri(tarmac), uri(helmet)), resolve(links, listOf(meter), gear = gear).gear, "falls back to the default")
    }

    @Test
    fun aDeletedLinkedItemIsSkipped() {
        val links = record(GearSensorLink(meterKey, AtUri("at://${did.value}/app.activitytracker.gear.item/3gone")))
        assertEquals(listOf(uri(tarmac), uri(helmet)), resolve(links, listOf(meter)).gear)
    }

    @Test
    fun unlinkedDevicesSkipLinkedIgnoredAndUnidentifiedOnes() {
        val links =
            record(GearSensorLink(meterKey, uri(crank)), ignored = listOf(SensorKey(manufacturer = "garmin", serialNumber = "3510682071")))
        val bare = ActivityDevice(role = DeviceRole.SENSOR, kind = SensorType.CADENCE)
        val unlinked = GearAutoAssign.unlinked(listOf(edge, meter, hrm, hrm, bare), links)
        assertEquals(listOf(strapKey), unlinked.map { it.key }, "the HRM once; the Edge is ignored and the bare sensor has no key")
    }
}
