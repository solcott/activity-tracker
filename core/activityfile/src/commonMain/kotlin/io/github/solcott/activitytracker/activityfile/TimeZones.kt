package io.github.solcott.activitytracker.activityfile

import kotlinx.datetime.TimeZone

/** The zones an athlete can read a zone-less file's clock in (see [ImportedActivity.localStart]). */
object TimeZones {
    private val REGIONS = setOf("Africa", "America", "Antarctica", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific")

    /** This device's zone, by its region id where the platform knows one. */
    fun device(): TimeZone = deviceTimeZone()

    /** Region ids ("America/Cancun") and UTC, sorted. Leaves out the legacy aliases ("EST", "US/Central", "Etc/GMT+5"). */
    val ids: List<String> by lazy {
        (TimeZone.availableZoneIds.filter { it.substringBefore('/') in REGIONS && '/' in it } + "UTC").distinct().sorted()
    }
}

/** On web the zone rules load from npm, and the system zone needs the browser's name for it. */
internal expect fun deviceTimeZone(): TimeZone
