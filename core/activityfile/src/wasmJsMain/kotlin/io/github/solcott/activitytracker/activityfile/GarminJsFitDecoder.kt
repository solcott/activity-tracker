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
        val result = decoder.read()
        if (result.errors.length > 0) activityFileError("Couldn't read FIT file: ${jsString(result.errors[0]!!)}")
        val messages = result.messages
        val sessions = messages.sessionMesgs?.toList().orEmpty()
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
            records =
                messages.recordMesgs?.toList().orEmpty().map {
                    DecodedSample(
                        timestampMillis = it.timestamp?.let(::dateMillis),
                        latitude = number(it.positionLat)?.let { s -> FitUnits.semicirclesToDegrees(s.toInt()) },
                        longitude = number(it.positionLong)?.let { s -> FitUnits.semicirclesToDegrees(s.toInt()) },
                        altitudeMeters = number(it.enhancedAltitude) ?: number(it.altitude),
                        heartRate = number(it.heartRate)?.roundToInt(),
                        cadence = number(it.cadence)?.roundToInt(),
                        powerWatts = number(it.power)?.roundToInt(),
                        speedMps = number(it.enhancedSpeed) ?: number(it.speed),
                        distanceMeters = number(it.distance),
                        temperatureCelsius = number(it.temperature),
                        depthMeters = number(it.depth),
                    )
                },
            dive = dive(messages, sessions.firstOrNull()),
        )
    }

    private fun dive(messages: FitMessages, session: JsSummaryMesg?): DecodedDive? {
        val settings = messages.diveSettingsMesgs?.toList()?.firstOrNull()
        return FitDives.interpret(
            FitDiveMessages(
                summaries =
                    messages.diveSummaryMesgs?.toList().orEmpty().map {
                        FitDiveSummary(
                            referenceMesg = it.referenceMesg?.let(::jsString),
                            avgDepthMeters = number(it.avgDepth),
                            maxDepthMeters = number(it.maxDepth),
                            surfaceIntervalSeconds = number(it.surfaceInterval),
                            diveNumber = number(it.diveNumber)?.roundToInt(),
                            bottomTimeSeconds = number(it.bottomTime),
                            startCns = number(it.startCns)?.roundToInt(),
                            endCns = number(it.endCns)?.roundToInt(),
                            oxygenToxicityOtu = number(it.o2Toxicity)?.roundToInt(),
                            avgRmv = number(it.avgRmv),
                            avgVolumeSac = number(it.avgVolumeSac),
                            avgPressureSac = number(it.avgPressureSac),
                        )
                    },
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
                        number(e.data)?.roundToInt()?.takeIf { e.event?.let(::jsString) == "diveGasSwitched" }
                    },
                tanks =
                    messages.tankSummaryMesgs?.toList().orEmpty().map {
                        FitTankSummary(number(it.startPressure), number(it.endPressure), number(it.volumeUsed))
                    },
                entry = position(session?.startPositionLat, session?.startPositionLong),
                exit = position(session?.endPositionLat, session?.endPositionLong),
                sessionMaxDepthMeters = number(session?.maxDepth),
                sessionAvgDepthMeters = number(session?.avgDepth),
                sessionDiveNumber = number(session?.diveNumber)?.roundToInt(),
            ),
            sport = session?.sport?.let(::jsString),
            subSport = session?.subSport?.let(::jsString),
        )
    }

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
        )

    private fun <T : JsAny> JsArray<T>.toList(): List<T> = (0 until length).mapNotNull { get(it) }
}

@Suppress("UnusedParameter") // Used by the JS body.
private fun bufferOf(array: JsAny): JsAny = js("array.buffer")

@Suppress("UnusedParameter") // Used by the JS body.
private fun jsString(value: JsAny): String = js("String(value)")

/** A numeric field, or null when it's absent (undefined) or not a number. */
private fun number(value: JsAny?): Double? = if (value == null) null else jsNumberOrNaN(value).takeUnless { it.isNaN() }

@Suppress("UnusedParameter") // Used by the JS body.
private fun jsNumberOrNaN(value: JsAny): Double = js("typeof value === 'number' ? value : NaN")

private fun dateMillis(date: JsAny): Long = jsDateMillis(date).toLong()

@Suppress("UnusedParameter") // Used by the JS body.
private fun jsDateMillis(date: JsAny): Double = js("date instanceof Date ? date.getTime() : Number(date)")
