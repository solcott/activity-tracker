package io.github.solcott.activitytracker.activityfile

import com.garmin.fit.DateTime
import com.garmin.fit.Decode
import com.garmin.fit.FileIdMesg
import com.garmin.fit.FitRuntimeException
import com.garmin.fit.GarminProduct
import com.garmin.fit.LapMesg
import com.garmin.fit.Manufacturer
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import java.io.ByteArrayInputStream

/** FIT decoding with Garmin's Java SDK, for Android, desktop and the AppView (which constructs it directly, without Metro). */
@ContributesBinding(AppScope::class)
@Inject
class GarminJavaFitDecoder : FitDecoder {
    override fun decode(bytes: ByteArray): FitActivity {
        // A Decode keeps its position between calls, so each check gets a fresh one.
        if (!Decode().isFileFit(ByteArrayInputStream(bytes))) activityFileError("Not a FIT file")
        if (!Decode().checkFileIntegrity(ByteArrayInputStream(bytes))) activityFileError("FIT file is corrupt or truncated")

        val decode = Decode()

        var fileId: FitFileId? = null
        val sessions = mutableListOf<FitSummary>()
        val laps = mutableListOf<FitSummary>()
        val records = mutableListOf<FitRecord>()
        val broadcaster = MesgBroadcaster(decode)
        broadcaster.addListener { m: FileIdMesg ->
            // A file has one file_id; keep the first if a tool wrote more.
            if (fileId == null) {
                fileId =
                    FitFileId(
                        type = m.type?.name,
                        manufacturer = m.manufacturer?.let(Manufacturer::getStringFromValue),
                        product =
                            if (m.manufacturer == Manufacturer.GARMIN) m.garminProduct?.let(GarminProduct::getStringFromValue)
                            else m.productName ?: m.product?.toString(),
                        timeCreatedMillis = m.timeCreated?.millis(),
                    )
            }
        }
        broadcaster.addListener { m: SessionMesg -> sessions += m.toSummary() }
        broadcaster.addListener { m: LapMesg -> laps += m.toSummary() }
        broadcaster.addListener { m: RecordMesg ->
            records +=
                FitRecord(
                    timestampMillis = m.timestamp?.millis(),
                    latitude = m.positionLat?.let(FitUnits::semicirclesToDegrees),
                    longitude = m.positionLong?.let(FitUnits::semicirclesToDegrees),
                    altitudeMeters = (m.enhancedAltitude ?: m.altitude)?.toDouble(),
                    heartRate = m.heartRate?.toInt(),
                    cadence = m.cadence?.toInt(),
                    powerWatts = m.power,
                    speedMps = (m.enhancedSpeed ?: m.speed)?.toDouble(),
                    distanceMeters = m.distance?.toDouble(),
                    temperatureCelsius = m.temperature?.toDouble(),
                )
        }
        try {
            decode.read(ByteArrayInputStream(bytes), broadcaster, broadcaster)
        } catch (e: FitRuntimeException) {
            activityFileError("Couldn't read FIT file: ${e.message}", e)
        }
        return FitActivity(fileId, sessions, laps, records)
    }

    private fun DateTime.millis(): Long = instant.toEpochMilli()

    private fun SessionMesg.toSummary() =
        FitSummary(
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
        FitSummary(
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
