package io.github.solcott.activitytracker.lexicon.app

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * `app.activitytracker.feed.defs#trailAttributes`: what the segment importer's source data says about a trail or road
 * (segments/trail-attributes), combined over the ways a line follows. Every field is optional, and null means unknown, never a default.
 * Each string field is an open enum of the matching object's values ([TrailSurfaces], [TrailSmoothness], …); the AppView drops a value
 * outside them when it serves the object.
 */
@Immutable
@Serializable
data class TrailAttributes(
    /**
     * Each surface's whole-number share of the length whose surface is known, largest first, at most [MAX_SURFACES]; the shares sum to at
     * most 100.
     */
    val surfaces: List<TrailSurface>? = null,
    /** A [TrailSmoothness] value: the roughest along at least 50 m. */
    val smoothness: String? = null,
    /** A [TrailSacScale] value: the hardest along at least 50 m. */
    val sacScale: String? = null,
    /** The hardest MTB scale, 0 to [MAX_MTB_SCALE]. */
    val mtbScale: Int? = null,
    /** The hardest IMBA difficulty, 0 (white circle, easiest) to [MAX_MTB_SCALE_IMBA] (double black diamond), as OSM `mtb:scale:imba`. */
    val mtbScaleImba: Int? = null,
    /** The hardest MTB uphill scale, 0 to [MAX_MTB_SCALE_UPHILL]. */
    val mtbScaleUphill: Int? = null,
    /** A [TrailPisteDifficulty] value: the hardest along at least 50 m. */
    val pisteDifficulty: String? = null,
    /** A [TrailPisteGrooming] value. */
    val pisteGrooming: String? = null,
    /** An agency's trail class, 1 (minimally developed) to [MAX_TRAIL_CLASS] (fully developed): the least developed along at least 50 m. */
    val trailClass: Int? = null,
    /** A [TrailVisibility] value: the worst along at least 50 m. */
    val visibility: String? = null,
    /** The typical width in centimetres, positive. */
    val widthCm: Int? = null,
    /** An agency's typical grade in percent, not negative. */
    val gradePercent: Int? = null,
    /** A [TrailLit] value. */
    val lit: String? = null,
    /** A [TrailDog] value: the strictest known. */
    val dog: String? = null,
    /** A [TrailHorse] value: the most open known. */
    val horse: String? = null,
    /** A [TrailWheelchair] value. */
    val wheelchair: String? = null,
    /** [TrailSeason] values it is open in; never empty. */
    val seasons: List<String>? = null,
    /** True when it is open only part of the year with no [seasons] or dates known. */
    val seasonal: Boolean? = null,
    /** The first day it is open each year, `MM-DD`, given only with [openUntil]. */
    val openFrom: String? = null,
    /** The last day it is open each year, `MM-DD`, given only with [openFrom]. */
    val openUntil: String? = null,
    /** Who maintains it, as the source writes it: at most [MAX_OPERATOR_GRAPHEMES] graphemes. */
    val operator: String? = null,
    /**
     * An http or https URL about it, at most [MAX_WEBSITE_LENGTH] characters. Open only in the system browser, after checking its scheme.
     */
    val website: String? = null,
) {
    /** Whether it says nothing at all. */
    val isEmpty: Boolean
        get() = this == TrailAttributes()

    companion object {
        /** The lexicon's `maxLength` for [surfaces]. */
        const val MAX_SURFACES = 8
        const val MAX_MTB_SCALE = 6
        const val MAX_MTB_SCALE_IMBA = 4
        const val MAX_MTB_SCALE_UPHILL = 5
        const val MAX_TRAIL_CLASS = 5
        const val MAX_WIDTH_CM = 100_000
        const val MAX_GRADE_PERCENT = 1_000
        const val MAX_OPERATOR_GRAPHEMES = 100
        const val MAX_OPERATOR_LENGTH = 1_000
        const val MAX_WEBSITE_LENGTH = 512

        /** `MM-DD`, as [openFrom] and [openUntil] hold. */
        val MONTH_DAY = Regex("""(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])""")
    }
}

/** `app.activitytracker.feed.defs#trailSurface`: a [TrailSurfaces] value and its whole-number share of a line, 1 to 100. */
@Immutable @Serializable data class TrailSurface(val surface: String, val percent: Int)

/** [TrailAttributes.surfaces] values, an open enum. */
object TrailSurfaces {
    const val PAVED = "paved"
    const val COMPACTED = "compacted"
    const val GRAVEL = "gravel"
    const val DIRT = "dirt"
    const val GRASS = "grass"
    const val ROCK = "rock"
    const val SAND = "sand"
    const val WOOD = "wood"
    const val SNOW = "snow"
    const val OTHER = "other"

    val all = listOf(PAVED, COMPACTED, GRAVEL, DIRT, GRASS, ROCK, SAND, WOOD, SNOW, OTHER)
}

/** [TrailAttributes.smoothness] values, best to worst. */
object TrailSmoothness {
    val all = listOf("excellent", "good", "intermediate", "bad", "very_bad", "horrible", "very_horrible", "impassable")
}

/** [TrailAttributes.sacScale] values, easiest to hardest. */
object TrailSacScale {
    const val HIKING = "hiking"
    const val MOUNTAIN_HIKING = "mountain_hiking"
    const val DEMANDING_MOUNTAIN_HIKING = "demanding_mountain_hiking"
    const val ALPINE_HIKING = "alpine_hiking"
    const val DEMANDING_ALPINE_HIKING = "demanding_alpine_hiking"
    const val DIFFICULT_ALPINE_HIKING = "difficult_alpine_hiking"

    val all = listOf(HIKING, MOUNTAIN_HIKING, DEMANDING_MOUNTAIN_HIKING, ALPINE_HIKING, DEMANDING_ALPINE_HIKING, DIFFICULT_ALPINE_HIKING)
}

/** [TrailAttributes.pisteDifficulty] values, easiest to hardest. */
object TrailPisteDifficulty {
    val all = listOf("novice", "easy", "intermediate", "advanced", "expert", "freeride", "extreme")
}

/** [TrailAttributes.pisteGrooming] values. */
object TrailPisteGrooming {
    const val CLASSIC_SKATING = "classic_skating"

    val all = listOf("classic", "skating", CLASSIC_SKATING, "mogul", "backcountry", "scooter")
}

/** [TrailAttributes.visibility] values, best to worst. */
object TrailVisibility {
    val all = listOf("excellent", "good", "intermediate", "bad", "horrible", "no")
}

/** [TrailAttributes.lit] values. */
object TrailLit {
    const val YES = "yes"
    const val PARTLY = "partly"
    const val NO = "no"

    val all = listOf(YES, PARTLY, NO)
}

/** [TrailAttributes.dog] values, strictest first. */
object TrailDog {
    const val NO = "no"
    const val LEASHED = "leashed"
    const val YES = "yes"

    val all = listOf(NO, LEASHED, YES)
}

/** [TrailAttributes.horse] values, most open first. */
object TrailHorse {
    const val DESIGNATED = "designated"
    const val YES = "yes"
    const val NO = "no"

    val all = listOf(DESIGNATED, YES, NO)
}

/** [TrailAttributes.wheelchair] values, most limited first. */
object TrailWheelchair {
    const val NO = "no"
    const val LIMITED = "limited"
    const val YES = "yes"

    val all = listOf(NO, LIMITED, YES)
}

/** [TrailAttributes.seasons] values. */
object TrailSeason {
    val all = listOf("spring", "summer", "autumn", "winter", "dry_season", "wet_season")
}
