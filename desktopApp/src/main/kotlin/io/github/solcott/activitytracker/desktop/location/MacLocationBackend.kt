package io.github.solcott.activitytracker.desktop.location

import io.github.solcott.activitytracker.location.GeoPoint
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * macOS Location Services through `ActivityTrackerLocation.app` (`desktopApp/src/macosHelper`, built by `buildMacLocationHelper`): macOS
 * grants location only to an app bundle that says why it wants it, which the JVM running this app isn't. Launched through LaunchServices
 * (`open`), the helper gets its own prompt and grant, writes one location to a file and quits.
 */
class MacLocationBackend(private val helper: File, private val io: CoroutineDispatcher = Dispatchers.IO) : LocationBackend {
    override suspend fun locate(): GeoPoint? {
        val out = File.createTempFile("activitytracker-location", ".json")
        try {
            val exited =
                runInterruptible(io) {
                    val open =
                        ProcessBuilder("open", "-W", "-n", helper.path, "--args", "--out", out.path).redirectErrorStream(true).start()
                    try {
                        open.inputStream.readAllBytes()
                        open.waitFor(HELPER_WAIT_SECONDS, TimeUnit.SECONDS)
                    } finally {
                        open.destroy()
                    }
                }
            return if (exited) parseHelperOutput(out.readText()) else null
        } finally {
            out.delete()
        }
    }

    companion object {
        /** The system property `run` and `hotRun` pass the built helper's path in. */
        const val HELPER_PROPERTY = "activitytracker.macLocationHelper"

        /** The helper gives up after 30 seconds of its own; this only stops a wedged `open`. */
        private const val HELPER_WAIT_SECONDS = 40L

        /** The backend for the helper [HELPER_PROPERTY] names, or null when it's unset or wasn't built. */
        fun fromSystemProperty(): MacLocationBackend? =
            System.getProperty(HELPER_PROPERTY)?.let(::File)?.takeIf { it.isDirectory }?.let(::MacLocationBackend)
    }
}

/** The helper's answer, `{"lat":…,"lng":…}` or `{"error":"…"}`, as a place; null for an error or anything malformed. */
internal fun parseHelperOutput(text: String): GeoPoint? {
    val json = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
    return pointOrNull(json["lat"]?.jsonPrimitive?.doubleOrNull, json["lng"]?.jsonPrimitive?.doubleOrNull)
}
