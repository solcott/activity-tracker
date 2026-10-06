package io.github.solcott.activitytracker.activityfile

import kotlinx.datetime.IllegalTimeZoneException
import kotlinx.datetime.TimeZone

/**
 * Importing @js-joda/timezone registers the zone database with @js-joda/core, where kotlinx-datetime reads it. Without it only UTC, fixed
 * offsets and an unnamed system zone work.
 */
@JsModule("@js-joda/timezone") private external object JsJodaTimezone : JsAny

/** Kotlin/Wasm imports an npm module only for an external that code uses; this is that use. */
@Suppress("UnusedParameter") // The js() body reads it.
private fun loaded(module: JsAny): Boolean = js("module !== undefined")

private fun browserZoneId(): String = js("Intl.DateTimeFormat().resolvedOptions().timeZone")

internal actual fun deviceTimeZone(): TimeZone {
    check(loaded(JsJodaTimezone)) { "No time zone database" }
    // kotlinx-datetime names the browser's zone "SYSTEM", which isn't in the list the athlete picks from.
    return try {
        TimeZone.of(browserZoneId())
    } catch (_: IllegalTimeZoneException) {
        TimeZone.currentSystemDefault()
    }
}
