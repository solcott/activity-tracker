package io.github.solcott.activitytracker.lexicon

import kotlinx.serialization.json.Json

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
