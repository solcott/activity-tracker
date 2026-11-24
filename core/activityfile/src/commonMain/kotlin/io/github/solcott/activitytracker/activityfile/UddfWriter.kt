package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityDiveGas
import io.github.solcott.activitytracker.location.toXmlDecimal
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
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
    fun write(activity: ExportActivity, zone: TimeZone, createdAt: Instant = Clock.System.now()): String =
        XmlOutput.document(UDDF_NS, "uddf") {
            val dive = activity.dive
            val waypoints = waypoints(activity)
            val switches = switches(activity, waypoints)
            val alarms = alarms(activity, waypoints)
            val site = SiteInfo(dive?.site, activity.points.firstOrNull { it.hasPosition })
            val tanks = dive?.tanks.orEmpty()
            attr("version", "3.2.3")
            line()
            element("generator") {
                leaf("name", "activity-tracker")
                leaf("type", "logbook")
                leaf("datetime", createdAt)
            }
            activity.device?.let { diver(it) }
            if (site.present) site(site)
            val gases = gases(activity)
            gases(gases)
            line()
            element("profiledata") {
                line()
                element("repetitiongroup") {
                    attr("id", "rg")
                    line()
                    element("dive") {
                        attr("id", "dive")
                        before(activity, zone, site.present)
                        line()
                        element("samples") {
                            waypoints.forEachIndexed { index, point ->
                                waypoint(
                                    activity,
                                    point,
                                    switches[index],
                                    alarms[index].orEmpty(),
                                    closedCircuit = index == 0 && dive?.closedCircuit == true,
                                )
                            }
                            endLine()
                        }
                        tanks.forEachIndexed { index, tank ->
                            line()
                            element("tankdata") {
                                attr("id", tankId(index))
                                tank.gas?.let { link(mixId(it.oxygen, it.helium)) }
                                leaf("tankvolume", tank.volume?.let { (it / MILLILITERS_PER_CUBIC_METER).toXmlDecimal(VOLUME_DECIMALS) })
                                leaf("tankpressurebegin", tank.startPressure?.let { it * PASCALS_PER_MILLIBAR })
                                leaf("tankpressureend", tank.endPressure?.let { it * PASCALS_PER_MILLIBAR })
                            }
                        }
                        gasOnlyTanks(gases, tanks.size, tanks.mapNotNull { it.gas?.pair() }.toSet())
                        after(activity, waypoints)
                        endLine()
                    }
                    endLine()
                }
                endLine()
            }
        }

    private fun XmlOutput.link(ref: String) = element("link") { attr("ref", ref) }

    private class SiteInfo(val name: String?, val position: ExportPoint?) {
        val present = name != null || position != null
    }

    private fun XmlOutput.diver(device: String) {
        line()
        element("diver") {
            element("owner") {
                attr("id", "owner")
                // `personal` and its names are required; the app doesn't keep the diver's, so they stay empty.
                element("personal") {
                    element("firstname")
                    element("lastname")
                }
                element("equipment") {
                    element("divecomputer") {
                        attr("id", COMPUTER_ID)
                        leaf("name", device)
                        leaf("model", device)
                    }
                }
            }
        }
    }

    private fun XmlOutput.site(site: SiteInfo) {
        val name = site.name.orEmpty()
        line()
        element("divesite") {
            element("site") {
                attr("id", SITE_ID)
                leaf("name", name)
                val lat = site.position?.latitude
                val lon = site.position?.longitude
                if (lat != null && lon != null) {
                    element("geography") {
                        leaf("location", name)
                        leaf("latitude", lat.toXmlDecimal(COORDINATE_DECIMALS))
                        leaf("longitude", lon.toXmlDecimal(COORDINATE_DECIMALS))
                    }
                }
            }
        }
    }

    /** The record's gases in order of first use, then each a switch or a tank names that the record lacks (merged from another file). */
    private fun gases(activity: ExportActivity): List<Pair<Int, Int>> {
        val dive = activity.dive
        val tankGases = dive?.tanks.orEmpty().mapNotNull { it.gas }
        val switched = activity.gasSwitches.map { it.oxygen to it.helium }
        return (dive?.gases.orEmpty().map { it.pair() } + switched + tankGases.map { it.pair() }).distinct()
    }

    private fun XmlOutput.gases(gases: List<Pair<Int, Int>>) {
        if (gases.isEmpty()) return
        line()
        element("gasdefinitions") {
            for ((oxygen, helium) in gases) {
                line()
                element("mix") {
                    attr("id", mixId(oxygen, helium))
                    leaf("name", mixName(oxygen, helium))
                    leaf("o2", fraction(oxygen))
                    leaf("he", fraction(helium))
                }
            }
            endLine()
        }
    }

    /**
     * A tank linking each gas no stored tank holds, after the stored ones and with nothing else in it: logbook software (Subsurface) makes
     * a cylinder only from `tankdata` and drops a `switchmix` to a gas no cylinder holds. The schema wants `tankpressurebegin`, but a
     * made-up pressure would read back as one, so it's left out; our own import skips a tank with no pressure, volume or readings.
     */
    private fun XmlOutput.gasOnlyTanks(gases: List<Pair<Int, Int>>, storedTanks: Int, inStoredTanks: Set<Pair<Int, Int>>) {
        gases
            .filterNot { it in inStoredTanks }
            .forEachIndexed { index, (oxygen, helium) ->
                line()
                element("tankdata") {
                    attr("id", tankId(storedTanks + index))
                    link(mixId(oxygen, helium))
                }
            }
    }

    private fun XmlOutput.before(activity: ExportActivity, zone: TimeZone, hasSite: Boolean) {
        val dive = activity.dive
        line()
        element("informationbeforedive") {
            if (hasSite) link(SITE_ID)
            if (activity.device != null) link(COMPUTER_ID)
            leaf("divenumber", dive?.diveNumber?.takeIf { it > 0 })
            leaf("datetime", LOCAL_DATE_TIME.format(activity.startedAt.toLocalDateTime(zone)))
            leaf("airtemperature", dive?.airTemperature?.let { kelvin(it) })
            dive?.surfaceInterval?.let { element("surfaceintervalbeforedive") { leaf("passedtime", it) } }
            leaf("altitude", dive?.altitude)
        }
    }

    /**
     * Children in the schema's sequence. `heartrate` isn't in the 3.2.1 schema; it's written after `divetime`, where the readers we know of
     * take it.
     */
    private fun XmlOutput.waypoint(
        activity: ExportActivity,
        p: ExportPoint,
        switchTo: Pair<Int, Int>?,
        alarms: List<String>,
        closedCircuit: Boolean,
    ) {
        line()
        element("waypoint") {
            alarms.forEach { leaf("alarm", it) }
            leaf("cns", p.cnsPercent)
            p.nextStopDepthMeters?.let { depth ->
                element("decostop") {
                    attr("kind", "mandatory")
                    attr("decodepth", depth.toXmlDecimal(METER_DECIMALS))
                    attr("duration", p.nextStopSeconds ?: 0)
                }
            }
            leaf("calculatedpo2", p.ppO2Bar?.let { (it * PASCALS_PER_BAR).roundToLong() })
            leaf("depth", (p.depthMeters ?: 0.0).toXmlDecimal(METER_DECIMALS))
            leaf("divetime", second(activity, p))
            leaf("heartrate", p.heartRate)
            switchTo?.let { (oxygen, helium) -> element("switchmix") { attr("ref", mixId(oxygen, helium)) } }
            val tankCount = activity.dive?.tanks.orEmpty().size
            p.tankPressuresBar.take(tankCount).forEachIndexed { index, bar ->
                if (bar != null) {
                    element("tankpressure") {
                        attr("ref", tankId(index))
                        text((bar * PASCALS_PER_BAR).roundToLong())
                    }
                }
            }
            leaf("temperature", p.temperatureCelsius?.let { (it + KELVIN_OFFSET).toXmlDecimal(TEMPERATURE_DECIMALS) })
            if (closedCircuit) element("divemode") { attr("type", "closedcircuit") }
            leaf("nodecotime", p.ndlSeconds)
        }
    }

    private fun XmlOutput.after(activity: ExportActivity, waypoints: List<ExportPoint>) {
        val dive = activity.dive
        // Both required: the record's, else the profile's.
        val greatest = dive?.maxDepth?.let { it / CENTI } ?: waypoints.maxOfOrNull { it.depthMeters ?: 0.0 } ?: 0.0
        line()
        element("informationafterdive") {
            leaf("greatestdepth", greatest.toXmlDecimal(METER_DECIMALS))
            leaf("averagedepth", dive?.avgDepth?.let { (it / CENTI).toXmlDecimal(METER_DECIMALS) })
            leaf("diveduration", activity.elapsed.inWholeSeconds)
            leaf("lowesttemperature", dive?.minTemperature?.let { kelvin(it) })
            leaf("visibility", dive?.visibility?.let { (it / CENTI).toXmlDecimal(METER_DECIMALS) })
            leaf("highestpo2", dive?.highestPpO2?.let { it * PASCALS_PER_MILLIBAR })
            leaf("noflighttime", dive?.noFlyTime)
            leaf("desaturationtime", dive?.desaturationTime)
        }
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

    /** The UDDF alarms each waypoint carries, by index: each alert UDDF names, at the first waypoint at or after its second. */
    private fun alarms(activity: ExportActivity, waypoints: List<ExportPoint>): Map<Int, List<String>> {
        val seconds = waypoints.map { second(activity, it) }
        return activity.diveAlerts
            .mapNotNull { alert ->
                val alarm = DiveAlerts.byToken(alert.kind)?.uddfAlarm ?: return@mapNotNull null
                seconds.indexOfFirst { it >= alert.time }.takeIf { it >= 0 }?.let { it to alarm }
            }
            .groupBy({ it.first }, { it.second })
    }

    private fun second(activity: ExportActivity, point: ExportPoint) =
        ((point.time - activity.startedAt).inWholeMilliseconds / MILLIS_PER_SECOND).roundToLong()

    /** `yyyy-MM-ddTHH:mm:ss`, always with seconds, which `LocalDateTime.toString` drops when they're zero. */
    private val LOCAL_DATE_TIME = LocalDateTime.Format {
        date(LocalDate.Formats.ISO)
        char('T')
        hour()
        char(':')
        minute()
        char(':')
        second()
    }

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
    private const val COORDINATE_DECIMALS = 7
    private const val METER_DECIMALS = 2
    private const val TEMPERATURE_DECIMALS = 2
    private const val FRACTION_DECIMALS = 2
    private const val VOLUME_DECIMALS = 6
}
