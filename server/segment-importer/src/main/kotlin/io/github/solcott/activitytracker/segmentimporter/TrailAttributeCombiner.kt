package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.lexicon.app.TrailDog
import io.github.solcott.activitytracker.lexicon.app.TrailHorse
import io.github.solcott.activitytracker.lexicon.app.TrailLit
import io.github.solcott.activitytracker.lexicon.app.TrailPisteDifficulty
import io.github.solcott.activitytracker.lexicon.app.TrailSacScale
import io.github.solcott.activitytracker.lexicon.app.TrailSmoothness
import io.github.solcott.activitytracker.lexicon.app.TrailSurface
import io.github.solcott.activitytracker.lexicon.app.TrailSurfaces
import io.github.solcott.activitytracker.lexicon.app.TrailVisibility
import io.github.solcott.activitytracker.lexicon.app.TrailWheelchair
import kotlin.math.floor
import kotlin.math.min
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** One stretch of a line: what its way says, and how many meters of the line lie along it. */
internal data class FactsAlong(val facts: WayFacts, val meters: Double)

/**
 * Combines what the ways a candidate or path follows say into its [TrailAttributes] (segments/trail-attributes), each attribute by its own
 * rule, weighted by the length of the line along each way. An attribute is kept only when the ways giving it cover [MIN_COVERAGE] of the
 * line, except the strict ones (`dog: no`, `wheelchair: no` and `limited`), kept from any stretch. Deterministic for the same stretches.
 */
internal object TrailAttributeCombiner {
    /** The share of a line an attribute's ways must cover. */
    const val MIN_COVERAGE = 0.25

    /** How far along a line a difficulty must run to count, in meters, or the whole line when it's shorter. */
    const val HARDEST_ALONG_M = 50.0

    /** The share of a line that makes it lit, unlit or accessible throughout. */
    const val THROUGHOUT = 0.9

    /** A surface's least share to be listed, in percent: only one that rounds to 0% is dropped, so a trail shows what its segments do. */
    const val MIN_SURFACE_PERCENT = 1
    /** Absorbs float error in a share, so a line wholly of one surface never rounds down to 99%. */
    const val SHARE_EPSILON = 1e-9

    private const val PERCENT = 100

    fun combine(stretches: List<FactsAlong>): TrailAttributes? {
        val total = stretches.sumOf { it.meters }
        if (total <= 0.0) return null
        val line = Line(stretches.filter { it.meters > 0.0 }, total)
        val seasonal = line.agreed { it.seasonal }
        val seasons = line.agreed { it.seasons }
        val open = line.agreed { it.open }
        val attributes =
            TrailAttributes(
                surfaces = line.surfaces(),
                smoothness = line.hardest(TrailSmoothness.all) { it.smoothness },
                sacScale = line.hardest(TrailSacScale.all) { it.sacScale },
                mtbScale = line.hardest { it.mtbScale },
                mtbScaleImba = line.hardest { it.mtbScaleImba },
                mtbScaleUphill = line.hardest { it.mtbScaleUphill },
                pisteDifficulty = line.hardest(TrailPisteDifficulty.all) { it.pisteDifficulty },
                pisteGrooming = line.longest { it.pisteGrooming },
                // The least developed class is the hardest.
                trailClass = line.hardest { it.trailClass?.unaryMinus() }?.unaryMinus(),
                visibility = line.hardest(TrailVisibility.all) { it.visibility },
                widthCm = line.medianWidth(),
                gradePercent = line.hardest { it.gradePercent },
                lit = line.lit(),
                dog = line.strictest(TrailDog.all, strict = setOf(TrailDog.NO)) { it.dog },
                horse = line.strictest(TrailHorse.all) { it.horse },
                wheelchair = line.wheelchair(),
                seasons = seasons?.toList(),
                seasonal = true.takeIf { seasonal == true && seasons == null && open == null },
                openFrom = open?.from,
                openUntil = open?.until,
                operator = line.longest { it.operator },
                website = line.longest { it.website },
            )
        return attributes.takeUnless { it.isEmpty }
    }

    /** [stretches] of a line [total] meters long, those of no length left out. */
    private class Line(val stretches: List<FactsAlong>, val total: Double) {
        private val hardestAlong = min(HARDEST_ALONG_M, total)

        private fun covers(meters: Double) = meters >= total * MIN_COVERAGE

        /** The stretches giving [value], with it. */
        private fun <T : Any> given(value: (WayFacts) -> T?): List<Pair<T, Double>> = stretches.mapNotNull { stretch ->
            value(stretch.facts)?.let { it to stretch.meters }
        }

        /**
         * Each surface's share of the length whose surface is known, rounded down to whole percents, those rounding to 0% dropped, largest
         * first: a stretch saying nothing is no other surface.
         */
        fun surfaces(): List<TrailSurface>? {
            val given = given { it.surface }
            val known = given.sumOf { it.second }
            if (!covers(known)) return null
            return given
                .groupBy({ it.first }, { it.second })
                .map { (surface, meters) -> TrailSurface(surface, floor(meters.sum() / known * PERCENT + SHARE_EPSILON).toInt()) }
                .filter { it.percent >= MIN_SURFACE_PERCENT }
                .sortedWith(compareByDescending<TrailSurface> { it.percent }.thenBy { TrailSurfaces.all.indexOf(it.surface) })
                .take(TrailAttributes.MAX_SURFACES)
                .ifEmpty { null }
        }

        /** The hardest of [value]'s words, by their place in [order] (later is harder). */
        fun hardest(order: List<String>, value: (WayFacts) -> String?): String? = hardest { facts ->
            value(facts)?.let(order::indexOf)?.takeIf { it >= 0 }
        }
            ?.let(order::get)

        /**
         * The highest value that, with every harder one, runs along at least [HARDEST_ALONG_M] (or the whole line), when the ways giving it
         * cover enough of the line.
         */
        fun hardest(value: (WayFacts) -> Int?): Int? {
            val given = given(value)
            if (!covers(given.sumOf { it.second })) return null
            var along = 0.0
            return given
                .groupBy({ it.first }, { it.second })
                .toSortedMap(compareByDescending { it })
                .entries
                .firstOrNull { (_, meters) ->
                    along += meters.sum()
                    along >= hardestAlong
                }
                ?.key
        }

        /** The value along the most of the line, the first by its own order on a tie. */
        fun longest(value: (WayFacts) -> String?): String? {
            val given = given(value)
            if (!covers(given.sumOf { it.second })) return null
            return given
                .groupBy({ it.first }, { it.second })
                .mapValues { it.value.sum() }
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
                .first()
                .key
        }

        /** The first of [order] that any stretch gives: those in [strict] from any stretch, others when enough of the line says. */
        fun strictest(order: List<String>, strict: Set<String> = emptySet(), value: (WayFacts) -> String?): String? {
            val given = given(value).filter { it.first in order }
            val first = order.firstOrNull { word -> given.any { it.first == word } } ?: return null
            return first.takeIf { word -> word in strict || covers(given.sumOf { it.second }) }
        }

        /** The length-weighted median of the known widths. */
        fun medianWidth(): Int? {
            val given = given { it.widthCm }.sortedBy { it.first }
            val known = given.sumOf { it.second }
            if (!covers(known)) return null
            var along = 0.0
            return given
                .firstOrNull { (_, meters) ->
                    along += meters
                    along >= known / 2
                }
                ?.first
        }

        fun lit(): String? {
            val given = given { it.lit }
            if (!covers(given.sumOf { it.second })) return null
            val lit = given.filter { it.first }.sumOf { it.second }
            val unlit = given.filterNot { it.first }.sumOf { it.second }
            return when {
                lit >= total * THROUGHOUT -> TrailLit.YES
                lit == 0.0 && unlit >= total * THROUGHOUT -> TrailLit.NO
                lit > 0.0 -> TrailLit.PARTLY
                else -> null
            }
        }

        /**
         * `yes` only when at least [THROUGHOUT] of the line is known accessible and none of it isn't; else the most limited known value
         * short of `yes`, `no` and `limited` from any stretch.
         */
        fun wheelchair(): String? {
            val given = given { it.wheelchair }
            val accessible = given.filter { it.first == TrailWheelchair.YES }.sumOf { it.second }
            return when {
                given.any { it.first == TrailWheelchair.NO } -> TrailWheelchair.NO
                given.any { it.first == TrailWheelchair.LIMITED } -> TrailWheelchair.LIMITED
                accessible >= total * THROUGHOUT -> TrailWheelchair.YES
                else -> null
            }
        }

        /** The one value every stretch giving one gives, when they cover enough of the line; null when they disagree. */
        fun <T : Any> agreed(value: (WayFacts) -> T?): T? {
            val given = given(value)
            val known = given.sumOf { it.second }
            return given.map { it.first }.distinct().singleOrNull()?.takeIf { covers(known) }
        }
    }
}

/**
 * What a stored record's `trail` must become to hold [trail] (segments/trail-attributes): its encoding, or [JsonNull] to remove it; null
 * when it already does. Compared as values, so a stored object differing only in its fields' order holds it already.
 */
internal fun trailChange(record: JsonObject, trail: TrailAttributes?): JsonElement? {
    val stored = record[TRAIL_FIELD]?.let { runCatching { AtJson.decodeFromJsonElement(TrailAttributes.serializer(), it) }.getOrNull() }
    val differs = stored != trail || (trail == null && TRAIL_FIELD in record)
    return if (!differs) null else trail?.let { AtJson.encodeToJsonElement(TrailAttributes.serializer(), it) } ?: JsonNull
}

/** `feed.segment#trail` and `feed.path#trail`. */
internal const val TRAIL_FIELD = "trail"

/**
 * These attributes with each one they lack taken from [other]'s (segments/path-import); the season (seasons, seasonal and open dates) is
 * one attribute, taken whole.
 */
@Suppress("CyclomaticComplexMethod") // One fallback per attribute.
internal fun TrailAttributes?.filledFrom(other: TrailAttributes?): TrailAttributes? {
    if (this == null || other == null) return this ?: other
    val season = if (seasons != null || seasonal != null || openFrom != null) this else other
    return copy(
        surfaces = surfaces ?: other.surfaces,
        smoothness = smoothness ?: other.smoothness,
        sacScale = sacScale ?: other.sacScale,
        mtbScale = mtbScale ?: other.mtbScale,
        mtbScaleImba = mtbScaleImba ?: other.mtbScaleImba,
        mtbScaleUphill = mtbScaleUphill ?: other.mtbScaleUphill,
        pisteDifficulty = pisteDifficulty ?: other.pisteDifficulty,
        pisteGrooming = pisteGrooming ?: other.pisteGrooming,
        trailClass = trailClass ?: other.trailClass,
        visibility = visibility ?: other.visibility,
        widthCm = widthCm ?: other.widthCm,
        gradePercent = gradePercent ?: other.gradePercent,
        lit = lit ?: other.lit,
        dog = dog ?: other.dog,
        horse = horse ?: other.horse,
        wheelchair = wheelchair ?: other.wheelchair,
        seasons = season.seasons,
        seasonal = season.seasonal,
        openFrom = season.openFrom,
        openUntil = season.openUntil,
        operator = operator ?: other.operator,
        website = website ?: other.website,
    )
}
