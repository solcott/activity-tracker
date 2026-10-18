package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.activityfile.ActivityFileFormat
import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.DiveGas
import io.github.solcott.activitytracker.activityfile.GarminJavaFitDecoder
import io.github.solcott.activitytracker.activityfile.ImportedFile
import io.github.solcott.activitytracker.activityfile.SourceFile
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityExporter
import io.github.solcott.activitytracker.data.activity.DraftSource
import io.github.solcott.activitytracker.data.activity.ExportFormat
import io.github.solcott.activitytracker.data.activity.ExportUnreadableException
import io.github.solcott.activitytracker.data.activity.NotSentYetException
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveGas
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GasSwitch
import io.github.solcott.activitytracker.lexicon.app.GearChange
import io.github.solcott.activitytracker.lexicon.app.Lap
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import io.github.solcott.activitytracker.lexicon.app.TrainingLoad
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.Gpx
import io.github.solcott.activitytracker.location.LocationSample
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone

/** Exporting the athlete's own activity (`feed/activity-export`): rebuilt from its record, streams and full track. */
class ActivityExporterTest {
    private val pds = FakeAthletePds()
    private val start = Instant.parse("2026-10-14T07:00:00Z")
    private val route = (0..2).map { LocationSample(listOf(47.6, 47.6001, 47.6002)[it], -122.3, 50.0, null, 5.0, start + it.seconds) }

    private fun draft(type: ActivityType = ActivityType(Sport.RUN), title: String? = "Morning Run", gpx: Boolean = true) =
        ActivityDraft(
            type = type,
            title = title,
            description = null,
            startedAt = start,
            elapsed = 30.minutes,
            distanceMeters = 5000.0,
            route = if (gpx) route else emptyList(),
            gpx = if (gpx) Gpx.write("Run", Sport.RUN, route).encodeToByteArray() else null,
        )

    private fun view(uri: AtUri, sport: String = Sport.RUN, title: String? = "Morning Run") =
        ActivityView(
            uri = uri,
            cid = Cid("bafyrec"),
            author = ProfileViewBasic(pds.did, Handle("alice.test")),
            record =
                ActivityRecord(
                    sport = sport,
                    startedAt = start,
                    elapsedTime = 1800,
                    createdAt = Clock.System.now(),
                    title = title,
                    distance = 5000,
                    avgHeartRate = 150,
                ),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Clock.System.now(),
        )

    private fun serveStreams(uri: AtUri, data: ActivityStreamData) {
        pds.streamsVisibility[uri.rkey!!] = Visibility.PUBLIC
        pds.streamsData[uri.rkey!!] = data
    }

    @Test
    fun formatsFollowTheDataStored() {
        val all = setOf(ExportFormat.GPX, ExportFormat.TCX, ExportFormat.FIT)
        assertEquals(all, ActivityExporter.formats(route, hasStreams = true, isDive = false))
        assertEquals(all, ActivityExporter.formats(route, hasStreams = false, isDive = false))
        assertEquals(setOf(ExportFormat.TCX, ExportFormat.FIT), ActivityExporter.formats(null, hasStreams = true, isDive = false))
        assertEquals(setOf(ExportFormat.TCX, ExportFormat.FIT), ActivityExporter.formats(route.take(1), hasStreams = false, isDive = false))
        assertEquals(emptySet(), ActivityExporter.formats(null, hasStreams = false, isDive = false))
    }

    @Test
    fun aDiveAlsoOffersUddfWheneverItOffersTcx() {
        assertEquals(ExportFormat.entries.toSet(), ActivityExporter.formats(route.take(2), hasStreams = true, isDive = true))
        assertEquals(
            setOf(ExportFormat.TCX, ExportFormat.FIT, ExportFormat.UDDF),
            ActivityExporter.formats(null, hasStreams = true, isDive = true),
        )
        assertEquals(emptySet(), ActivityExporter.formats(null, hasStreams = false, isDive = true))
        assertFalse(ExportFormat.UDDF in ActivityExporter.formats(route, hasStreams = true, isDive = false), "a run never offers it")
    }

    @Test
    fun aDiveExportsAsUddf() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft(ActivityType(Sport.DIVE), title = "Blue Hole", gpx = false))
        val switches = listOf(GasSwitch(0, 21, 0), GasSwitch(60, 50, 0))
        serveStreams(uri, ActivityStreamData(time = listOf(0, 60, 120), depth = listOf(0, 1800, 0), gasSwitches = switches))
        val dive =
            view(uri, Sport.DIVE, title = "Blue Hole").let {
                it.copy(
                    record =
                        it.record.copy(
                            distance = null,
                            device = "Shearwater Perdix 2",
                            dive = ActivityDive(maxDepth = 1800, diveNumber = 7, gases = listOf(ActivityDiveGas(21, 0))),
                        )
                )
            }

        val file = s.exporter.export(dive, ExportFormat.UDDF)
        assertEquals("application/xml", file.mimeType)
        assertTrue(file.name.endsWith(".uddf"), file.name)
        val read = ActivityFileReader { error("not FIT") }.read(file.bytes).single()
        assertEquals(Sport.DIVE, read.type.sport)
        assertEquals("Shearwater Perdix 2", read.device)
        assertEquals(listOf(0, 1800, 0), read.streams?.depth)
        assertEquals(switches, read.streams?.gasSwitches)
        assertEquals(7, read.dive?.diveNumber)
    }

    @Test
    fun aRunCantBeExportedAsUddf() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft())
        serveStreams(uri, ActivityStreamData(time = listOf(0, 1, 2), heartRate = listOf(140, 141, 142)))

        assertFailsWith<ExportUnreadableException> { s.exporter.export(view(uri), ExportFormat.UDDF) }
    }

    @Test
    fun anUnsentActivityIsRefused() = runTest {
        val s = pds.session()
        pds.offline = true
        val uri = s.activities.publish(draft())

        assertFailsWith<NotSentYetException> { s.exporter.export(view(uri), ExportFormat.GPX) }
    }

    @Test
    fun anUnreadableTrackFailsRatherThanWriteAThinnerFile() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft())
        serveStreams(uri, ActivityStreamData(time = listOf(0, 1, 2), heartRate = listOf(140, 141, 142)))
        pds.offline = true

        assertFailsWith<ExportUnreadableException> { s.exporter.export(view(uri), ExportFormat.TCX) }
    }

    @Test
    fun unreadableStreamsFailToo() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft())
        pds.failStreams = true

        assertFailsWith<ExportUnreadableException> { s.exporter.export(view(uri), ExportFormat.GPX) }
    }

    @Test
    fun aGpsRunJoinsItsFullTrackAndStreams() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft())
        serveStreams(uri, ActivityStreamData(time = listOf(0, 1, 2), heartRate = listOf(140, 141, 142)))

        val gpx = s.exporter.export(view(uri), ExportFormat.GPX)
        val text = gpx.bytes.decodeToString()
        assertEquals("application/gpx+xml", gpx.mimeType)
        assertEquals(3, Regex("<trkpt ").findAll(text).count())
        assertTrue("<gpxtpx:hr>141</gpxtpx:hr>" in text)
        assertTrue("""lat="47.6002"""" in text)

        val tcx = s.exporter.export(view(uri), ExportFormat.TCX).bytes.decodeToString()
        assertTrue("""<Activity Sport="Running">""" in tcx)
        assertTrue("<DistanceMeters>5000</DistanceMeters>" in tcx)
        assertTrue("<AverageHeartRateBpm><Value>150</Value></AverageHeartRateBpm>" in tcx)
    }

    @Test
    fun anIndoorRideExportsAsTcxOnly() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft(ActivityType(Sport.RIDE, SubSport.INDOOR), gpx = false))
        serveStreams(uri, ActivityStreamData(time = listOf(0, 1), heartRate = listOf(120, 121), power = listOf(200, 210)))
        val ride = view(uri, Sport.RIDE)

        val tcx = s.exporter.export(ride, ExportFormat.TCX)
        val text = tcx.bytes.decodeToString()
        assertEquals("application/vnd.garmin.tcx+xml", tcx.mimeType)
        assertTrue("""<Activity Sport="Biking">""" in text)
        assertFalse("<Position>" in text)
        assertTrue("<ns3:Watts>210</ns3:Watts>" in text)
        assertFailsWith<ExportUnreadableException> { s.exporter.export(ride, ExportFormat.GPX) }
    }

    @Test
    fun aRideWithGearsExportsAsFit() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft(ActivityType(Sport.RIDE, SubSport.ROAD)))
        val gears = listOf(GearChange(time = 1, front = 2, rear = 5))
        serveStreams(uri, ActivityStreamData(time = listOf(0, 1, 2), heartRate = listOf(140, 141, 142), gears = gears))
        val ride = view(uri, Sport.RIDE).let { it.copy(record = it.record.copy(subSport = SubSport.ROAD, movingTime = 1700)) }

        val fit = s.exporter.export(ride, ExportFormat.FIT)
        assertEquals("application/vnd.ant.fit", fit.mimeType)
        assertTrue(fit.name.endsWith(" 2026-10-14.fit"), fit.name)
        val read = ActivityFileReader(GarminJavaFitDecoder()).read(fit.bytes).single()
        assertEquals(ActivityType(Sport.RIDE, SubSport.ROAD), read.type)
        assertEquals(1700L, read.movingSeconds)
        assertEquals(listOf(47.6, 47.6001, 47.6002), read.samples.map { kotlin.math.round(it.latitude * 1e4) / 1e4 })
        assertEquals(listOf(140, 141, 142), read.streams?.heartRate)
        assertEquals(gears, read.streams?.gears)
    }

    @Test
    fun aRideExportsItsLapsTotalsAndTrainingLoadAsFit() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft(ActivityType(Sport.RIDE, SubSport.ROAD), gpx = false))
        val laps =
            listOf(Lap(start = 0, elapsed = 60, moving = 60, distance = 500), Lap(start = 60, elapsed = 60, moving = 55, avgPower = 260))
        serveStreams(uri, ActivityStreamData(time = listOf(0, 60, 120), power = listOf(200, 250, 270), laps = laps))
        pds.streamsLoad[uri.rkey!!] = TrainingLoad(ftp = 250, intensityFactor = 84, trainingStressScore = 1124, aerobicTrainingEffect = 34)
        val ride =
            view(uri, Sport.RIDE).let {
                it.copy(
                    record =
                        it.record.copy(subSport = SubSport.ROAD, avgPower = 245, normalizedPower = 250, calories = 300, frontShifts = 2)
                )
            }

        val read = ActivityFileReader(GarminJavaFitDecoder()).read(s.exporter.export(ride, ExportFormat.FIT).bytes).single()
        assertEquals(laps, read.streams?.laps)
        assertEquals(245 to 250, read.power?.avgWatts to read.power?.normalizedWatts)
        assertEquals(250, read.power?.ftpWatts)
        assertEquals(300, read.calories)
        assertEquals(2, read.shifting?.frontShifts)
        assertEquals(3.4, read.aerobicTrainingEffect!!, 1e-6)
    }

    @Test
    fun aDiveExportsItsSwitchesAsFit() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft(ActivityType(Sport.DIVE), gpx = false))
        val switches = listOf(GasSwitch(0, 21, 0), GasSwitch(60, 50, 0))
        serveStreams(uri, ActivityStreamData(time = listOf(0, 60, 120), depth = listOf(0, 1800, 0), gasSwitches = switches))
        val dive =
            view(uri, Sport.DIVE).let {
                it.copy(
                    record = it.record.copy(distance = null, dive = ActivityDive(maxDepth = 1800, gases = listOf(ActivityDiveGas(21, 0))))
                )
            }

        val read = ActivityFileReader(GarminJavaFitDecoder()).read(s.exporter.export(dive, ExportFormat.FIT).bytes).single()
        assertEquals(switches, read.streams?.gasSwitches)
        assertEquals(listOf(DiveGas(21, 0), DiveGas(50, 0)), read.dive?.gases)
    }

    @Test
    fun aDiveExportsItsSummaryAsFit() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft(ActivityType(Sport.DIVE), gpx = false))
        serveStreams(uri, ActivityStreamData(time = listOf(0, 60, 120), depth = listOf(0, 1800, 0)))
        val dive =
            view(uri, Sport.DIVE).let {
                it.copy(
                    record =
                        it.record.copy(
                            distance = null,
                            dive = ActivityDive(maxDepth = 1800, diveNumber = 7, gases = listOf(ActivityDiveGas(32, 0))),
                        )
                )
            }

        val read = ActivityFileReader(GarminJavaFitDecoder()).read(s.exporter.export(dive, ExportFormat.FIT).bytes).single()
        assertEquals(Sport.DIVE, read.type.sport)
        assertEquals(listOf(0, 1800, 0), read.streams?.depth)
        assertEquals(18.0, read.dive?.maxDepthMeters)
        assertEquals(7, read.dive?.diveNumber)
        assertEquals(listOf(DiveGas(32, 0)), read.dive?.gases)
    }

    @Test
    fun aSwimIsOtherInTcx() {
        assertEquals("Other", ActivityExporter.tcxSport(Sport.SWIM).xmlName)
        assertEquals("Running", ActivityExporter.tcxSport(Sport.RUN).xmlName)
        assertEquals("Biking", ActivityExporter.tcxSport(Sport.RIDE).xmlName)
    }

    @Test
    fun aStoredOffsetSetsTheExportsLocalTimes() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft(ActivityType(Sport.RIDE, SubSport.ROAD), gpx = false))
        serveStreams(uri, ActivityStreamData(time = listOf(0, 60, 120), power = listOf(200, 250, 270)))
        val ride = view(uri, Sport.RIDE).let { it.copy(record = it.record.copy(subSport = SubSport.ROAD, utcOffset = 120)) }

        val file = s.exporter.export(ride, ExportFormat.FIT)
        assertEquals(120, ActivityFileReader(GarminJavaFitDecoder()).read(file.bytes).single().utcOffsetMinutes, "local timestamp at +2:00")
        // 07:00 UTC is 09:00 there, still the 14th; 23:30 UTC at +1:00 is the 15th.
        val late = ride.copy(record = ride.record.copy(startedAt = Instant.parse("2026-10-14T23:30:00Z"), utcOffset = 60))
        assertEquals(
            "2026-10-15",
            ActivityExporter.fileName(late, ExportFormat.GPX, ActivityExporter.zoneOf(late.record)).takeLast(14).take(10),
        )
    }

    @Test
    fun aDiveWithAStoredOffsetStartsAtItsLocalTimeInUddf() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft(ActivityType(Sport.DIVE), title = "Blue Hole", gpx = false))
        serveStreams(uri, ActivityStreamData(time = listOf(0, 60, 120), depth = listOf(0, 1800, 0)))
        val dive =
            view(uri, Sport.DIVE, title = "Blue Hole").let {
                it.copy(record = it.record.copy(distance = null, utcOffset = -300, dive = ActivityDive(maxDepth = 1800)))
            }

        val xml = s.exporter.export(dive, ExportFormat.UDDF).bytes.decodeToString()
        assertTrue("<datetime>2026-10-14T02:00:00</datetime>" in xml, "07:00 UTC at UTC−5")
    }

    @Test
    fun withoutAStoredOffsetTheDevicesZoneIsUsed() {
        val record = ActivityRecord(Sport.RUN, start, 1800, start)
        assertEquals(TimeZone.currentSystemDefault(), ActivityExporter.zoneOf(record))
    }

    @Test
    fun fileNamesAreTheTitleAndLocalDateWithoutInvalidCharacters() {
        val uri = AtUri("at://${pds.did.value}/app.activitytracker.feed.activity/3kabc")
        val utc = TimeZone.UTC
        val seattle = TimeZone.of("America/Los_Angeles")
        val late = view(uri).let { it.copy(record = it.record.copy(startedAt = Instant.parse("2026-10-15T03:00:00Z"))) }

        assertEquals("Morning Run 2026-10-14.gpx", ActivityExporter.fileName(view(uri), ExportFormat.GPX, utc))
        assertEquals("Morning Run 2026-10-14.tcx", ActivityExporter.fileName(late, ExportFormat.TCX, seattle), "the local date")
        assertEquals(
            "Hill_Repeats_ 1_2 _x_ 2026-10-14.gpx",
            ActivityExporter.fileName(view(uri, title = "Hill/Repeats: 1\\2 <x>"), ExportFormat.GPX, utc),
        )
        assertEquals("Xc ski 2026-10-14.gpx", ActivityExporter.fileName(view(uri, Sport.XC_SKI, title = null), ExportFormat.GPX, utc))
        val long = ActivityExporter.fileName(view(uri, title = "a".repeat(300)), ExportFormat.GPX, utc)
        assertTrue(long.length < 100)
    }

    private val fit = byteArrayOf(14, 16, 0, 0, 0, 0, 0, 0) + ".FIT".encodeToByteArray() + ByteArray(64) { it.toByte() }

    private suspend fun imported(bytes: ByteArray, name: String, gzipped: Boolean = false): ActivityDraft {
        val file = ImportedFile(ActivityFileFormat.FIT, gzipped, activities = emptyList())
        return draft(gpx = false).copy(source = DraftSource(requireNotNull(SourceFile.prepare(bytes, name, file))))
    }

    @Test
    fun aFitOriginalComesBackByteForByteUnderItsName() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(imported(fit, "Morning_Ride.fit"))

        val file = s.exporter.original(view(uri))

        assertEquals("Morning_Ride.fit", file.name)
        assertEquals(ExportFormat.FIT.mimeType, file.mimeType)
        assertContentEquals(fit, file.bytes)
    }

    @Test
    fun aGzippedOriginalComesBackStillGzipped() = runTest {
        val s = pds.session()
        val gzipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(fit) } }.toByteArray()
        val uri = s.activities.publish(imported(gzipped, "12345.fit.gz", gzipped = true))

        val file = s.exporter.original(view(uri))

        assertEquals("12345.fit.gz", file.name)
        assertEquals("application/gzip", file.mimeType)
        assertContentEquals(gzipped, file.bytes)
    }

    @Test
    fun aQueuedOriginalIsNotSentYet() = runTest {
        val s = pds.session()
        pds.offline = true
        val uri = s.activities.publish(imported(fit, "Morning_Ride.fit"))
        pds.offline = false

        assertFailsWith<NotSentYetException> { s.exporter.original(view(uri)) }
    }

    @Test
    fun anUnreadableOriginalFails() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(imported(fit, "Morning_Ride.fit"))
        pds.blobs.clear()

        assertFailsWith<ExportUnreadableException> { s.exporter.original(view(uri)) }
        assertFailsWith<ExportUnreadableException>("a recorded activity has none") {
            s.exporter.original(view(s.activities.publish(draft(gpx = false))))
        }
    }
}
