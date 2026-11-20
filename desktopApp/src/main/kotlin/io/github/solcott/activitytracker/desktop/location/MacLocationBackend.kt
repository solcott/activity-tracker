package io.github.solcott.activitytracker.desktop.location

import io.github.solcott.activitytracker.app.browse.LATE_LOCATION_LIMIT
import io.github.solcott.activitytracker.location.GeoPoint
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
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
 * (`open`), the helper gets its own prompt and grant, writes one location to a file and quits. The helper only writes into the file this
 * makes (owner-only) and never creates it, so deleting it here, cancelled too, leaves no location on disk however late the helper answers.
 */
class MacLocationBackend(private val helper: File, private val io: CoroutineDispatcher = Dispatchers.IO) : LocationBackend {
    override suspend fun locate(): GeoPoint? {
        val out = Files.createTempFile("activitytracker-location", ".json", OWNER_ONLY).toFile()
        try {
            val exited =
                runInterruptible(io) {
                    val open =
                        ProcessBuilder("open", "-W", "-n", helper.path, "--args", "--out", out.path, "--give-up", "$HELPER_GIVE_UP_SECONDS")
                            .redirectErrorStream(true)
                            .start()
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

        /**
         * How long the helper waits for the athlete and a fix: inside [LATE_LOCATION_LIMIT], the time map browse still listens from
         * opening, less what launching the helper through LaunchServices takes, so an answer the athlete gives in time arrives in time.
         */
        private val HELPER_GIVE_UP_SECONDS = (LATE_LOCATION_LIMIT - HELPER_LAUNCH_MARGIN).inWholeSeconds

        /** Stops only a wedged `open`: the helper has given up well before. */
        private const val HELPER_WAIT_SECONDS = 40L

        private val OWNER_ONLY = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))

        /** The backend for the helper [HELPER_PROPERTY] names, or null when it's unset or wasn't built. */
        fun fromSystemProperty(): MacLocationBackend? =
            System.getProperty(HELPER_PROPERTY)?.let(::File)?.takeIf { it.isDirectory }?.let(::MacLocationBackend)
    }
}

/** Time for `open` and LaunchServices to start the helper before its own wait begins. */
private val HELPER_LAUNCH_MARGIN = 5.seconds

/** The helper's answer, `{"lat":…,"lng":…}` or `{"error":"…"}`, as a place; null for an error or anything malformed. */
internal fun parseHelperOutput(text: String): GeoPoint? {
    val json = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
    return pointOrNull(json["lat"]?.jsonPrimitive?.doubleOrNull, json["lng"]?.jsonPrimitive?.doubleOrNull)
}
