package io.github.solcott.activitytracker.activityfile

/**
 * Decodes a FIT file's activity messages with the platform's Garmin FIT SDK: [GarminJavaFitDecoder] on Android, desktop and the AppView,
 * `GarminJsFitDecoder` on web, and `SwiftFitDecoder` (in iosApp, passed into the iOS graph) on iOS. Implementations only translate the
 * SDK's messages into [DecodedActivity]; [DecodedActivityMapper] does the interpreting, so it stays the same everywhere.
 *
 * This is an interface rather than expect/actual because the iOS implementation is Swift.
 *
 * Every field the decoders read is in each pinned SDK's profile (Java and JS 21.218, Swift 21.217), dive state, running dynamics, power
 * balance, `tank_update` and the dive summary's N₂, rates and descent time included; a field an SDK lacked would be passed as null there.
 */
fun interface FitDecoder {
    /**
     * Blocking and CPU-bound: call off the main thread. Throws [ActivityFileException] for an unreadable file; on iOS an error thrown by
     * the Swift implementation arrives as another exception type, so callers catch broadly.
     */
    @Throws(ActivityFileException::class) fun decode(bytes: ByteArray): DecodedActivity
}

/** The file isn't a readable activity: not FIT, corrupt, truncated, or a FIT file of another type (a course, settings, ...). */
open class ActivityFileException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The file is none of the formats we read (FIT, GPX, TCX, a dive log, or one of those gzipped). */
class UnsupportedActivityFileException : ActivityFileException("Not a FIT, GPX, TCX, Shearwater, UDDF or Subsurface file")

/** Throws [ActivityFileException]; decoders and mappers fail through this. */
internal fun activityFileError(message: String, cause: Throwable? = null): Nothing = throw ActivityFileException(message, cause)
