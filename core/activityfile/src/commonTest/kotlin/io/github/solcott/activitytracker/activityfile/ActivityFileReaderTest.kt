package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class ActivityFileReaderTest {
    private val start = Instant.parse("2026-09-20T07:00:00Z")

    private val fitRun =
        DecodedActivity(
            fileId = null,
            sessions =
                listOf(
                    DecodedSummary(
                        "running",
                        null,
                        start.toEpochMilliseconds(),
                        60.0,
                        60.0,
                        200.0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                    )
                ),
            laps = emptyList(),
            records = emptyList(),
        )

    /** Only what the reader looks at: a FIT header with ".FIT" at byte 8. */
    private val fitHeader = byteArrayOf(14, 16, 0, 0, 0, 0, 0, 0) + ".FIT".encodeToByteArray()

    /** A two-point running GPX, gzipped (by Python's gzip, so not by any of our gunzip actuals). */
    private val gzippedGpx =
        Base64.decode(
            "H4sIAAAAAAAC/42QTWvDMAyG7/0VRvf4q6NfxO5tu+7QQuktpG4amtjG0Zb031cuPRS6w0BIr5B4XqFyO/Ud+3VpaIM3oLgE5nwd" +
                "Tq1vDOx3n8UKtnZWNnF63VLA6uQqDMkAugGBEcYPBi6IcSPEOI4cQwxNqs7txOvQi6/vg1BCgS0xXSndorPpx3syKsWjy4PBNeRG" +
                "IiLrKjTwseQLYF22LZTWfJ4Bbe+slnpRyHWh5U4uN1JSHAmUR1Qy4B0k1f9Q8z9Q4nndQ1Cmj9g7X2gjvD4BAAA="
        )

    private val reader = ActivityFileReader { fitRun }

    @Test fun picksFitByItsSignature() = runTest { assertEquals(200.0, reader.read(fitHeader + ByteArray(4)).distanceMeters) }

    @Test
    fun picksGpxAndTcxByContent() = runTest {
        val gpx =
            """<gpx version="1.1"><trk><type>walking</type><trkseg><trkpt lat="47.6" lon="-122.3"><time>2026-09-20T07:00:00Z</time></trkpt>
              </trkseg></trk></gpx>"""
        assertEquals(Sport.WALK, reader.read(gpx.encodeToByteArray()).type.sport)

        val tcx =
            """<?xml version="1.0"?><TrainingCenterDatabase><Activities><Activity Sport="Running"><Id>2026-09-20T07:00:00Z</Id>
              <Lap StartTime="2026-09-20T07:00:00Z"><TotalTimeSeconds>60</TotalTimeSeconds><DistanceMeters>250</DistanceMeters></Lap>
              </Activity></Activities></TrainingCenterDatabase>"""
        val activity = reader.read(tcx.encodeToByteArray())
        assertEquals(Sport.RUN, activity.type.sport)
        assertEquals(250.0, activity.distanceMeters)
    }

    @Test
    fun unzipsGzippedFiles() = runTest {
        val activity = reader.read(gzippedGpx)
        assertEquals(Sport.RUN, activity.type.sport)
        assertEquals(start, activity.startedAt)
        assertEquals(111.2, activity.distanceMeters!!, 0.5)
    }

    @Test fun stopsAtTheUnzippedSizeCap() = runTest { assertFailsWith<ActivityFileException> { gunzip(gzippedGpx, maxBytes = 100) } }

    @Test
    fun rejectsATruncatedGzip() = runTest {
        assertFailsWith<ActivityFileException> { reader.read(gzippedGpx.copyOf(gzippedGpx.size - 30)) }
    }

    @Test
    fun picksShearwaterDivesByContent() = runTest {
        val activity = reader.read(ShearwaterFixtures.dive().encodeToByteArray())
        assertEquals(Sport.DIVE, activity.type.sport)
        assertEquals("Shearwater Peregrine TX", activity.device)
        assertFailsWith<ActivityFileException> { reader.read("""<dive version="3"/>""".encodeToByteArray()) }
    }

    @Test
    fun readsUtf16WithAByteOrderMark() = runTest {
        val xml = ShearwaterFixtures.dive()
        fun utf16(bigEndian: Boolean): ByteArray {
            val bom = if (bigEndian) byteArrayOf(-2, -1) else byteArrayOf(-1, -2)
            return bom +
                xml.flatMap { c ->
                    val high = (c.code shr 8).toByte()
                    val low = c.code.toByte()
                    if (bigEndian) listOf(high, low) else listOf(low, high)
                }
        }
        assertEquals(Sport.DIVE, reader.read(utf16(bigEndian = false)).type.sport)
        assertEquals(Sport.DIVE, reader.read(utf16(bigEndian = true)).type.sport)
    }

    @Test
    fun rejectsOtherFiles() = runTest {
        assertFailsWith<UnsupportedActivityFileException> { reader.read("hello".encodeToByteArray()) }
        assertFailsWith<UnsupportedActivityFileException> { reader.read("<html><body/></html>".encodeToByteArray()) }
        assertFailsWith<UnsupportedActivityFileException> { reader.read(byteArrayOf(0, 1, 2, -1, -2, 7)) }
    }
}
