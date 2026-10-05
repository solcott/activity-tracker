package io.github.solcott.activitytracker.activityfile

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlin.math.roundToInt

/** FIT decoding on the web with Garmin's JavaScript SDK (see GarminFitSdk.kt). */
@ContributesBinding(AppScope::class)
@Inject
class GarminJsFitDecoder : FitDecoder {
    override fun decode(bytes: ByteArray): FitActivity {
        val decoder = Decoder(Stream(bytes.toArrayBuffer()))
        if (!decoder.isFIT()) activityFileError("Not a FIT file")
        if (!decoder.checkIntegrity()) activityFileError("FIT file is corrupt or truncated")
        val result = decoder.read()
        if (result.errors.length > 0) activityFileError("Couldn't read FIT file: ${jsString(result.errors[0]!!)}")
        val messages = result.messages
        return FitActivity(
            fileId =
                messages.fileIdMesgs?.toList()?.firstOrNull()?.let {
                    FitFileId(
                        type = it.type?.let(::jsString),
                        manufacturer = it.manufacturer?.let(::jsString),
                        product = (it.garminProduct ?: it.product)?.let(::jsString) ?: it.productName,
                        timeCreatedMillis = it.timeCreated?.let(::dateMillis),
                    )
                },
            sessions = messages.sessionMesgs?.toList().orEmpty().map { it.toSummary() },
            laps = messages.lapMesgs?.toList().orEmpty().map { it.toSummary() },
            records =
                messages.recordMesgs?.toList().orEmpty().map {
                    FitRecord(
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
                    )
                },
        )
    }

    private fun JsSummaryMesg.toSummary() =
        FitSummary(
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

    private fun ByteArray.toArrayBuffer(): JsAny {
        val array = newUint8Array(size)
        forEachIndexed { i, b -> setByte(array, i, b) }
        return bufferOf(array)
    }
}

@Suppress("UnusedParameter") // Used by the JS body.
private fun newUint8Array(size: Int): JsAny = js("new Uint8Array(size)")

@Suppress("UnusedParameter") // Used by the JS body. A negative byte wraps into 0..255 on assignment to a Uint8Array.
private fun setByte(array: JsAny, index: Int, value: Byte) {
    js("array[index] = value")
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
