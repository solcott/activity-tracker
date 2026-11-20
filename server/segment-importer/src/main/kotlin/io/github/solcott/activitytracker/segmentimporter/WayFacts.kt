package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.lexicon.app.TrailDog
import io.github.solcott.activitytracker.lexicon.app.TrailHorse
import io.github.solcott.activitytracker.lexicon.app.TrailPisteDifficulty
import io.github.solcott.activitytracker.lexicon.app.TrailPisteGrooming
import io.github.solcott.activitytracker.lexicon.app.TrailSacScale
import io.github.solcott.activitytracker.lexicon.app.TrailSeason
import io.github.solcott.activitytracker.lexicon.app.TrailSmoothness
import io.github.solcott.activitytracker.lexicon.app.TrailSurfaces
import io.github.solcott.activitytracker.lexicon.app.TrailVisibility
import io.github.solcott.activitytracker.lexicon.app.TrailWheelchair
import java.util.Locale
import kotlin.math.roundToInt

/**
 * What one way says about its trail (segments/trail-attributes), already in the attributes' vocabulary, before [TrailAttributeCombiner]
 * combines the ways a line follows. Each source's [TrailRules.facts] reads its own tags into it; null is unknown.
 */
internal data class WayFacts(
    /** A [TrailSurfaces] value. */
    val surface: String? = null,
    val smoothness: String? = null,
    val sacScale: String? = null,
    val mtbScale: Int? = null,
    val mtbScaleImba: Int? = null,
    val mtbScaleUphill: Int? = null,
    val pisteDifficulty: String? = null,
    val pisteGrooming: String? = null,
    val trailClass: Int? = null,
    val visibility: String? = null,
    val widthCm: Int? = null,
    val gradePercent: Int? = null,
    /** Lit at night (any `lit` but `no`), or not. */
    val lit: Boolean? = null,
    val dog: String? = null,
    val horse: String? = null,
    val wheelchair: String? = null,
    /** Open only part of the year, or all of it (`seasonal=no`). */
    val seasonal: Boolean? = null,
    /** The [TrailSeason]s it is open in. */
    val seasons: Set<String>? = null,
    val open: OpenDates? = null,
    val operator: String? = null,
    /** An http or https URL. */
    val website: String? = null,
) {
    companion object {
        val NONE = WayFacts()
    }
}

/** The days a trail is open each year, each `MM-DD`. */
internal data class OpenDates(val from: String, val until: String) {
    /** How many days of a year it spans, wrapping past the new year when [until] is before [from]. */
    fun days(): Int {
        val start = dayOfYear(from)
        val end = dayOfYear(until)
        return if (end >= start) end - start + 1 else YEAR_DAYS - start + end + 1
    }

    private companion object {
        const val YEAR_DAYS = 366
        val BEFORE = intArrayOf(0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335)

        fun dayOfYear(monthDay: String): Int = BEFORE[monthDay.take(2).toInt() - 1] + monthDay.takeLast(2).toInt()
    }
}

/** The shared readers of tag and field values into [WayFacts]' vocabulary. */
internal object FactValues {
    private const val CM_PER_M = 100.0
    private const val CM_PER_FOOT = 30.48
    private const val CM_PER_INCH = 2.54
    private val LEADING_DIGIT = Regex("""^\s*(\d)""")
    private const val NUMBER = """(\d+(?:\.\d+)?)"""
    private val METRIC = Regex("""^$NUMBER\s*(cm|m|ft)?$""")
    private val FEET_INCHES = Regex("""^$NUMBER\s*'\s*(?:$NUMBER\s*")?$""")
    private val INCHES = Regex("""^$NUMBER\s*"$""")
    private val HTTP = Regex("""^https?://\S+$""", RegexOption.IGNORE_CASE)

    /** Field values that say nothing: blanks, `N/A`, `None`, `Unknown`, `Not Applicable` and `N`. */
    private val NOTHING = setOf("", "n/a", "na", "none", "unknown", "not applicable", "n", "null")

    /** [value] trimmed and lower-cased, or null when it says nothing (segments/trail-attributes). */
    fun meaningful(value: String?): String? = value?.trim()?.lowercase()?.takeUnless { it in NOTHING }

    /** [value] in [vocabulary], or null. */
    fun word(value: String?, vocabulary: List<String>): String? = value?.trim()?.takeIf { it in vocabulary }

    /** The leading digit of [value] (`2+` is 2), within [range]; null otherwise. */
    fun leadingDigit(value: String?, range: IntRange): Int? =
        value?.let { LEADING_DIGIT.find(it) }?.groupValues?.get(1)?.toInt()?.takeIf { it in range }

    /** A `width`: metres unless it names `cm`, `m`, `ft`, `'` or `"`, in whole centimetres; null when it isn't one. */
    fun widthCm(value: String?): Int? {
        val text = value?.trim()?.lowercase()?.replace(',', '.') ?: return null
        val cm =
            METRIC.matchEntire(text)?.let { match ->
                val n = match.groupValues[1].toDouble()
                when (match.groupValues[2]) {
                    "cm" -> n
                    "ft" -> n * CM_PER_FOOT
                    else -> n * CM_PER_M
                }
            }
                ?: FEET_INCHES.matchEntire(text)?.let { match ->
                    match.groupValues[1].toDouble() * CM_PER_FOOT + (match.groupValues[2].toDoubleOrNull() ?: 0.0) * CM_PER_INCH
                }
                ?: INCHES.matchEntire(text)?.let { it.groupValues[1].toDouble() * CM_PER_INCH }
        return cm?.let(::positiveCm)
    }

    /** [inches] in whole centimetres, or null when it isn't a positive width. */
    fun inchesToCm(inches: Double): Int? = positiveCm(inches * CM_PER_INCH)

    private fun positiveCm(cm: Double): Int? = cm.roundToInt().takeIf { it in 1..TrailAttributes.MAX_WIDTH_CM }

    /** [value] trimmed, when it is an absolute http or https URL the lexicon holds. */
    fun website(value: String?): String? =
        value?.trim()?.takeIf {
            it.length <= TrailAttributes.MAX_WEBSITE_LENGTH && HTTP.matches(it) && it.substringAfter("://").isNotEmpty()
        }

    /** [value] trimmed, when it fits `trailAttributes#operator`. */
    fun operator(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() && it.length <= TrailAttributes.MAX_OPERATOR_LENGTH && CandidateCutter.truncate(it) == it }
}

/** The open dates tags and fields give (segments/trail-attributes), as [OpenDates]. */
internal object FactDates {
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val MONTH_DAYS = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    private val MONTH_RANGE = Regex("""^([A-Za-z]{3})(?:\s+(\d{1,2}))?\s*-\s*([A-Za-z]{3})(?:\s+(\d{1,2}))?$""")
    private const val FROM_MONTH = 1
    private const val FROM_DAY = 2
    private const val UNTIL_MONTH = 3
    private const val UNTIL_DAY = 4
    private const val WHOLE_YEAR_FROM = "01-01"
    private const val WHOLE_YEAR_UNTIL = "12-31"

    /** One month or month-day range such as `Apr-Nov` or `May 15-Oct 31`, short of the whole year; null for any other form. */
    @Suppress("ReturnCount") // Each way a value can fail to be a range.
    fun monthRange(value: String?): OpenDates? {
        val match = value?.trim()?.let(MONTH_RANGE::matchEntire) ?: return null
        val parts = match.groupValues
        val from = monthDay(parts[FROM_MONTH], parts[FROM_DAY].toIntOrNull() ?: 1) ?: return null
        val until = monthDay(parts[UNTIL_MONTH], parts[UNTIL_DAY].toIntOrNull()) ?: return null
        return dates(from, until)
    }

    /** `MM/DD-MM/DD`, as the Forest Service writes a use's season, short of the whole year; null for any other form. */
    @Suppress("ReturnCount") // Each way a value can fail to be a range.
    fun slashRange(value: String?): OpenDates? {
        val parts = value?.trim()?.split('-')?.takeIf { it.size == 2 } ?: return null
        val (from, until) =
            parts.map { part ->
                val (month, day) = part.trim().split('/').takeIf { it.size == 2 }?.map { it.toIntOrNull() } ?: return null
                monthDay(month ?: return null, day ?: return null) ?: return null
            }
        return dates(from, until)
    }

    private fun dates(from: String, until: String): OpenDates? =
        OpenDates(from, until).takeUnless { from == WHOLE_YEAR_FROM && until == WHOLE_YEAR_UNTIL }

    /** [month] (a three-letter name) and [day], or the month's last day when null, as `MM-DD`. */
    private fun monthDay(month: String, day: Int?): String? {
        val index = MONTHS.indexOf(month.lowercase()).takeIf { it >= 0 } ?: return null
        return monthDay(index + 1, day ?: lastDay(index))
    }

    private fun monthDay(month: Int, day: Int): String? {
        if (month !in 1..MONTHS.size || day !in 1..MONTH_DAYS[month - 1]) return null
        return "%02d-%02d".format(Locale.ROOT, month, day)
    }

    /** A month's last day, February's as 28 so a range ending in it holds every year. */
    private fun lastDay(index: Int): Int = if (index == 1) FEB_DAYS else MONTH_DAYS[index]

    private const val FEB_DAYS = 28
}

/** What OSM tags say about a way (segments/trail-attributes). */
internal object OsmFacts {
    private val SURFACES: Map<String, String> = buildMap {
        for (v in listOf("asphalt", "paved", "paving_stones", "sett", "chipseal", "metal", "bricks")) put(v, TrailSurfaces.PAVED)
        for (v in listOf("compacted", "fine_gravel")) put(v, TrailSurfaces.COMPACTED)
        for (v in listOf("gravel", "pebblestone")) put(v, TrailSurfaces.GRAVEL)
        for (v in listOf("dirt", "earth", "ground", "soil", "mud", "unpaved")) put(v, TrailSurfaces.DIRT)
        for (v in listOf("grass", "grass_paver")) put(v, TrailSurfaces.GRASS)
        for (v in listOf("rock", "stone")) put(v, TrailSurfaces.ROCK)
        put("sand", TrailSurfaces.SAND)
        for (v in listOf("wood", "woodchips")) put(v, TrailSurfaces.WOOD)
        for (v in listOf("snow", "ice")) put(v, TrailSurfaces.SNOW)
    }
    private val TRACK_SURFACES =
        mapOf(
            "grade1" to TrailSurfaces.PAVED,
            "grade2" to TrailSurfaces.GRAVEL,
            "grade3" to TrailSurfaces.DIRT,
            "grade4" to TrailSurfaces.DIRT,
            "grade5" to TrailSurfaces.DIRT,
        )
    private val DOGS =
        mapOf("yes" to TrailDog.YES, "designated" to TrailDog.YES, "permissive" to TrailDog.YES) +
            (TrailDog.LEASHED to TrailDog.LEASHED) +
            (TrailDog.NO to TrailDog.NO)
    private val HORSES = mapOf("permissive" to TrailHorse.YES) + TrailHorse.all.associateWith { it }

    fun of(tags: Map<String, String>): WayFacts =
        WayFacts(
            surface = surfaceOf(tags["surface"]) ?: TRACK_SURFACES[tags["tracktype"]?.trim()],
            smoothness = FactValues.word(tags["smoothness"], TrailSmoothness.all),
            sacScale = FactValues.word(tags["sac_scale"], TrailSacScale.all),
            mtbScale = FactValues.leadingDigit(tags["mtb:scale"], 0..TrailAttributes.MAX_MTB_SCALE),
            mtbScaleImba = FactValues.leadingDigit(tags["mtb:scale:imba"], 0..TrailAttributes.MAX_MTB_SCALE_IMBA),
            mtbScaleUphill = FactValues.leadingDigit(tags["mtb:scale:uphill"], 0..TrailAttributes.MAX_MTB_SCALE_UPHILL),
            pisteDifficulty = FactValues.word(tags["piste:difficulty"], TrailPisteDifficulty.all),
            pisteGrooming = groomingOf(tags["piste:grooming"]),
            visibility = FactValues.word(tags["trail_visibility"], TrailVisibility.all),
            widthCm = FactValues.widthCm(tags["width"]),
            lit = tags["lit"]?.trim()?.takeIf { it.isNotEmpty() }?.let { it != "no" },
            dog = DOGS[tags["dog"]?.trim()],
            horse = HORSES[tags["horse"]?.trim()],
            wheelchair = FactValues.word(tags["wheelchair"], TrailWheelchair.all),
            seasonal = seasonalOf(tags["seasonal"]),
            seasons = seasonsOf(tags["seasonal"]),
            open = FactDates.monthRange(tags["opening_hours"]),
            operator = FactValues.operator(tags["operator"]),
            website = FactValues.website(tags["website"]) ?: FactValues.website(tags["contact:website"]),
        )

    /** A `surface` value's surface, by its first value when it lists several; `other` for one not in the table. */
    private fun surfaceOf(value: String?): String? {
        val first = value?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return if (first == "concrete" || first.startsWith("concrete:")) TrailSurfaces.PAVED else SURFACES[first] ?: TrailSurfaces.OTHER
    }

    private fun groomingOf(value: String?): String? {
        val text = value?.trim() ?: return null
        return if (text == "classic+skating" || text == "classic;skating") TrailPisteGrooming.CLASSIC_SKATING
        else FactValues.word(text, TrailPisteGrooming.all)
    }

    /** `seasonal`: `yes` or a list of seasons is seasonal, `no` isn't; anything else says nothing. */
    private fun seasonalOf(value: String?): Boolean? =
        when (val text = value?.trim()) {
            null,
            "" -> null
            "yes" -> true
            "no" -> false
            else -> true.takeIf { seasonsOf(text) != null }
        }

    private fun seasonsOf(value: String?): Set<String>? =
        value?.split(';')?.map { it.trim() }?.filterTo(sortedSetOf()) { it in TrailSeason.all }?.ifEmpty { null }
}

/** What agency fields say (segments/trail-attributes): values matched ignoring case, spaces and a leading code such as `NAT -`. */
internal object AgencyFacts {
    private val CODE = Regex("""^\s*[A-Za-z0-9]+\s+-\s+""")
    private val NUMBER = Regex("""\d+(?:\.\d+)?""")
    private val TRAIL_CLASS = Regex("""^(?:tc\s*|class\s*)?([1-5])\b""")
    private const val INCHES_PER_FOOT = 12

    /** [value] without its leading code, trimmed and lower-cased; null when it says nothing or is a bare numeric code. */
    private fun cleaned(value: String?): String? =
        FactValues.meaningful(value?.replace(CODE, ""))?.takeUnless { text -> text.all { it.isDigit() || it == '.' } }

    fun usfsSurface(value: String?): String? {
        val text = cleaned(value) ?: return null
        return when {
            "asphalt" in text || "concrete" in text -> TrailSurfaces.PAVED
            "compacted" in text -> TrailSurfaces.COMPACTED
            "aggregate" in text || "gravel" in text -> TrailSurfaces.GRAVEL
            "native" in text || "loose" in text -> TrailSurfaces.DIRT
            "wood" in text -> TrailSurfaces.WOOD
            "snow" in text -> TrailSurfaces.SNOW
            text == "other" -> TrailSurfaces.OTHER
            else -> null
        }
    }

    private val NPS_SURFACES: Map<String, String> = buildMap {
        for (v in listOf("asphalt", "bituminous", "concrete", "brick", "paver", "pavers", "masonry/stone", "masonry")) {
            put(v, TrailSurfaces.PAVED)
        }
        for (v in listOf("aggregate", "gravel", "gravel road")) put(v, TrailSurfaces.GRAVEL)
        for (v in
            listOf(
                "dirt",
                "earth",
                "soil",
                "clay",
                "native",
                "native material",
                "other unpaved",
                "earth/gravel",
                "gravel/earth",
                "dirt/gravel",
                "gravel/dirt",
                "soil/gravel",
            )) {
            put(v, TrailSurfaces.DIRT)
        }
        for (v in listOf("grass", "earth/grass")) put(v, TrailSurfaces.GRASS)
        for (v in listOf("stone", "lava")) put(v, TrailSurfaces.ROCK)
        put("sand", TrailSurfaces.SAND)
        for (v in listOf("wood", "wood chips", "woodchips")) put(v, TrailSurfaces.WOOD)
        put("snow", TrailSurfaces.SNOW)
    }

    fun npsSurface(value: String?): String? = cleaned(value)?.let { NPS_SURFACES[it] ?: TrailSurfaces.OTHER }

    private val BLM_SURFACES =
        mapOf(
            "solid surface" to TrailSurfaces.PAVED,
            "aggregate" to TrailSurfaces.GRAVEL,
            "natural" to TrailSurfaces.DIRT,
            "natural improved" to TrailSurfaces.DIRT,
            "snow" to TrailSurfaces.SNOW,
        )

    fun blmSurface(value: String?): String? = cleaned(value)?.let(BLM_SURFACES::get)

    /** A trail class, 1 to 5: `3`, `TC3` or `Class 4: Highly Developed`. */
    fun trailClass(value: String?): Int? = FactValues.meaningful(value)?.let(TRAIL_CLASS::find)?.groupValues?.get(1)?.toInt()

    /** A typical tread width such as `TW03 - 18-24 INCHES`, as its range's middle; an open-ended range as its bound. */
    fun usfsWidth(value: String?): Int? {
        val text = cleaned(value).orEmpty()
        val numbers = NUMBER.findAll(text).map { it.value.toDouble() }.toList()
        val inches = if (numbers.size >= 2) (numbers[0] + numbers[1]) / 2 else numbers.singleOrNull() ?: return null
        val feet = "feet" in text || "ft" in text.split(' ')
        return FactValues.inchesToCm(if (feet) inches * INCHES_PER_FOOT else inches)
    }

    /** A typical grade such as `TG05 - +12-20%`, as its range's upper bound without its sign. */
    fun usfsGrade(value: String?): Int? =
        cleaned(value)
            ?.let { NUMBER.findAll(it).lastOrNull() }
            ?.value
            ?.toDouble()
            ?.roundToInt()
            ?.takeIf { it in 0..TrailAttributes.MAX_GRADE_PERCENT }

    fun usfsAccessibility(value: String?): String? =
        when (cleaned(value)) {
            "accessible" -> TrailWheelchair.YES
            "not accessible" -> TrailWheelchair.NO
            else -> null
        }
}
