package io.github.solcott.activitytracker.activityfile

import com.garmin.fit.DateTime
import com.garmin.fit.Decode
import com.garmin.fit.DiveGasMesg
import com.garmin.fit.DiveSettingsMesg
import com.garmin.fit.DiveSummaryMesg
import com.garmin.fit.Event
import com.garmin.fit.EventMesg
import com.garmin.fit.FileIdMesg
import com.garmin.fit.FitRuntimeException
import com.garmin.fit.GarminProduct
import com.garmin.fit.LapMesg
import com.garmin.fit.Manufacturer
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.MesgNum
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.TankSummaryMesg
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import java.io.ByteArrayInputStream

/** FIT decoding with Garmin's Java SDK, for Android, desktop and the AppView (which constructs it directly, without Metro). */
@ContributesBinding(AppScope::class)
@Inject
class GarminJavaFitDecoder : FitDecoder {
    override fun decode(bytes: ByteArray): DecodedActivity {
        // A Decode keeps its position between calls, so each check gets a fresh one.
        if (!Decode().isFileFit(ByteArrayInputStream(bytes))) activityFileError("Not a FIT file")
        if (!Decode().checkFileIntegrity(ByteArrayInputStream(bytes))) activityFileError("FIT file is corrupt or truncated")

        val decode = Decode()

        var fileId: DecodedSource? = null
        val sessions = mutableListOf<DecodedSummary>()
        val laps = mutableListOf<DecodedSummary>()
        val records = mutableListOf<DecodedSample>()
        var firstSession: SessionMesg? = null
        val broadcaster = MesgBroadcaster(decode)
        val dive = DiveListeners(broadcaster)
        broadcaster.addListener { m: FileIdMesg ->
            // A file has one file_id; keep the first if a tool wrote more.
            if (fileId == null) {
                fileId =
                    DecodedSource(
                        type = m.type?.name,
                        manufacturer = m.manufacturer?.let(Manufacturer::getStringFromValue),
                        product =
                            if (m.manufacturer == Manufacturer.GARMIN) m.garminProduct?.let(GarminProduct::getStringFromValue)
                            else m.productName ?: m.product?.toString(),
                        timeCreatedMillis = m.timeCreated?.millis(),
                    )
            }
        }
        broadcaster.addListener { m: SessionMesg ->
            if (firstSession == null) firstSession = m
            sessions += m.toSummary()
        }
        broadcaster.addListener { m: LapMesg -> laps += m.toSummary() }
        broadcaster.addListener { m: RecordMesg -> records += m.toSample() }
        try {
            decode.read(ByteArrayInputStream(bytes), broadcaster, broadcaster)
        } catch (e: FitRuntimeException) {
            activityFileError("Couldn't read FIT file: ${e.message}", e)
        }
        return DecodedActivity(fileId, sessions, laps, records, dive.interpret(firstSession))
    }

    private fun RecordMesg.toSample() =
        DecodedSample(
            timestampMillis = timestamp?.millis(),
            latitude = positionLat?.let(FitUnits::semicirclesToDegrees),
            longitude = positionLong?.let(FitUnits::semicirclesToDegrees),
            altitudeMeters = (enhancedAltitude ?: altitude)?.toDouble(),
            heartRate = heartRate?.toInt(),
            cadence = cadence?.toInt(),
            powerWatts = power,
            speedMps = (enhancedSpeed ?: speed)?.toDouble(),
            distanceMeters = distance?.toDouble(),
            temperatureCelsius = temperature?.toDouble(),
            depthMeters = depth?.toDouble(),
        )

    private fun DateTime.millis(): Long = instant.toEpochMilli()

    private fun SessionMesg.toSummary() =
        DecodedSummary(
            sport = sport?.name,
            subSport = subSport?.name,
            startTimeMillis = startTime?.millis(),
            totalElapsedSeconds = totalElapsedTime?.toDouble(),
            totalTimerSeconds = totalTimerTime?.toDouble(),
            totalDistanceMeters = totalDistance?.toDouble(),
            totalAscentMeters = totalAscent?.toDouble(),
            avgSpeedMps = (enhancedAvgSpeed ?: avgSpeed)?.toDouble(),
            maxSpeedMps = (enhancedMaxSpeed ?: maxSpeed)?.toDouble(),
            avgHeartRate = avgHeartRate?.toInt(),
            maxHeartRate = maxHeartRate?.toInt(),
            avgPowerWatts = avgPower,
            avgCadence = avgCadence?.toInt(),
            avgTemperatureCelsius = avgTemperature?.toDouble(),
        )

    private fun LapMesg.toSummary() =
        DecodedSummary(
            sport = sport?.name,
            subSport = subSport?.name,
            startTimeMillis = startTime?.millis(),
            totalElapsedSeconds = totalElapsedTime?.toDouble(),
            totalTimerSeconds = totalTimerTime?.toDouble(),
            totalDistanceMeters = totalDistance?.toDouble(),
            totalAscentMeters = totalAscent?.toDouble(),
            avgSpeedMps = (enhancedAvgSpeed ?: avgSpeed)?.toDouble(),
            maxSpeedMps = (enhancedMaxSpeed ?: maxSpeed)?.toDouble(),
            avgHeartRate = avgHeartRate?.toInt(),
            maxHeartRate = maxHeartRate?.toInt(),
            avgPowerWatts = avgPower,
            avgCadence = avgCadence?.toInt(),
            avgTemperatureCelsius = avgTemperature?.toDouble(),
        )
}

/** Collects a dive log's dive messages as the decoder reads them, for [FitDives] to interpret. */
private class DiveListeners(broadcaster: MesgBroadcaster) {
    private var settings: DiveSettingsMesg? = null
    private val summaries = mutableListOf<FitDiveSummary>()
    private val gases = mutableListOf<FitDiveGas>()
    private val gasSwitches = mutableListOf<Int>()
    private val tanks = mutableListOf<FitTankSummary>()

    init {
        broadcaster.addListener { m: DiveSettingsMesg -> if (settings == null) settings = m }
        broadcaster.addListener { m: DiveSummaryMesg -> summaries += m.toDiveSummary() }
        broadcaster.addListener { m: DiveGasMesg ->
            gases += FitDiveGas(m.messageIndex, m.oxygenContent?.toInt(), m.heliumContent?.toInt(), m.status?.name, m.mode?.name)
        }
        broadcaster.addListener { m: EventMesg -> if (m.event == Event.DIVE_GAS_SWITCHED) m.data?.let { gasSwitches += it.toInt() } }
        broadcaster.addListener { m: TankSummaryMesg ->
            tanks += FitTankSummary(m.startPressure?.toDouble(), m.endPressure?.toDouble(), m.volumeUsed?.toDouble())
        }
    }

    /** [session] is the file's first: where the diver went in and came out, and its own dive fields. */
    fun interpret(session: SessionMesg?): DecodedDive? =
        FitDives.interpret(
            FitDiveMessages(
                summaries = summaries,
                gradientFactorLow = settings?.gfLow?.toInt(),
                gradientFactorHigh = settings?.gfHigh?.toInt(),
                gases = gases,
                gasSwitches = gasSwitches,
                tanks = tanks,
                entry = position(session?.startPositionLat, session?.startPositionLong),
                exit = position(session?.endPositionLat, session?.endPositionLong),
                sessionMaxDepthMeters = session?.maxDepth?.toDouble(),
                sessionAvgDepthMeters = session?.avgDepth?.toDouble(),
                sessionDiveNumber = session?.diveNumber?.toInt(),
            ),
            sport = session?.sport?.name,
            subSport = session?.subSport?.name,
        )

    private fun position(lat: Int?, lon: Int?): DivePosition? =
        if (lat == null || lon == null) null else DivePosition(FitUnits.semicirclesToDegrees(lat), FitUnits.semicirclesToDegrees(lon))

    private fun DiveSummaryMesg.toDiveSummary() =
        FitDiveSummary(
            referenceMesg = referenceMesg?.let { MesgNum.getStringFromValue(it).ifEmpty { it.toString() } },
            avgDepthMeters = avgDepth?.toDouble(),
            maxDepthMeters = maxDepth?.toDouble(),
            surfaceIntervalSeconds = surfaceInterval?.toDouble(),
            diveNumber = diveNumber?.toInt(),
            bottomTimeSeconds = bottomTime?.toDouble(),
            startCns = startCns?.toInt(),
            endCns = endCns?.toInt(),
            oxygenToxicityOtu = o2Toxicity,
            avgRmv = avgRmv?.toDouble(),
            avgVolumeSac = avgVolumeSac?.toDouble(),
            avgPressureSac = avgPressureSac?.toDouble(),
        )
}
