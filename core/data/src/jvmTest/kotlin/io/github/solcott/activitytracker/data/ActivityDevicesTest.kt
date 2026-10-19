package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.activityfile.ActivityFileFormat
import io.github.solcott.activitytracker.activityfile.ImportedActivity
import io.github.solcott.activitytracker.activityfile.ImportedDevice
import io.github.solcott.activitytracker.activityfile.ImportedDevices
import io.github.solcott.activitytracker.activityfile.ImportedFile
import io.github.solcott.activitytracker.activityfile.SensorBattery
import io.github.solcott.activitytracker.activityfile.SensorKind
import io.github.solcott.activitytracker.activityfile.SourceFile
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.DraftSource
import io.github.solcott.activitytracker.data.activity.OwnActivity
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityDevice
import io.github.solcott.activitytracker.lexicon.app.ActivityDevicesRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.DeviceBattery
import io.github.solcott.activitytracker.lexicon.app.DeviceConnection
import io.github.solcott.activitytracker.lexicon.app.DeviceRole
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.RecordingView
import io.github.solcott.activitytracker.lexicon.app.SensorType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * An imported activity's devices (`feed/activity-devices`): a `feed.activityDevices` record in the athlete's `self` space whatever the
 * activity's visibility, written, merged and deleted with it, and skipped by a session that can't write it.
 */
class ActivityDevicesTest {
    private val pds = FakeAthletePds()
    private val did = pds.did
    private val start = Instant.parse("2026-10-18T15:00:00Z")
    private val fit = byteArrayOf(14, 16, 0, 0, 0, 0, 0, 0) + ".FIT".encodeToByteArray() + ByteArray(64) { it.toByte() }

    private val edge =
        ImportedDevices(
            listOf(
                ImportedDevice(recorder = true, manufacturer = "Garmin", model = "Edge 840", serialNumber = "3510682071"),
                ImportedDevice(
                    recorder = false,
                    kind = SensorKind.Power,
                    manufacturer = "SRAM",
                    model = "1052",
                    serialNumber = "3236233426",
                    connection = DeviceConnection.ANT_PLUS,
                    battery = SensorBattery.Low,
                    batteryMillivolts = 1574,
                    operatingSeconds = 2_221_408,
                ),
            ),
            activityProfile = "MOUNTAIN",
        )
    private val fenix = ImportedDevices(listOf(ImportedDevice(recorder = true, manufacturer = "Garmin", model = "Fenix 7")))

    private suspend fun sourceFile() =
        requireNotNull(
            SourceFile.prepare(fit, "Morning_Ride.fit", ImportedFile(ActivityFileFormat.FIT, gzipped = false, activities = emptyList()))
        )

    private suspend fun draft(
        devices: ImportedDevices? = edge,
        source: Boolean = true,
        choice: VisibilityChoice = VisibilityChoice.PUBLIC,
    ) =
        ActivityDraft(
                type = ActivityType(Sport.RIDE),
                title = "Ride",
                description = null,
                startedAt = start,
                elapsed = 30.minutes,
                source = if (source) DraftSource(sourceFile()) else null,
                devices = devices,
            )
            .withVisibility(choice)

    private fun devicesOf(rkey: String) = pds.placesOf(Nsid.FEED_ACTIVITY_DEVICES, rkey)

    private fun storedDevices(rkey: String) =
        AtJson.decodeFromJsonElement(
            ActivityDevicesRecord.serializer(),
            pds.stored.getValue(Visibility.SELF).getValue("${Nsid.FEED_ACTIVITY_DEVICES}/$rkey"),
        )

    private fun view(uri: AtUri, recordings: List<AtUri> = emptyList()) =
        ActivityView(
            uri = uri,
            cid = Cid("bafyrec"),
            author = ProfileViewBasic(did, Handle("alice.test")),
            record = ActivityRecord(sport = Sport.RIDE, startedAt = start, elapsedTime = 1800, createdAt = Clock.System.now()),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Clock.System.now(),
            recordings = recordings.map { RecordingView(it, start) }.ifEmpty { null },
        )

    private fun ownRecord(uri: AtUri): ActivityRecord =
        AtJson.decodeFromJsonElement(
            ActivityRecord.serializer(),
            pds.stored.getValue(pds.repo).getValue("${Nsid.FEED_ACTIVITY}/${uri.rkey}"),
        )

    @Test
    fun aPublicImportsDevicesGoToTheSelfSpaceAfterItsActivityAndSource() = runTest {
        val uri = pds.session().activities.publish(draft())
        val rkey = uri.rkey!!

        assertEquals(setOf(Visibility.SELF), devicesOf(rkey), "never in the repo")
        assertFalse("3510682071" in pds.stored.getValue(pds.repo).toString(), "no serial number is in the repo")
        val record = storedDevices(rkey)
        assertEquals(uri, record.activity)
        assertEquals("MOUNTAIN", record.activityProfile)
        assertEquals(
            listOf(
                ActivityDevice(role = DeviceRole.RECORDER, manufacturer = "Garmin", model = "Edge 840", serialNumber = "3510682071"),
                ActivityDevice(
                    role = DeviceRole.SENSOR,
                    kind = SensorType.POWER,
                    manufacturer = "SRAM",
                    model = "1052",
                    serialNumber = "3236233426",
                    connection = DeviceConnection.ANT_PLUS,
                    battery = DeviceBattery.LOW,
                    batteryVoltage = 1574,
                    operatingTime = 2_221_408,
                ),
            ),
            record.devices,
        )
        val order = pds.writes.filter { it.startsWith("put ") }.map { it.substringAfterLast(' ').substringBefore('/') }
        assertEquals(
            listOf(Nsid.FEED_ACTIVITY, Nsid.FEED_ACTIVITY_SOURCE, Nsid.FEED_ACTIVITY_DEVICES),
            order.filter { it.startsWith("${Nsid.NAMESPACE}.feed") },
        )
    }

    @Test
    fun aDraftFromAnImportCarriesItsDevices() = runTest {
        val imported =
            ImportedActivity(
                type = ActivityType(Sport.RIDE),
                startedAt = start,
                elapsed = 30.minutes,
                movingSeconds = null,
                distanceMeters = null,
                elevationGainMeters = null,
                maxSpeedMps = null,
                avgHeartRate = null,
                maxHeartRate = null,
                avgTemperatureCelsius = null,
                device = "Garmin Edge 840",
                samples = emptyList(),
                devices = edge,
            )
        val draft = ActivityDraft.fromImport(imported, imported.type, "Ride")
        assertEquals(edge, draft.devices)

        val uri = pds.session().activities.publish(draft)
        assertEquals(setOf(Visibility.SELF), devicesOf(uri.rkey!!))
    }

    @Test
    fun aFilesOutOfBoundsValuesAreHeldToTheLexicon() = runTest {
        val odd =
            ImportedDevices(
                listOf(
                    ImportedDevice(
                        recorder = false,
                        kind = SensorKind.Speed,
                        manufacturer = "M".repeat(300),
                        serialNumber = "9".repeat(100),
                        antDeviceNumber = 70_000,
                        antTransmissionType = 300,
                        batteryPercent = 140,
                        batteryMillivolts = -1,
                        operatingSeconds = -5,
                    )
                ),
                activityProfile = "P".repeat(200),
            )
        val uri = pds.session().activities.publish(draft(devices = odd, source = false))

        val record = storedDevices(uri.rkey!!)
        assertEquals(ActivityDevice.MAX_NAME, record.activityProfile?.length)
        assertEquals(
            ActivityDevice(
                role = DeviceRole.SENSOR,
                kind = SensorType.SPEED,
                manufacturer = "M".repeat(ActivityDevice.MAX_NAME),
                serialNumber = "9".repeat(ActivityDevice.MAX_IDENTIFIER),
            ),
            record.devices.single(),
        )
    }

    @Test
    fun anOfflineImportSendsItsDevicesAfterReconnecting() = runTest {
        val s = pds.session()
        pds.offline = true
        val uri = s.activities.publish(draft())
        assertEquals(3, s.activities.pendingWrites.first(), "the activity, its source and its devices wait together")
        assertTrue(s.devices.has(uri), "queued devices count")

        pds.offline = false
        assertEquals(0, s.activities.sync())

        assertEquals(setOf(Visibility.SELF), devicesOf(uri.rkey!!))
        assertTrue(s.devices.has(uri), "stored devices count")
    }

    @Test
    fun anImportWithoutItsOriginalStillKeepsItsDevices() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft(source = false))

        assertEquals(setOf(Visibility.SELF), devicesOf(uri.rkey!!))
    }

    @Test
    fun anActivityWithoutDevicesWritesNone() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft(devices = null, source = false))

        assertEquals(emptySet(), devicesOf(uri.rkey!!))
        assertFalse(s.devices.has(uri))
    }

    @Test
    fun aSessionThatCantWriteDevicesPublishesWithoutThem() = runTest {
        val s =
            pds.session(
                scope =
                    "atproto transition:generic space:${Nsid.SPACE_ATHLETE}?collection=${Nsid.FEED_ACTIVITY}" +
                        "&collection=${Nsid.FEED_ACTIVITY_STREAMS}&collection=${Nsid.FEED_ACTIVITY_SOURCE}&collection=${Nsid.FEED_ACTIVITY_TRACK}"
            )
        val uri = s.activities.publish(draft())

        assertEquals(setOf(pds.repo), pds.placesOf(Nsid.FEED_ACTIVITY, uri.rkey!!))
        assertEquals(setOf(Visibility.SELF), pds.placesOf(Nsid.FEED_ACTIVITY_SOURCE, uri.rkey!!), "the source is still kept")
        assertEquals(emptySet(), devicesOf(uri.rkey!!))
        s.manager.delete(view(uri), withRecordings = false)
        assertTrue(pds.writes.none { Nsid.FEED_ACTIVITY_DEVICES in it }, "nor is a delete queued for the PDS to refuse")
    }

    @Test
    fun mergingIntoAnAppRecordingAddsTheImportsDevices() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft(devices = null, source = false))

        s.activities.mergeInto(OwnActivity(uri, Cid("bafyrec"), ownRecord(uri)), draft())
        assertEquals(0, s.activities.sync())

        assertEquals("3510682071", storedDevices(uri.rkey!!).devices.first().serialNumber)
    }

    @Test
    fun mergingKeepsDevicesTheActivityHas() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft(devices = fenix, source = false))
        val writesBefore = pds.writes.size

        s.activities.mergeInto(OwnActivity(uri, Cid("bafyrec"), ownRecord(uri)), draft())
        assertEquals(0, s.activities.sync())

        assertTrue(pds.writes.drop(writesBefore).none { Nsid.FEED_ACTIVITY_DEVICES in it }, "the stored devices aren't replaced")
        assertEquals("Fenix 7", storedDevices(uri.rkey!!).devices.single().model)
    }

    @Test
    fun aGroupedRecordingKeepsItsOwnDevices() = runTest {
        val s = pds.session()
        val main = s.activities.publish(draft(devices = fenix, source = false))
        val recording = s.activities.publish(draft().copy(recordingOf = main))

        assertEquals("Fenix 7", storedDevices(main.rkey!!).devices.single().model)
        assertEquals("Edge 840", storedDevices(recording.rkey!!).devices.first().model)
    }

    @Test
    fun aVisibilityChangeLeavesTheDevicesWhereTheyAre() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft())
        val before = storedDevices(uri.rkey!!)
        val writesBefore = pds.writes.size

        s.manager.setVisibility(view(uri), VisibilityChoice(Visibility.SELF))

        assertEquals(setOf(Visibility.SELF), devicesOf(uri.rkey!!))
        assertEquals(before, storedDevices(uri.rkey!!))
        assertTrue(pds.writes.drop(writesBefore).none { Nsid.FEED_ACTIVITY_DEVICES in it }, "the devices aren't touched")
    }

    @Test
    fun deletingAnActivityDeletesItsDevices() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft())

        s.manager.delete(view(uri), withRecordings = false)

        assertEquals(emptySet(), devicesOf(uri.rkey!!))
    }

    @Test
    fun deletingWithRecordingsDeletesEveryRecordingsDevices() = runTest {
        val s = pds.session()
        val main = s.activities.publish(draft())
        val recording = s.activities.publish(draft())

        s.manager.delete(view(main, recordings = listOf(recording)), withRecordings = true)

        assertEquals(emptySet(), devicesOf(main.rkey!!))
        assertEquals(emptySet(), devicesOf(recording.rkey!!))
    }

    @Test
    fun deletingOnlyTheMainActivityKeepsItsRecordingsDevices() = runTest {
        val s = pds.session()
        val main = s.activities.publish(draft())
        val recording = s.activities.publish(draft())

        s.manager.delete(view(main, recordings = listOf(recording)), withRecordings = false)

        assertEquals(emptySet(), devicesOf(main.rkey!!))
        assertEquals(setOf(Visibility.SELF), devicesOf(recording.rkey!!))
    }
}
