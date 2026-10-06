package io.github.solcott.activitytracker.activityfile

import com.garmin.fit.BufferEncoder
import com.garmin.fit.DateTime
import com.garmin.fit.DiveGasMesg
import com.garmin.fit.DiveGasMode
import com.garmin.fit.DiveGasStatus
import com.garmin.fit.DiveSettingsMesg
import com.garmin.fit.DiveSummaryMesg
import com.garmin.fit.Event
import com.garmin.fit.EventMesg
import com.garmin.fit.EventType
import com.garmin.fit.File
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.GarminProduct
import com.garmin.fit.LapMesg
import com.garmin.fit.Manufacturer
import com.garmin.fit.Mesg
import com.garmin.fit.MesgNum
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport as FitSport
import com.garmin.fit.SubSport
import com.garmin.fit.TankSummaryMesg
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/** Small FIT activity files, written with Garmin's encoder so the decoder reads the same bytes a device would produce. */
object FitFixtures {
    const val RUN_POINTS = 11

    private const val SEMICIRCLES_PER_DEGREE = 2_147_483_648.0 / 180.0

    /** A 1 km run north from 47.6, -122.3: one point every 30 s, heart rate climbing from 120 to 190 bpm (avg 155). */
    fun run(start: Instant): ByteArray =
        encode(start, FitSport.RUNNING, SubSport.GENERIC, distance = 1000f, avgHr = 155, points = RUN_POINTS) { i, record ->
            record.positionLat = ((47.6 + i * 0.0009) * SEMICIRCLES_PER_DEGREE).roundToInt()
            record.positionLong = (-122.3 * SEMICIRCLES_PER_DEGREE).roundToInt()
            record.enhancedAltitude = 50f + i
            record.heartRate = (120 + i * 7).toShort()
            record.distance = i * 100f
        }

    /** A trainer ride: power and cadence, no position. */
    fun indoorRide(start: Instant): ByteArray =
        encode(start, FitSport.CYCLING, SubSport.INDOOR_CYCLING, distance = 5000f, avgHr = 140, points = 5) { _, record ->
            record.power = 200
            record.cadence = 90.toShort()
        }

    /**
     * What the sample Descent files lack: RMV, a tank with its gas used, a backup-only gas and a lap summary written before the session's.
     * Ten minutes at 0-20-0 m on air, then nitrox 50 from the backup slot; GF 35/75.
     */
    fun dive(start: Instant): ByteArray {
        val startedAt = DateTime(start.toJavaInstant())
        fun at(seconds: Long) = DateTime(startedAt).apply { add(seconds) }
        fun summary(reference: Int) =
            DiveSummaryMesg().apply {
                timestamp = at(DIVE_POINTS * 60L)
                referenceMesg = reference
                referenceIndex = 0
                avgDepth = 12.5f
                maxDepth = 20f
                surfaceInterval = 3_600L
                bottomTime = 480f
                avgRmv = 14.2f
                avgPressureSac = 1.1f
            }
        val extra =
            listOf<Mesg>(
                DiveSettingsMesg().apply {
                    gfLow = 35.toShort()
                    gfHigh = 75.toShort()
                },
                gas(0, 21, DiveGasStatus.ENABLED),
                gas(1, 50, DiveGasStatus.BACKUP_ONLY),
                gas(2, 21, DiveGasStatus.DISABLED),
                EventMesg().apply {
                    timestamp = at(420)
                    event = Event.DIVE_GAS_SWITCHED
                    eventType = EventType.MARKER
                    data = 1L
                },
                summary(MesgNum.LAP),
                summary(MesgNum.SESSION).apply {
                    diveNumber = 7L
                    startCns = 0.toShort()
                    endCns = 9.toShort()
                    o2Toxicity = 12
                },
                TankSummaryMesg().apply {
                    timestamp = at(DIVE_POINTS * 60L)
                    sensor = 42L
                    startPressure = 200f
                    endPressure = 60f
                    volumeUsed = 1_680f
                },
            )
        return encode(start, FitSport.DIVING, SubSport.MULTI_GAS_DIVING, 0f, 80, DIVE_POINTS + 1, interval = 60, extra) { i, record ->
            record.depth = 20f - abs(i - DIVE_POINTS / 2) * 4f
            record.temperature = (18 - i % 3).toByte()
        }
    }

    const val DIVE_POINTS = 10

    private fun gas(index: Int, oxygen: Int, status: DiveGasStatus) =
        DiveGasMesg().apply {
            messageIndex = index
            oxygenContent = oxygen.toShort()
            heliumContent = 0.toShort()
            this.status = status
            mode = DiveGasMode.OPEN_CIRCUIT
        }

    @Suppress("LongParameterList")
    private fun encode(
        start: Instant,
        sport: FitSport,
        subSport: SubSport,
        distance: Float,
        avgHr: Int,
        points: Int,
        interval: Long = 30,
        extra: List<Mesg> = emptyList(),
        fill: (Int, RecordMesg) -> Unit,
    ): ByteArray {
        val startedAt = DateTime(start.toJavaInstant())
        val elapsed = (points - 1) * interval.toFloat()
        val end = DateTime(startedAt).apply { add(elapsed.toLong()) }
        val encoder = BufferEncoder(Fit.ProtocolVersion.V2_0)
        encoder.write(
            FileIdMesg().apply {
                type = File.ACTIVITY
                manufacturer = Manufacturer.GARMIN
                product = GarminProduct.FENIX7
                timeCreated = startedAt
                serialNumber = 1234L
            }
        )
        repeat(points) { i ->
            encoder.write(
                RecordMesg().apply {
                    timestamp = DateTime(startedAt).apply { add(i * interval) }
                    fill(i, this)
                }
            )
        }
        extra.forEach(encoder::write)
        encoder.write(
            LapMesg().apply {
                timestamp = end
                startTime = DateTime(startedAt)
                totalElapsedTime = elapsed
                totalTimerTime = elapsed
                totalDistance = distance
            }
        )
        encoder.write(
            SessionMesg().apply {
                timestamp = end
                startTime = DateTime(startedAt)
                this.sport = sport
                this.subSport = subSport
                totalElapsedTime = elapsed
                totalTimerTime = elapsed
                totalDistance = distance
                avgHeartRate = avgHr.toShort()
            }
        )
        return encoder.close()
    }
}
