package io.github.solcott.activitytracker.activityfile

import com.garmin.fit.ActivityMesg
import com.garmin.fit.AntplusDeviceType
import com.garmin.fit.BatteryStatus
import com.garmin.fit.BleDeviceType
import com.garmin.fit.DateTime
import com.garmin.fit.Decode
import com.garmin.fit.DeviceInfoMesg
import com.garmin.fit.DiveGasMesg
import com.garmin.fit.DiveSettingsMesg
import com.garmin.fit.DiveSummaryMesg
import com.garmin.fit.Event
import com.garmin.fit.EventMesg
import com.garmin.fit.ExerciseCategory
import com.garmin.fit.FileIdMesg
import com.garmin.fit.FitRuntimeException
import com.garmin.fit.GarminProduct
import com.garmin.fit.HrMesg
import com.garmin.fit.HrvMesg
import com.garmin.fit.JumpMesg
import com.garmin.fit.LapMesg
import com.garmin.fit.LengthMesg
import com.garmin.fit.LocalDeviceType
import com.garmin.fit.Manufacturer
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.MesgNum
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.SetMesg
import com.garmin.fit.SetType
import com.garmin.fit.SourceType
import com.garmin.fit.SplitMesg
import com.garmin.fit.TankSummaryMesg
import com.garmin.fit.TankUpdateMesg
import com.garmin.fit.UserProfileMesg
import com.garmin.fit.ZonesTargetMesg
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
        val ride = RideListeners(broadcaster)
        broadcaster.addListener { m: FileIdMesg ->
            // A file has one file_id; keep the first if a tool wrote more.
            if (fileId == null) {
                fileId =
                    DecodedSource(
                        type = m.type?.name,
                        manufacturer = m.manufacturer?.let(Manufacturer::getStringFromValue),
                        product = productName(m.manufacturer, m.garminProduct, m.productName, m.product),
                        timeCreatedMillis = m.timeCreated?.millis(),
                    )
            }
        }
        broadcaster.addListener { m: SessionMesg ->
            if (firstSession == null) firstSession = m
            sessions += m.toSummary()
        }
        broadcaster.addListener { m: LapMesg -> laps += m.toSummary() }
        val workout = WorkoutListeners(broadcaster)
        var zonesTarget: ZonesTargetMesg? = null
        var userProfile: UserProfileMesg? = null
        broadcaster.addListener { m: ZonesTargetMesg -> if (zonesTarget == null) zonesTarget = m }
        broadcaster.addListener { m: UserProfileMesg -> if (userProfile == null) userProfile = m }
        broadcaster.addListener { m: RecordMesg -> records += m.toSample() }
        val heart = HeartListeners(broadcaster)
        try {
            decode.read(ByteArrayInputStream(bytes), broadcaster, broadcaster)
        } catch (e: FitRuntimeException) {
            activityFileError("Couldn't read FIT file: ${e.message}", e)
        }
        val diveMessages = dive.messages(firstSession)
        val decoded =
            DecodedActivity(
                fileId = fileId,
                sessions = sessions,
                laps = laps,
                records = records,
                dive = FitDives.interpret(diveMessages, sport = firstSession?.sport?.name, subSport = firstSession?.subSport?.name),
                gasSwitches = FitDives.gasSwitches(diveMessages),
                tankUpdates = FitDives.tankUpdates(diveMessages),
                sensors = ride.sensors,
                gearChanges = ride.gearChanges,
                jumps = ride.jumps,
                athlete = athleteSettings(zonesTarget, userProfile),
                lengths = workout.lengths,
                sets = workout.sets,
                splits = workout.splits,
            )
        return heart.into(decoded)
    }

    private fun athleteSettings(zones: ZonesTargetMesg?, profile: UserProfileMesg?): DecodedAthleteSettings? =
        if (zones == null && profile == null) {
            null
        } else {
            DecodedAthleteSettings(
                maxHeartRate = zones?.maxHeartRate?.toInt(),
                thresholdHeartRate = zones?.thresholdHeartRate?.toInt(),
                functionalThresholdPower = zones?.functionalThresholdPower,
                restingHeartRate = profile?.restingHeartRate?.toInt(),
            )
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
            ppO2Bar = po2?.toDouble(),
            cnsPercent = cnsLoad?.toInt(),
            n2LoadPercent = n2Load,
            ndlSeconds = ndlTime,
            nextStopDepthMeters = nextStopDepth?.toDouble(),
            nextStopSeconds = nextStopTime,
            ttsSeconds = timeToSurface,
            respirationRate = (enhancedRespirationRate ?: respirationRate?.toFloat())?.toDouble(),
            verticalOscillationMm = verticalOscillation?.toDouble(),
            stanceTimeMs = stanceTime?.toDouble(),
            stepLengthMm = stepLength?.toDouble(),
            verticalRatioPercent = verticalRatio?.toDouble(),
            stanceTimeBalancePercent = stanceTimeBalance?.toDouble(),
            leftRightBalance = leftRightBalance?.toInt(),
            leftTorqueEffectiveness = leftTorqueEffectiveness?.toDouble(),
            rightTorqueEffectiveness = rightTorqueEffectiveness?.toDouble(),
            leftPedalSmoothness = leftPedalSmoothness?.toDouble(),
            rightPedalSmoothness = rightPedalSmoothness?.toDouble(),
            leftPcoMm = leftPco?.toInt(),
            rightPcoMm = rightPco?.toInt(),
            coreTemperatureCelsius = coreTemperature?.toDouble(),
            saturatedHemoglobinPercent = saturatedHemoglobinPercent?.toDouble(),
            totalHemoglobinConc = totalHemoglobinConc?.toDouble(),
            ebikeBatteryPercent = ebikeBatteryLevel?.toInt(),
            ebikeAssistMode = ebikeAssistMode?.toInt(),
            ebikeAssistLevelPercent = ebikeAssistLevelPercent?.toInt(),
            developerFields = developerFields.map { DecodedDeveloperField(it.name, it.units, it.doubleValue) },
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
            maxPowerWatts = maxPower,
            normalizedPowerWatts = normalizedPower,
            maxCadence = maxCadence?.toInt(),
            totalDescentMeters = totalDescent?.toDouble(),
            totalWorkJoules = totalWork?.toDouble(),
            thresholdPowerWatts = thresholdPower,
            intensityFactor = intensityFactor?.toDouble(),
            trainingStressScore = trainingStressScore?.toDouble(),
            frontShiftCount = frontGearShiftCount,
            rearShiftCount = rearGearShiftCount,
            jumpCount = jumpCount,
            totalGrit = totalGrit?.toDouble(),
            avgFlow = avgFlow?.toDouble(),
            totalCalories = totalCalories,
            aerobicTrainingEffect = totalTrainingEffect?.toDouble(),
            anaerobicTrainingEffect = totalAnaerobicTrainingEffect?.toDouble(),
            poolLengthMeters = poolLength?.toDouble(),
            totalCycles = totalCycles,
            avgStrokeDistanceMeters = avgStrokeDistance?.toDouble(),
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
            maxPowerWatts = maxPower,
            normalizedPowerWatts = normalizedPower,
            maxCadence = maxCadence?.toInt(),
            totalDescentMeters = totalDescent?.toDouble(),
            totalWorkJoules = totalWork?.toDouble(),
            frontShiftCount = frontGearShiftCount,
            rearShiftCount = rearGearShiftCount,
            jumpCount = jumpCount,
            totalGrit = totalGrit?.toDouble(),
            avgFlow = avgFlow?.toDouble(),
            totalCalories = totalCalories,
            intensity = intensity?.name,
            lapTrigger = lapTrigger?.name,
        )
}

/**
 * Collects heart rate a strap stored in `hr` messages, the beat-to-beat intervals in `hrv` messages, and the `activity` message's UTC and
 * local times.
 */
private class HeartListeners(broadcaster: MesgBroadcaster) {
    private val hrMessages = mutableListOf<DecodedHrMessage>()
    private val rrIntervals = mutableListOf<Double>()
    private var activity: ActivityMesg? = null

    /** [decoded] with what these collected. */
    fun into(decoded: DecodedActivity) =
        decoded.copy(
            hrMessages = hrMessages,
            rrIntervalsSeconds = rrIntervals,
            activityTimestampMillis = activity?.timestamp?.instant?.toEpochMilli(),
            activityLocalTimestampSeconds = activity?.localTimestamp,
        )

    init {
        broadcaster.addListener { m: HrMesg ->
            // The last message's arrays are padded with invalid values: keep only valid pairs.
            val readings =
                m.eventTimestamp.orEmpty().zip(m.filteredBpm.orEmpty()).mapNotNull { (time, bpm) ->
                    val seconds = time?.toDouble() ?: return@mapNotNull null
                    val reading = bpm?.toInt() ?: return@mapNotNull null
                    (seconds to reading).takeIf { FitHeart.isValidReading(seconds, reading) }
                }
            hrMessages +=
                DecodedHrMessage(
                    timestampMillis = m.timestamp?.instant?.toEpochMilli(),
                    fractionalSeconds = m.fractionalTimestamp?.toDouble(),
                    eventTimestamps = readings.map { it.first },
                    filteredBpm = readings.map { it.second },
                )
        }
        broadcaster.addListener { m: ActivityMesg -> if (activity == null) activity = m }
        broadcaster.addListener { m: HrvMesg ->
            m.time.orEmpty().forEach { time -> time?.toDouble()?.takeIf(FitHeart::isValidRr)?.let { rrIntervals += it } }
        }
    }
}

/** Collects a pool swim's lengths, a workout's sets and a ski day's splits as the decoder reads them. */
private class WorkoutListeners(broadcaster: MesgBroadcaster) {
    val lengths = mutableListOf<DecodedLength>()
    val sets = mutableListOf<DecodedSet>()
    val splits = mutableListOf<DecodedSplit>()

    init {
        broadcaster.addListener { m: LengthMesg -> lengths += m.toLength() }
        broadcaster.addListener { m: SetMesg -> sets += m.toSet() }
        broadcaster.addListener { m: SplitMesg -> splits += m.toSplit() }
    }

    private fun LengthMesg.toLength() =
        DecodedLength(
            startTimeMillis = startTime?.instant?.toEpochMilli(),
            totalElapsedSeconds = totalElapsedTime?.toDouble(),
            swimStroke = swimStroke?.name,
            totalStrokes = totalStrokes,
            lengthType = lengthType?.name,
        )

    private fun SetMesg.toSet(): DecodedSet {
        val category = getCategory(0)
        val subtype = getCategorySubtype(0)
        return DecodedSet(
            startTimeMillis = startTime?.instant?.toEpochMilli(),
            timestampMillis = timestamp?.instant?.toEpochMilli(),
            durationSeconds = duration?.toDouble(),
            setType = setType?.let(SetType::getStringFromValue)?.ifEmpty { null },
            category = category?.let(ExerciseCategory::getStringFromValue)?.ifEmpty { null },
            exercise = if (category != null && subtype != null) exerciseName(category, subtype) else null,
            repetitions = repetitions,
            weightKg = weight?.toDouble(),
        )
    }

    private fun SplitMesg.toSplit() =
        DecodedSplit(
            splitType = splitType?.name,
            startTimeMillis = startTime?.instant?.toEpochMilli(),
            totalElapsedSeconds = totalElapsedTime?.toDouble(),
            totalTimerSeconds = totalTimerTime?.toDouble(),
            totalDistanceMeters = totalDistance?.toDouble(),
            totalAscentMeters = totalAscent?.toDouble(),
            totalDescentMeters = totalDescent?.toDouble(),
            avgSpeedMps = avgSpeed?.toDouble(),
            maxSpeedMps = maxSpeed?.toDouble(),
            avgHeartRate = avgHeartRate?.toInt(),
            maxHeartRate = maxHeartRate?.toInt(),
            totalCalories = totalCalories?.toInt(),
        )
}

/** Collects a ride's sensors, gear changes and jumps as the decoder reads them. */
private class RideListeners(broadcaster: MesgBroadcaster) {
    val sensors = mutableListOf<DecodedSensor>()
    val gearChanges = mutableListOf<DecodedGearChange>()
    val jumps = mutableListOf<DecodedJump>()

    init {
        broadcaster.addListener { m: DeviceInfoMesg -> sensors += m.toSensor() }
        broadcaster.addListener { m: EventMesg ->
            val rear = m.event == Event.REAR_GEAR_CHANGE
            if (rear || m.event == Event.FRONT_GEAR_CHANGE) {
                gearChanges +=
                    DecodedGearChange(
                        timestampMillis = m.timestamp?.instant?.toEpochMilli(),
                        rear = rear,
                        frontGear = m.frontGearNum?.toInt(),
                        frontTeeth = m.frontGear?.toInt(),
                        rearGear = m.rearGearNum?.toInt(),
                        rearTeeth = m.rearGear?.toInt(),
                    )
            }
        }
        broadcaster.addListener { m: JumpMesg -> jumps += DecodedJump(m.hangTime?.toDouble(), m.distance?.toDouble()) }
    }

    private fun DeviceInfoMesg.toSensor() =
        DecodedSensor(
            deviceIndex = deviceIndex?.toInt(),
            sourceType = sourceType?.name,
            deviceType =
                when (sourceType) {
                    SourceType.ANTPLUS -> antplusDeviceType?.let(AntplusDeviceType::getStringFromValue)
                    SourceType.BLUETOOTH_LOW_ENERGY -> bleDeviceType?.let(BleDeviceType::getStringFromValue)
                    SourceType.LOCAL -> localDeviceType?.let(LocalDeviceType::getStringFromValue)
                    else -> null
                }?.ifEmpty { null },
            manufacturer = manufacturer?.let(Manufacturer::getStringFromValue)?.ifEmpty { null },
            product = productName(manufacturer, garminProduct, productName, product)?.ifEmpty { null },
            batteryStatus = batteryStatus?.let(BatteryStatus::getStringFromValue)?.ifEmpty { null },
            batteryLevelPercent = batteryLevel?.toInt(),
        )
}

/** The SDK's name for a Garmin product, else the device's own product name, else the product number. */
internal fun productName(manufacturer: Int?, garminProduct: Int?, productName: String?, product: Int?): String? =
    if (manufacturer == Manufacturer.GARMIN) garminProduct?.let(GarminProduct::getStringFromValue) else productName ?: product?.toString()

/** Collects a dive log's dive messages as the decoder reads them, for [FitDives] to interpret. */
private class DiveListeners(broadcaster: MesgBroadcaster) {
    private var settings: DiveSettingsMesg? = null
    private val summaries = mutableListOf<FitDiveSummary>()
    private val gases = mutableListOf<FitDiveGas>()
    private val gasSwitches = mutableListOf<FitGasSwitch>()
    private val tanks = mutableListOf<FitTankSummary>()
    private val tankUpdates = mutableListOf<FitTankUpdate>()

    init {
        broadcaster.addListener { m: DiveSettingsMesg -> if (settings == null) settings = m }
        broadcaster.addListener { m: DiveSummaryMesg -> summaries += m.toDiveSummary() }
        broadcaster.addListener { m: DiveGasMesg ->
            gases += FitDiveGas(m.messageIndex, m.oxygenContent?.toInt(), m.heliumContent?.toInt(), m.status?.name, m.mode?.name)
        }
        broadcaster.addListener { m: EventMesg ->
            if (m.event == Event.DIVE_GAS_SWITCHED)
                m.data?.let { gasSwitches += FitGasSwitch(m.timestamp?.instant?.toEpochMilli(), it.toInt()) }
        }
        broadcaster.addListener { m: TankSummaryMesg ->
            tanks += FitTankSummary(m.startPressure?.toDouble(), m.endPressure?.toDouble(), m.volumeUsed?.toDouble(), m.sensor)
        }
        broadcaster.addListener { m: TankUpdateMesg ->
            tankUpdates += FitTankUpdate(m.timestamp?.instant?.toEpochMilli(), m.sensor, m.pressure?.toDouble())
        }
    }

    /** [session] is the file's first: where the diver went in and came out, and its own dive fields. */
    fun messages(session: SessionMesg?) =
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
            waterType = settings?.waterType?.name,
            waterDensityKgPerM3 = settings?.waterDensity?.toDouble(),
            tankUpdates = tankUpdates,
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
            startN2 = startN2,
            endN2 = endN2,
            avgAscentRate = avgAscentRate?.toDouble(),
            maxAscentRate = maxAscentRate?.toDouble(),
            avgDescentRate = avgDescentRate?.toDouble(),
            maxDescentRate = maxDescentRate?.toDouble(),
            descentTimeSeconds = descentTime?.toDouble(),
        )
}
