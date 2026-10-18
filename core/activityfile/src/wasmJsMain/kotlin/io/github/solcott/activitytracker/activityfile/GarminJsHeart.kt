package io.github.solcott.activitytracker.activityfile

import kotlin.math.roundToInt

// Heart rate from hr messages, and the JS helpers it and developer fields need, for GarminJsFitDecoder.

internal fun JsHrMesg.toHrMessage(): DecodedHrMessage {
    val readings =
        numbers(eventTimestamp).zip(numbers(filteredBpm).map { it.roundToInt() }).filter { (time, bpm) ->
            FitHeart.isValidReading(time, bpm)
        }
    return DecodedHrMessage(
        timestampMillis = timestamp?.let(::dateMillis),
        fractionalSeconds = number(fractionalTimestamp),
        eventTimestamps = readings.map { it.first },
        filteredBpm = readings.map { it.second },
    )
}

/** The SDK's decode options: everything at its default except `mergeHeartRates`. */
internal fun decodeOptions(): JsAny = js("({ mergeHeartRates: false })")

/** An array field's numbers, or a single number as a list of one; anything else (null, a padding value the SDK left undefined) dropped. */
internal fun numbers(value: JsAny?): List<Double> =
    if (value == null) emptyList() else (0 until jsArrayLength(value)).mapNotNull { number(jsArrayAt(value, it)) }

@Suppress("UnusedParameter") // Used by the JS body.
private fun jsArrayLength(value: JsAny): Int = js("Array.isArray(value) ? value.length : 1")

@Suppress("UnusedParameter") // Used by the JS body.
private fun jsArrayAt(value: JsAny, index: Int): JsAny? = js("Array.isArray(value) ? (value[index] ?? null) : value")

@Suppress("UnusedParameter") // Used by the JS body.
internal fun fieldOf(fields: JsAny, key: String): JsAny? = js("fields[key] ?? null")
