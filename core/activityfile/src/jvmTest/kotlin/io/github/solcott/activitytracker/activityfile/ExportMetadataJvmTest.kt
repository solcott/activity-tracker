package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class ExportMetadataJvmTest {
    private val reader = ActivityFileReader(GarminJavaFitDecoder())
    private val runStart = Instant.parse("2026-09-01T07:00:00Z")
    private val rideStart = Instant.parse("2026-09-02T07:00:00Z")
    private val run = FitFixtures.run(runStart)
    private val ride = FitFixtures.indoorRide(rideStart)
    private val gpx = BatchFixtures.gpx(Instant.parse("2026-09-03T07:00:00Z"))

    @Test
    fun appliesAStravaExportsCsv() = runTest {
        val csv =
            """
            Activity ID,Activity Date,Activity Name,Activity Type,Activity Description,Elapsed Time,Distance,Activity Private,Filename
            1,"Sep 1, 2026",Lunch loop,Trail Run,"Windy,
            cold",600,1.2,false,activities/1.fit.gz
            2,"Sep 2, 2026",Trainer,Zwift Dance Party,,600,0,true,activities/2.fit
            """
                .trimIndent()
        val zip =
            zipOf(
                "export_123/activities.csv" to csv.encodeToByteArray(),
                "export_123/activities/1.fit.gz" to gzipped(run),
                "export_123/activities/2.fit" to ride,
                "export_123/activities/3.gpx" to gpx,
                "export_123/media/photo.jpg" to ByteArray(10),
            )
        val reads = scan(zip)
        assertEquals(listOf(ExportKind.Strava), reads.map { it.export?.kind }.distinct())
        val (first, second, third) = reads.map { it.exportOf(0) }
        assertEquals(
            AccountExportActivity("Lunch loop", "Windy,\ncold", ActivityType(Sport.RUN, SubSport.TRAIL), ExportVisibility.Everyone),
            first,
        )
        // A type we don't know keeps the file's.
        assertEquals(AccountExportActivity("Trainer", null, null, ExportVisibility.OnlyMe), second)
        // A file the CSV doesn't name imports as from any archive.
        assertNull(third)
    }

    @Test
    fun readsAVisibilityColumnWhereTheExportHasOne() = runTest {
        val csv =
            """
            Activity Name,Activity Type,Activity Visibility,Filename
            A,Run,followers_only,activities/1.fit
            B,Ride,only_me,activities/2.fit
            """
                .trimIndent()
        val zip = zipOf("activities.csv" to csv.encodeToByteArray(), "activities/1.fit" to run, "activities/2.fit" to ride)
        assertEquals(
            listOf(ExportVisibility.Followers, ExportVisibility.OnlyMe),
            scan(zip).map { it.exportOf(0)?.visibility },
        )
    }

    @Test
    fun withoutAPrivacyColumnTheExportSaysNothingOfVisibility() = runTest {
        val csv = "Activity Name,Filename\nA,activities/1.fit\n"
        val zip = zipOf("activities.csv" to csv.encodeToByteArray(), "activities/1.fit" to run)
        assertNull(scan(zip).single().exportOf(0)?.visibility)
    }

    @Test
    fun appliesAGarminExportsSummaries() = runTest {
        val rideMillis = reader.read(ride).single().elapsed.inWholeMilliseconds
        val json =
            """
            [{"summarizedActivitiesExport":[
              {"activityId":12345678,"name":"Track Tuesday","activityType":"running","beginTimestamp":${runStart.toEpochMilliseconds()},
               "duration":1800000.0,"privacy":{"typeKey":"private"}},
              {"activityId":99999999,"name":"Trainer","activityType":{"typeKey":"indoor_cycling"},
               "beginTimestamp":${rideStart.toEpochMilliseconds() + 20_000},"elapsedDuration":${rideMillis + 1000}.0}
            ]}]
            """
                .trimIndent()
        val uploads = zipOf("me@example.com_12345678.fit" to run, "ride.fit" to ride, "unmatched.gpx" to gpx)
        val zip =
            zipOf(
                "DI_CONNECT/DI-Connect-Fitness/me@example.com_0_summarizedActivities.json" to json.encodeToByteArray(),
                "DI_CONNECT/DI-Connect-Uploaded-Files/UploadedFiles_0-_Part1.zip" to uploads,
            )
        val reads = scan(zip)
        assertEquals(listOf(ExportKind.Garmin), reads.map { it.export?.kind }.distinct())
        val byName = reads.associate { it.name to it.exportOf(0) }
        // By the activity ID in the file's name.
        assertEquals(
            AccountExportActivity("Track Tuesday", null, ActivityType(Sport.RUN), ExportVisibility.OnlyMe),
            byName["me@example.com_12345678.fit"],
        )
        // By its start, 20 s off, when the name carries no ID.
        assertEquals("Trainer", byName["ride.fit"]?.title)
        assertEquals(ActivityType(Sport.RIDE, SubSport.INDOOR), byName["ride.fit"]?.type)
        assertNull(byName["unmatched.gpx"])
    }

    @Test
    fun unreadableMetadataLeavesTheActivitiesAsTheirFilesSay() = runTest {
        val zip =
            zipOf(
                "DI_CONNECT/DI-Connect-Fitness/x_summarizedActivities.json" to "{ not json".encodeToByteArray(),
                "DI_CONNECT/DI-Connect-Uploaded-Files/UploadedFiles_0.zip" to zipOf("a_12345678.fit" to run),
            )
        assertNull(scan(zip).single().exportOf(0))
    }

    private suspend fun scan(zip: ByteArray): List<ScanEvent.Read> =
        BatchReader(listOf(ByteArrayImportSource("export.zip", zip)), reader).scan().toList().filterIsInstance<ScanEvent.Read>()

    private fun gzipped(bytes: ByteArray): ByteArray =
        java.io.ByteArrayOutputStream().also { out -> java.util.zip.GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
}
