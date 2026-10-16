package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityDiveGas
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Writes an [ExportActivity] dive as a UDDF 3.2 logbook of one dive in one repetition group, which dive logbook software (and our own
 * [UddfParser]) imports: the dive computer, the site (its name and the first position, which only the owner's export carries), a mix per
 * gas, then the dive's information before, a waypoint per second with a depth (the first point in it), its tanks (plus a gas-only tank for
 * each gas no stored tank holds) and its information after. Children follow the schema's sequence; units are SI (meters, seconds, Kelvin,
 * Pascal, fractions).
 *
 * UDDF's `datetime` has no zone, so the start is written as [write]'s zone shows it. A dive stored without switch times (imported before
 * they were kept) switches to its first gas at the start and defines the rest without switching to them: the file doesn't invent when.
 */
@Suppress("TooManyFunctions") // One per element group it writes, plus unit conversions.
object UddfWriter {
    const val UDDF_NS = "http://www.streit.cc/uddf/3.2/"

    /** [zone] gives the dive's local start; [createdAt] is the generator's time. */
    fun write(activity: ExportActivity, zone: TimeZone, createdAt: Instant = Clock.System.now()): String = buildString {
        val dive = activity.dive
        val waypoints = waypoints(activity)
        val switches = switches(activity, waypoints)
        val site = SiteInfo(dive?.site, activity.points.firstOrNull { it.hasPosition })
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<uddf xmlns="$UDDF_NS" version="3.2.3">""")
        appendLine("  <generator><name>activity-tracker</name><type>logbook</type><datetime>$createdAt</datetime></generator>")
        activity.device?.let { appendDiver(it) }
        if (site.present) appendSite(site)
        val gases = gases(activity)
        appendGases(gases)
        appendLine("""  <profiledata><repetitiongroup id="rg"><dive id="dive">""")
        appendBefore(activity, zone, site.present)
        appendLine("    <samples>")
        waypoints.forEachIndexed { index, point ->
            appendWaypoint(activity, point, switches[index], closedCircuit = index == 0 && dive?.closedCircuit == true)
        }
        appendLine("    </samples>")
        dive?.tanks.orEmpty().forEachIndexed { index, tank ->
            append("""    <tankdata id="${tankId(index)}">""")
            tank.gas?.let { append("""<link ref="${mixId(it.oxygen, it.helium)}"/>""") }
            tank.volume?.let { append("<tankvolume>${(it / MILLILITERS_PER_CUBIC_METER).toXmlDecimal(VOLUME_DECIMALS)}</tankvolume>") }
            tank.startPressure?.let { append("<tankpressurebegin>${it * PASCALS_PER_MILLIBAR}</tankpressurebegin>") }
            tank.endPressure?.let { append("<tankpressureend>${it * PASCALS_PER_MILLIBAR}</tankpressureend>") }
            appendLine("</tankdata>")
        }
        appendGasOnlyTanks(gases, dive?.tanks.orEmpty().size, dive?.tanks.orEmpty().mapNotNull { it.gas?.pair() }.toSet())
        appendAfter(activity, waypoints)
        appendLine("  </dive></repetitiongroup></profiledata>")
        appendLine("</uddf>")
    }

    private class SiteInfo(val name: String?, val position: ExportPoint?) {
        val present = name != null || position != null
    }

    private fun StringBuilder.appendDiver(device: String) {
        val name = device.escapeXml()
        // `personal` and its names are required; the app doesn't keep the diver's, so they stay empty.
        append("""  <diver><owner id="owner"><personal><firstname></firstname><lastname></lastname></personal><equipment>""")
        append("""<divecomputer id="$COMPUTER_ID"><name>$name</name><model>$name</model></divecomputer>""")
        appendLine("</equipment></owner></diver>")
    }

    private fun StringBuilder.appendSite(site: SiteInfo) {
        val name = site.name?.escapeXml().orEmpty()
        append("""  <divesite><site id="$SITE_ID"><name>$name</name>""")
        val lat = site.position?.latitude
        val lon = site.position?.longitude
        if (lat != null && lon != null) {
            append("<geography><location>$name</location>")
            append("<latitude>${lat.toXmlDecimal(COORDINATE_DECIMALS)}</latitude>")
            append("<longitude>${lon.toXmlDecimal(COORDINATE_DECIMALS)}</longitude></geography>")
        }
        appendLine("</site></divesite>")
    }

    /** The record's gases in order of first use, then each a switch or a tank names that the record lacks (merged from another file). */
    private fun gases(activity: ExportActivity): List<Pair<Int, Int>> {
        val dive = activity.dive
        val tankGases = dive?.tanks.orEmpty().mapNotNull { it.gas }
        val switched = activity.gasSwitches.map { it.oxygen to it.helium }
        return (dive?.gases.orEmpty().map { it.pair() } + switched + tankGases.map { it.pair() }).distinct()
    }

    private fun StringBuilder.appendGases(gases: List<Pair<Int, Int>>) {
        if (gases.isEmpty()) return
        appendLine("  <gasdefinitions>")
        for ((oxygen, helium) in gases) {
            append("""    <mix id="${mixId(oxygen, helium)}"><name>${mixName(oxygen, helium)}</name>""")
            append("<o2>${fraction(oxygen)}</o2>")
            append("<he>${fraction(helium)}</he>")
            appendLine("</mix>")
        }
        appendLine("  </gasdefinitions>")
    }

    /**
     * A tank linking each gas no stored tank holds, after the stored ones and with nothing else in it: logbook software (Subsurface) makes
     * a cylinder only from `tankdata` and drops a `switchmix` to a gas no cylinder holds. The schema wants `tankpressurebegin`, but a
     * made-up pressure would read back as one, so it's left out; our own import skips a tank with no pressure, volume or readings.
     */
    private fun StringBuilder.appendGasOnlyTanks(gases: List<Pair<Int, Int>>, storedTanks: Int, inStoredTanks: Set<Pair<Int, Int>>) {
        gases
            .filterNot { it in inStoredTanks }
            .forEachIndexed { index, (oxygen, helium) ->
                appendLine("""    <tankdata id="${tankId(storedTanks + index)}"><link ref="${mixId(oxygen, helium)}"/></tankdata>""")
            }
    }

    private fun StringBuilder.appendBefore(activity: ExportActivity, zone: TimeZone, hasSite: Boolean) {
        val dive = activity.dive
        append("    <informationbeforedive>")
        if (hasSite) append("""<link ref="$SITE_ID"/>""")
        if (activity.device != null) append("""<link ref="$COMPUTER_ID"/>""")
        dive?.diveNumber?.takeIf { it > 0 }?.let { append("<divenumber>$it</divenumber>") }
        append("<datetime>${localDateTime(activity.startedAt.toLocalDateTime(zone))}</datetime>")
        dive?.airTemperature?.let { append("<airtemperature>${kelvin(it)}</airtemperature>") }
        dive?.surfaceInterval?.let { append("<surfaceintervalbeforedive><passedtime>$it</passedtime></surfaceintervalbeforedive>") }
        dive?.altitude?.let { append("<altitude>$it</altitude>") }
        appendLine("</informationbeforedive>")
    }

    /**
     * Children in the schema's sequence. `heartrate` isn't in the 3.2.1 schema; it's written after `divetime`, where the readers we know of
     * take it.
     */
    private fun StringBuilder.appendWaypoint(activity: ExportActivity, p: ExportPoint, switchTo: Pair<Int, Int>?, closedCircuit: Boolean) {
        append("      <waypoint>")
        p.cnsPercent?.let { append("<cns>$it</cns>") }
        p.nextStopDepthMeters?.let { depth ->
            val duration = p.nextStopSeconds ?: 0
            append("""<decostop kind="mandatory" decodepth="${depth.toXmlDecimal(METER_DECIMALS)}" duration="$duration"/>""")
        }
        p.ppO2Bar?.let { append("<calculatedpo2>${(it * PASCALS_PER_BAR).roundToLong()}</calculatedpo2>") }
        append("<depth>${(p.depthMeters ?: 0.0).toXmlDecimal(METER_DECIMALS)}</depth>")
        append("<divetime>${second(activity, p)}</divetime>")
        p.heartRate?.let { append("<heartrate>$it</heartrate>") }
        switchTo?.let { (oxygen, helium) -> append("""<switchmix ref="${mixId(oxygen, helium)}"/>""") }
        val tankCount = activity.dive?.tanks.orEmpty().size
        p.tankPressuresBar.take(tankCount).forEachIndexed { index, bar ->
            if (bar != null) append("""<tankpressure ref="${tankId(index)}">${(bar * PASCALS_PER_BAR).roundToLong()}</tankpressure>""")
        }
        p.temperatureCelsius?.let { append("<temperature>${(it + KELVIN_OFFSET).toXmlDecimal(TEMPERATURE_DECIMALS)}</temperature>") }
        if (closedCircuit) append("""<divemode type="closedcircuit"/>""")
        p.ndlSeconds?.let { append("<nodecotime>$it</nodecotime>") }
        appendLine("</waypoint>")
    }

    private fun StringBuilder.appendAfter(activity: ExportActivity, waypoints: List<ExportPoint>) {
        val dive = activity.dive
        // Both required: the record's, else the profile's.
        val greatest = dive?.maxDepth?.let { it / CENTI } ?: waypoints.maxOfOrNull { it.depthMeters ?: 0.0 } ?: 0.0
        append("    <informationafterdive>")
        append("<greatestdepth>${greatest.toXmlDecimal(METER_DECIMALS)}</greatestdepth>")
        dive?.avgDepth?.let { append("<averagedepth>${(it / CENTI).toXmlDecimal(METER_DECIMALS)}</averagedepth>") }
        append("<diveduration>${activity.elapsed.inWholeSeconds}</diveduration>")
        dive?.minTemperature?.let { append("<lowesttemperature>${kelvin(it)}</lowesttemperature>") }
        dive?.visibility?.let { append("<visibility>${(it / CENTI).toXmlDecimal(METER_DECIMALS)}</visibility>") }
        dive?.highestPpO2?.let { append("<highestpo2>${it * PASCALS_PER_MILLIBAR}</highestpo2>") }
        dive?.noFlyTime?.let { append("<noflighttime>$it</noflighttime>") }
        dive?.desaturationTime?.let { append("<desaturationtime>$it</desaturationtime>") }
        appendLine("</informationafterdive>")
    }

    /** The first point of each second that has a depth: the join can give one stream sample to two track points in a second. */
    private fun waypoints(activity: ExportActivity) = activity.points.filter { it.depthMeters != null }.distinctBy { second(activity, it) }

    /**
     * The gas each waypoint switches to, by index: each stored switch at the first waypoint at or after its second (a later switch on the
     * same waypoint wins), or, with none stored, the record's first gas at the first waypoint.
     */
    private fun switches(activity: ExportActivity, waypoints: List<ExportPoint>): Map<Int, Pair<Int, Int>> {
        val seconds = waypoints.map { second(activity, it) }
        return when {
            waypoints.isEmpty() -> emptyMap()
            activity.gasSwitches.isEmpty() -> activity.dive?.gases?.firstOrNull()?.let { mapOf(0 to it.pair()) }.orEmpty()
            else ->
                buildMap {
                    for (switch in activity.gasSwitches) {
                        val index = seconds.indexOfFirst { it >= switch.time }
                        if (index >= 0) put(index, switch.oxygen to switch.helium)
                    }
                }
        }
    }

    private fun second(activity: ExportActivity, point: ExportPoint) =
        ((point.time - activity.startedAt).inWholeMilliseconds / MILLIS_PER_SECOND).roundToLong()

    /** `yyyy-MM-ddTHH:mm:ss`, always with seconds, which `LocalDateTime.toString` drops when they're zero. */
    private fun localDateTime(t: LocalDateTime) =
        "${t.year.pad(YEAR_DIGITS)}-${(t.month.ordinal + 1).pad()}-${t.day.pad()}T${t.hour.pad()}:${t.minute.pad()}:${t.second.pad()}"

    private fun Int.pad(digits: Int = 2) = toString().padStart(digits, '0')

    private fun ActivityDiveGas.pair() = oxygen to helium

    /** Ids from the percents, so a switch or tank finds its mix without a lookup. */
    private fun mixId(oxygen: Int, helium: Int) = "mix-$oxygen-$helium"

    private fun mixName(oxygen: Int, helium: Int) =
        when {
            helium > 0 -> "$oxygen/$helium"
            oxygen == AIR_OXYGEN -> "Air"
            else -> "EAN$oxygen"
        }

    private fun tankId(index: Int) = "tank$index"

    private fun fraction(percent: Int) = (percent / PERCENT).toXmlDecimal(FRACTION_DECIMALS)

    /** From the lexicon's tenths of a degree Celsius. */
    private fun kelvin(tenths: Long) = (tenths / DECI + KELVIN_OFFSET).toXmlDecimal(TEMPERATURE_DECIMALS)

    private const val SITE_ID = "site"
    private const val COMPUTER_ID = "dc"
    private const val AIR_OXYGEN = 21
    private const val PERCENT = 100.0
    private const val DECI = 10.0
    private const val CENTI = 100.0
    private const val MILLIS_PER_SECOND = 1000.0
    private const val KELVIN_OFFSET = 273.15
    private const val PASCALS_PER_BAR = 100_000.0
    private const val PASCALS_PER_MILLIBAR = 100L
    private const val MILLILITERS_PER_CUBIC_METER = 1_000_000.0
    private const val YEAR_DIGITS = 4
    private const val COORDINATE_DECIMALS = 7
    private const val METER_DECIMALS = 2
    private const val TEMPERATURE_DECIMALS = 2
    private const val FRACTION_DECIMALS = 2
    private const val VOLUME_DECIMALS = 6
}
