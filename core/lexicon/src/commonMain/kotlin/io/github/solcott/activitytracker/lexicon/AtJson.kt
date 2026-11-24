package io.github.solcott.activitytracker.lexicon

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The JSON configuration for all lexicon data. `encodeDefaults` is required so records always carry their `$type`; `explicitNulls = false`
 * keeps optional fields out of the payload.
 */
val AtJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    isLenient = true
}

/**
 * [element] decoded by [deserializer], or null when it doesn't decode: any client can write an athlete's records, so a record read back may
 * not be ours. Only a decoding failure is null; anything else (a cancellation, a bug) still throws.
 */
fun <T> Json.decodeOrNull(deserializer: DeserializationStrategy<T>, element: JsonElement): T? = decodingOrNull {
    decodeFromJsonElement(deserializer, element)
}

/** [string] decoded by [deserializer], or null when it isn't JSON or doesn't decode (see the [JsonElement] overload). */
fun <T> Json.decodeOrNull(deserializer: DeserializationStrategy<T>, string: String): T? = decodingOrNull {
    decodeFromString(deserializer, string)
}

// SerializationException is an IllegalArgumentException; both are named so a reader sees what a bad record throws: a malformed or
// mistyped value, or a model's own `require` on what it was given.
@Suppress("SwallowedException")
private inline fun <T> decodingOrNull(decode: () -> T): T? =
    try {
        decode()
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
