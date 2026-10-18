package io.github.solcott.activitytracker.activityfile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlin.math.roundToInt

/** FIT decoding on the web with Garmin's JavaScript SDK (see GarminFitSdk.kt). */
@ContributesBinding(AppScope::class)
@Inject
class GarminJsFitDecoder : FitDecoder {
    override fun decode(bytes: ByteArray): DecodedActivity {
        val decoder = Decoder(Stream(bufferOf(bytes.toUint8Array())))
        if (!decoder.isFIT()) activityFileError("Not a FIT file")
        if (!decoder.checkIntegrity()) activityFileError("FIT file is corrupt or truncated")
        // Heart rate from hr messages is merged in common code (HrMessages), as on every platform.
        val result = decoder.read(decodeOptions())
        if (result.errors.length > 0) activityFileError("Couldn't read FIT file: ${jsString(result.errors[0]!!)}")
        val messages = result.messages
        val sessions = messages.sessionMesgs?.toList().orEmpty()
        val diveMessages = diveMessages(messages, sessions.firstOrNull())
        val developerFields = developerFieldDescriptions(messages)
        val activity = messages.activityMesgs?.toList()?.firstOrNull()
        return DecodedActivity(
            fileId =
                messages.fileIdMesgs?.toList()?.firstOrNull()?.let {
                    DecodedSource(
                        type = it.type?.let(::jsString),
                        manufacturer = it.manufacturer?.let(::jsString),
                        product = (it.garminProduct ?: it.product)?.let(::jsString) ?: it.productName,
                        timeCreatedMillis = it.timeCreated?.let(::dateMillis),
                    )
                },
            sessions = sessions.map { it.toSummary() },
            laps = messages.lapMesgs?.toList().orEmpty().map { it.toSummary() },
            records = messages.recordMesgs?.toList().orEmpty().map { it.toSample(developerFields) },
            dive =
                FitDives.interpret(
                    diveMessages,
                    sport = sessions.firstOrNull()?.sport?.let(::jsString),
                    subSport = sessions.firstOrNull()?.subSport?.let(::jsString),
                ),
            gasSwitches = FitDives.gasSwitches(diveMessages),
            tankUpdates = FitDives.tankUpdates(diveMessages),
            sensors = messages.deviceInfoMesgs?.toList().orEmpty().map { it.toSensor() },
            gearChanges =
                messages.eventMesgs?.toList().orEmpty().mapNotNull { e ->
                    val event = e.event?.let(::jsString)
                    if (event != "rearGearChange" && event != "frontGearChange") return@mapNotNull null
                    DecodedGearChange(
                        timestampMillis = e.timestamp?.let(::dateMillis),
                        rear = event == "rearGearChange",
                        frontGear = number(e.frontGearNum)?.roundToInt(),
                        frontTeeth = number(e.frontGear)?.roundToInt(),
                        rearGear = number(e.rearGearNum)?.roundToInt(),
                        rearTeeth = number(e.rearGear)?.roundToInt(),
                    )
                },
            jumps = messages.jumpMesgs?.toList().orEmpty().map { DecodedJump(number(it.hangTime), number(it.distance)) },
            athlete = athleteSettings(messages),
            lengths = messages.lengthMesgs?.toList().orEmpty().map { it.toLength() },
            sets = messages.setMesgs?.toList().orEmpty().map { it.toSet() },
            splits = messages.splitMesgs?.toList().orEmpty().map { it.toSplit() },
            hrMessages = messages.hrMesgs?.toList().orEmpty().map { it.toHrMessage() },
            rrIntervalsSeconds = messages.hrvMesgs?.toList().orEmpty().flatMap { m -> numbers(m.time).filter(FitHeart::isValidRr) },
            activityTimestampMillis = activity?.timestamp?.let(::dateMillis),
            activityLocalTimestampSeconds = number(activity?.localTimestamp)?.toLong(),
        )
    }

    /** Each developer field's description, by the key records' `developerFields` use. The SDK leaves their values unscaled. */
    private fun developerFieldDescriptions(messages: FitMessages): Map<String, FitDeveloperFieldDescription> =
        messages.fieldDescriptionMesgs
            ?.toList()
            .orEmpty()
            .mapNotNull { d ->
                val key = d.key?.let(::jsString) ?: return@mapNotNull null
                key to
                    FitDeveloperFieldDescription(
                        name = d.fieldName?.let(::firstOf)?.let(::jsString),
                        units = d.units?.let(::firstOf)?.let(::jsString),
                        scale = number(d.scale),
                        offset = number(d.offset),
                    )
            }
            .toMap()

    private fun athleteSettings(messages: FitMessages): DecodedAthleteSettings? {
        val zones = messages.zonesTargetMesgs?.toList()?.firstOrNull()
        val profile = messages.userProfileMesgs?.toList()?.firstOrNull()
        if (zones == null && profile == null) return null
        return DecodedAthleteSettings(
            maxHeartRate = zones?.let { number(it.maxHeartRate) }?.roundToInt(),
            thresholdHeartRate = zones?.let { number(it.thresholdHeartRate) }?.roundToInt(),
            functionalThresholdPower = zones?.let { number(it.functionalThresholdPower) }?.roundToInt(),
            restingHeartRate = profile?.let { number(it.restingHeartRate) }?.roundToInt(),
        )
    }

    private fun JsDeviceInfoMesg.toSensor() =
        DecodedSensor(
            deviceIndex = number(deviceIndex)?.roundToInt(),
            sourceType = sourceType?.let(::jsString),
            deviceType = (antplusDeviceType ?: bleDeviceType ?: localDeviceType)?.let(::jsString),
            manufacturer = manufacturer?.let(::jsString),
            product = garminProduct?.let(::jsString) ?: productName ?: product?.let(::jsString),
            batteryStatus = batteryStatus?.let(::jsString),
            batteryLevelPercent = number(batteryLevel)?.roundToInt(),
        )

    private fun diveMessages(messages: FitMessages, session: JsSummaryMesg?): FitDiveMessages {
        val settings = messages.diveSettingsMesgs?.toList()?.firstOrNull()
        return FitDiveMessages(
            summaries = messages.diveSummaryMesgs?.toList().orEmpty().map { it.toDiveSummary() },
            gradientFactorLow = number(settings?.gfLow)?.roundToInt(),
            gradientFactorHigh = number(settings?.gfHigh)?.roundToInt(),
            gases =
                messages.diveGasMesgs?.toList().orEmpty().map {
                    FitDiveGas(
                        index = number(it.messageIndex)?.roundToInt(),
                        oxygenPercent = number(it.oxygenContent)?.roundToInt(),
                        heliumPercent = number(it.heliumContent)?.roundToInt(),
                        status = it.status?.let(::jsString),
                        mode = it.mode?.let(::jsString),
                    )
                },
            gasSwitches =
                messages.eventMesgs?.toList().orEmpty().mapNotNull { e ->
                    number(e.data)
                        ?.roundToInt()
                        ?.takeIf { e.event?.let(::jsString) == "diveGasSwitched" }
                        ?.let { FitGasSwitch(e.timestamp?.let(::dateMillis), it) }
                },
            tanks =
                messages.tankSummaryMesgs?.toList().orEmpty().map {
                    FitTankSummary(number(it.startPressure), number(it.endPressure), number(it.volumeUsed), number(it.sensor)?.toLong())
                },
            entry = position(session?.startPositionLat, session?.startPositionLong),
            exit = position(session?.endPositionLat, session?.endPositionLong),
            sessionMaxDepthMeters = number(session?.maxDepth),
            sessionAvgDepthMeters = number(session?.avgDepth),
            sessionDiveNumber = number(session?.diveNumber)?.roundToInt(),
            waterType = settings?.waterType?.let(::jsString),
            waterDensityKgPerM3 = number(settings?.waterDensity),
            tankUpdates =
                messages.tankUpdateMesgs?.toList().orEmpty().map {
                    FitTankUpdate(it.timestamp?.let(::dateMillis), number(it.sensor)?.toLong(), number(it.pressure))
                },
        )
    }

    private fun JsRecordMesg.toSample(descriptions: Map<String, FitDeveloperFieldDescription>) =
        DecodedSample(
            timestampMillis = timestamp?.let(::dateMillis),
            latitude = number(positionLat)?.let { s -> FitUnits.semicirclesToDegrees(s.toInt()) },
            longitude = number(positionLong)?.let { s -> FitUnits.semicirclesToDegrees(s.toInt()) },
            altitudeMeters = number(enhancedAltitude) ?: number(altitude),
            heartRate = number(heartRate)?.roundToInt(),
            cadence = number(cadence)?.roundToInt(),
            powerWatts = number(power)?.roundToInt(),
            speedMps = number(enhancedSpeed) ?: number(speed),
            distanceMeters = number(distance),
            temperatureCelsius = number(temperature),
            depthMeters = number(depth),
            ppO2Bar = number(po2),
            cnsPercent = number(cnsLoad)?.roundToInt(),
            n2LoadPercent = number(n2Load)?.roundToInt(),
            ndlSeconds = number(ndlTime)?.toLong(),
            nextStopDepthMeters = number(nextStopDepth),
            nextStopSeconds = number(nextStopTime)?.toLong(),
            ttsSeconds = number(timeToSurface)?.toLong(),
            respirationRate = number(enhancedRespirationRate) ?: number(respirationRate),
            verticalOscillationMm = number(verticalOscillation),
            stanceTimeMs = number(stanceTime),
            stepLengthMm = number(stepLength),
            verticalRatioPercent = number(verticalRatio),
            stanceTimeBalancePercent = number(stanceTimeBalance),
            leftRightBalance = number(leftRightBalance)?.roundToInt(),
            leftTorqueEffectiveness = number(leftTorqueEffectiveness),
            rightTorqueEffectiveness = number(rightTorqueEffectiveness),
            leftPedalSmoothness = number(leftPedalSmoothness),
            rightPedalSmoothness = number(rightPedalSmoothness),
            leftPcoMm = number(leftPco)?.roundToInt(),
            rightPcoMm = number(rightPco)?.roundToInt(),
            coreTemperatureCelsius = number(coreTemperature),
            saturatedHemoglobinPercent = number(saturatedHemoglobinPercent),
            totalHemoglobinConc = number(totalHemoglobinConc),
            ebikeBatteryPercent = number(ebikeBatteryLevel)?.roundToInt(),
            ebikeAssistMode = number(ebikeAssistMode)?.roundToInt(),
            ebikeAssistLevelPercent = number(ebikeAssistLevelPercent)?.roundToInt(),
            developerFields =
                developerFields
                    ?.let { fields ->
                        descriptions.mapNotNull { (key, description) ->
                            number(fieldOf(fields, key)?.let(::firstOf))?.let(description::field)
                        }
                    }
                    .orEmpty(),
        )

    private fun JsDiveSummaryMesg.toDiveSummary() =
        FitDiveSummary(
            referenceMesg = referenceMesg?.let(::jsString),
            avgDepthMeters = number(avgDepth),
            maxDepthMeters = number(maxDepth),
            surfaceIntervalSeconds = number(surfaceInterval),
            diveNumber = number(diveNumber)?.roundToInt(),
            bottomTimeSeconds = number(bottomTime),
            startCns = number(startCns)?.roundToInt(),
            endCns = number(endCns)?.roundToInt(),
            oxygenToxicityOtu = number(o2Toxicity)?.roundToInt(),
            avgRmv = number(avgRmv),
            avgVolumeSac = number(avgVolumeSac),
            avgPressureSac = number(avgPressureSac),
            startN2 = number(startN2)?.roundToInt(),
            endN2 = number(endN2)?.roundToInt(),
            avgAscentRate = number(avgAscentRate),
            maxAscentRate = number(maxAscentRate),
            avgDescentRate = number(avgDescentRate),
            maxDescentRate = number(maxDescentRate),
            descentTimeSeconds = number(descentTime),
        )

    private fun position(lat: JsAny?, lon: JsAny?): DivePosition? {
        val latitude = number(lat)
        val longitude = number(lon)
        return if (latitude == null || longitude == null) null
        else DivePosition(FitUnits.semicirclesToDegrees(latitude.toInt()), FitUnits.semicirclesToDegrees(longitude.toInt()))
    }

    private fun JsSummaryMesg.toSummary() =
        DecodedSummary(
            sport = sport?.let(::jsString),
            subSport = subSport?.let(::jsString),
            startTimeMillis = startTime?.let(::dateMillis),
            totalElapsedSeconds = number(totalElapsedTime),
            totalTimerSeconds = number(totalTimerTime),
            totalDistanceMeters = number(totalDistance),
            totalAscentMeters = number(totalAscent),
            avgSpeedMps = number(enhancedAvgSpeed) ?: number(avgSpeed),
            maxSpeedMps = number(enhancedMaxSpeed) ?: number(maxSpeed),
            avgHeartRate = number(avgHeartRate)?.roundToInt(),
            maxHeartRate = number(maxHeartRate)?.roundToInt(),
            avgPowerWatts = number(avgPower)?.roundToInt(),
            avgCadence = number(avgCadence)?.roundToInt(),
            avgTemperatureCelsius = number(avgTemperature),
            maxPowerWatts = number(maxPower)?.roundToInt(),
            normalizedPowerWatts = number(normalizedPower)?.roundToInt(),
            maxCadence = number(maxCadence)?.roundToInt(),
            totalDescentMeters = number(totalDescent),
            totalWorkJoules = number(totalWork),
            thresholdPowerWatts = number(thresholdPower)?.roundToInt(),
            intensityFactor = number(intensityFactor),
            trainingStressScore = number(trainingStressScore),
            frontShiftCount = number(frontGearShiftCount)?.roundToInt(),
            rearShiftCount = number(rearGearShiftCount)?.roundToInt(),
            jumpCount = number(jumpCount)?.roundToInt(),
            totalGrit = number(totalGrit),
            avgFlow = number(avgFlow),
            totalCalories = number(totalCalories)?.roundToInt(),
            aerobicTrainingEffect = number(totalTrainingEffect),
            anaerobicTrainingEffect = number(totalAnaerobicTrainingEffect),
            poolLengthMeters = number(poolLength),
            totalCycles = number(totalCycles)?.toLong(),
            avgStrokeDistanceMeters = number(avgStrokeDistance),
            intensity = intensity?.let(::jsString),
            lapTrigger = lapTrigger?.let(::jsString),
        )

    private fun <T : JsAny> JsArray<T>.toList(): List<T> = (0 until length).mapNotNull { get(it) }
}

private fun JsLengthMesg.toLength() =
    DecodedLength(
        startTimeMillis = startTime?.let(::dateMillis),
        totalElapsedSeconds = number(totalElapsedTime),
        swimStroke = swimStroke?.let(::jsString),
        totalStrokes = number(totalStrokes)?.roundToInt(),
        lengthType = lengthType?.let(::jsString),
    )

private fun JsSetMesg.toSet(): DecodedSet {
    val category = category?.let(::firstOf)
    // A category newer than the profile stays a number, which names no exercise-name type.
    val categoryName = category?.let(::jsString)
    val subtype = number(categorySubtype?.let(::firstOf))?.roundToInt()
    return DecodedSet(
        startTimeMillis = startTime?.let(::dateMillis),
        timestampMillis = timestamp?.let(::dateMillis),
        durationSeconds = number(duration),
        setType = setType?.let(::jsString),
        category = categoryName,
        exercise = if (categoryName != null && subtype != null) exerciseName(Profile.types, categoryName, subtype) else null,
        repetitions = number(repetitions)?.roundToInt(),
        weightKg = number(weight),
    )
}

private fun JsSplitMesg.toSplit() =
    DecodedSplit(
        splitType = splitType?.let(::jsString),
        startTimeMillis = startTime?.let(::dateMillis),
        totalElapsedSeconds = number(totalElapsedTime),
        totalTimerSeconds = number(totalTimerTime),
        totalDistanceMeters = number(totalDistance),
        totalAscentMeters = number(totalAscent),
        totalDescentMeters = number(totalDescent),
        avgSpeedMps = number(avgSpeed),
        maxSpeedMps = number(maxSpeed),
        avgHeartRate = number(avgHeartRate)?.roundToInt(),
        maxHeartRate = number(maxHeartRate)?.roundToInt(),
        totalCalories = number(totalCalories)?.roundToInt(),
    )

@Suppress("UnusedParameter") // Used by the JS body.
private fun bufferOf(array: JsAny): JsAny = js("array.buffer")

@Suppress("UnusedParameter") // Used by the JS body.
private fun jsString(value: JsAny): String = js("String(value)")

/** An array field's first value; a single value as is. Null when the array is empty. */
@Suppress("UnusedParameter") // Used by the JS body.
private fun firstOf(value: JsAny): JsAny? = js("Array.isArray(value) ? (value.length > 0 ? value[0] : null) : value")

/** The name of exercise [subtype] in [category]'s exercise-name type (`benchPressExerciseName`), or null when it has none. */
@Suppress("UnusedParameter") // Used by the JS body.
private fun exerciseName(types: JsAny, category: String, subtype: Int): String? =
    js("(types[category + 'ExerciseName'] || {})[subtype] ?? null")

/** A numeric field, or null when it's absent (undefined) or not a number. */
internal fun number(value: JsAny?): Double? = if (value == null) null else jsNumberOrNaN(value).takeUnless { it.isNaN() }

@Suppress("UnusedParameter") // Used by the JS body.
private fun jsNumberOrNaN(value: JsAny): Double = js("typeof value === 'number' ? value : NaN")

internal fun dateMillis(date: JsAny): Long = jsDateMillis(date).toLong()

@Suppress("UnusedParameter") // Used by the JS body.
private fun jsDateMillis(date: JsAny): Double = js("date instanceof Date ? date.getTime() : Number(date)")
