package io.github.solcott.activitytracker.lexicon.app

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * `app.activitytracker.feed.defs#trailSources`: the source of each trail detail, keyed by [TrailDetail] name, each a [TrailSource] value.
 */
@Immutable
@Serializable
data class TrailSources(
    val surface: String? = null,
    val smoothness: String? = null,
    val sacScale: String? = null,
    val mtbScale: String? = null,
    val mtbScaleImba: String? = null,
    val mtbScaleUphill: String? = null,
    val pisteDifficulty: String? = null,
    val pisteGrooming: String? = null,
    val trailClass: String? = null,
    val visibility: String? = null,
    val width: String? = null,
    val grade: String? = null,
    val lit: String? = null,
    val dog: String? = null,
    val horse: String? = null,
    val wheelchair: String? = null,
    val season: String? = null,
    val operator: String? = null,
    val website: String? = null,
) {
    /** The source of [detail], a [TrailDetail] name; null when none is known. */
    @Suppress("CyclomaticComplexMethod")
    fun of(detail: String): String? =
        when (detail) {
            TrailDetail.SURFACE -> surface
            TrailDetail.SMOOTHNESS -> smoothness
            TrailDetail.SAC_SCALE -> sacScale
            TrailDetail.MTB_SCALE -> mtbScale
            TrailDetail.MTB_SCALE_IMBA -> mtbScaleImba
            TrailDetail.MTB_SCALE_UPHILL -> mtbScaleUphill
            TrailDetail.PISTE_DIFFICULTY -> pisteDifficulty
            TrailDetail.PISTE_GROOMING -> pisteGrooming
            TrailDetail.TRAIL_CLASS -> trailClass
            TrailDetail.VISIBILITY -> visibility
            TrailDetail.WIDTH -> width
            TrailDetail.GRADE -> grade
            TrailDetail.LIT -> lit
            TrailDetail.DOG -> dog
            TrailDetail.HORSE -> horse
            TrailDetail.WHEELCHAIR -> wheelchair
            TrailDetail.SEASON -> season
            TrailDetail.OPERATOR -> operator
            TrailDetail.WEBSITE -> website
            else -> null
        }

    companion object {
        /** Sources keyed by [TrailDetail] name, dropping any outside [TrailSource.imports]. */
        @Suppress("CyclomaticComplexMethod")
        fun of(byDetail: Map<String, String>): TrailSources? {
            val known = byDetail.filterValues { it in TrailSource.imports }
            if (known.isEmpty()) return null
            return TrailSources(
                surface = known[TrailDetail.SURFACE],
                smoothness = known[TrailDetail.SMOOTHNESS],
                sacScale = known[TrailDetail.SAC_SCALE],
                mtbScale = known[TrailDetail.MTB_SCALE],
                mtbScaleImba = known[TrailDetail.MTB_SCALE_IMBA],
                mtbScaleUphill = known[TrailDetail.MTB_SCALE_UPHILL],
                pisteDifficulty = known[TrailDetail.PISTE_DIFFICULTY],
                pisteGrooming = known[TrailDetail.PISTE_GROOMING],
                trailClass = known[TrailDetail.TRAIL_CLASS],
                visibility = known[TrailDetail.VISIBILITY],
                width = known[TrailDetail.WIDTH],
                grade = known[TrailDetail.GRADE],
                lit = known[TrailDetail.LIT],
                dog = known[TrailDetail.DOG],
                horse = known[TrailDetail.HORSE],
                wheelchair = known[TrailDetail.WHEELCHAIR],
                season = known[TrailDetail.SEASON],
                operator = known[TrailDetail.OPERATOR],
                website = known[TrailDetail.WEBSITE],
            )
        }
    }
}

/** [TrailSources] and [TrailDetailInfo.source] values. */
object TrailSource {
    const val OSM = "osm"
    const val USFS = "usfs"
    const val NPS = "nps"
    const val BLM = "blm"
    /** Imported, with no source known: a record extracted before sources were kept. */
    const val IMPORTED = "imported"
    /** Adopted from riders' suggestions (segments/trail-detail-suggestions). */
    const val COMMUNITY = "community"

    /** The sources a record's [TrailSources] may name. */
    val imports = listOf(OSM, USFS, NPS, BLM)
}

/**
 * The trail details a [TrailAttributes] holds, by name (segments/trail-detail-suggestions): each covers one or more of its fields, and is
 * what a suggestion, a vote and a [TrailSources] key name.
 */
object TrailDetail {
    const val SURFACE = "surface"
    const val SMOOTHNESS = "smoothness"
    const val SAC_SCALE = "sacScale"
    const val MTB_SCALE = "mtbScale"
    const val MTB_SCALE_IMBA = "mtbScaleImba"
    const val MTB_SCALE_UPHILL = "mtbScaleUphill"
    const val PISTE_DIFFICULTY = "pisteDifficulty"
    const val PISTE_GROOMING = "pisteGrooming"
    const val TRAIL_CLASS = "trailClass"
    const val VISIBILITY = "visibility"
    const val WIDTH = "width"
    const val GRADE = "grade"
    const val LIT = "lit"
    const val DOG = "dog"
    const val HORSE = "horse"
    const val WHEELCHAIR = "wheelchair"
    const val SEASON = "season"
    const val OPERATOR = "operator"
    const val WEBSITE = "website"

    /** Every detail, in the order the Trail details cards show them. */
    val all =
        listOf(
            SAC_SCALE,
            MTB_SCALE,
            MTB_SCALE_UPHILL,
            MTB_SCALE_IMBA,
            PISTE_DIFFICULTY,
            PISTE_GROOMING,
            TRAIL_CLASS,
            VISIBILITY,
            SURFACE,
            SMOOTHNESS,
            WIDTH,
            GRADE,
            LIT,
            DOG,
            HORSE,
            WHEELCHAIR,
            SEASON,
            OPERATOR,
            WEBSITE,
        )

    /** The details riders may suggest: all but the official ([TRAIL_CLASS], [OPERATOR], [WEBSITE]) and the measured ([GRADE]). */
    val suggestable = all - setOf(TRAIL_CLASS, GRADE, OPERATOR, WEBSITE)

    /** [TrailAttributes.widthCm] a suggested width kind stores: the middle of each kind's range. */
    const val SINGLETRACK_CM = 100
    const val DOUBLETRACK_CM = 200
    const val ROAD_WIDTH_CM = 400
    /** Where each width kind starts, as the Width row reads it (segments/trail-attributes). */
    const val DOUBLETRACK_FROM_CM = 150
    const val ROAD_WIDTH_FROM_CM = 300

    /** The width kind's stored value for [widthCm]. */
    fun widthKind(widthCm: Int): Int =
        when {
            widthCm < DOUBLETRACK_FROM_CM -> SINGLETRACK_CM
            widthCm < ROAD_WIDTH_FROM_CM -> DOUBLETRACK_CM
            else -> ROAD_WIDTH_CM
        }

    /** [trail]'s fields for [detail] alone, with a width as its kind's value; null when it holds none of them. */
    @Suppress("CyclomaticComplexMethod")
    fun valueOf(trail: TrailAttributes, detail: String): TrailAttributes? {
        val value =
            when (detail) {
                SURFACE -> TrailAttributes(surfaces = trail.surfaces)
                SMOOTHNESS -> TrailAttributes(smoothness = trail.smoothness)
                SAC_SCALE -> TrailAttributes(sacScale = trail.sacScale)
                MTB_SCALE -> TrailAttributes(mtbScale = trail.mtbScale)
                MTB_SCALE_IMBA -> TrailAttributes(mtbScaleImba = trail.mtbScaleImba)
                MTB_SCALE_UPHILL -> TrailAttributes(mtbScaleUphill = trail.mtbScaleUphill)
                PISTE_DIFFICULTY -> TrailAttributes(pisteDifficulty = trail.pisteDifficulty)
                PISTE_GROOMING -> TrailAttributes(pisteGrooming = trail.pisteGrooming)
                TRAIL_CLASS -> TrailAttributes(trailClass = trail.trailClass)
                VISIBILITY -> TrailAttributes(visibility = trail.visibility)
                WIDTH -> TrailAttributes(widthCm = trail.widthCm?.let(::widthKind))
                GRADE -> TrailAttributes(gradePercent = trail.gradePercent)
                LIT -> TrailAttributes(lit = trail.lit)
                DOG -> TrailAttributes(dog = trail.dog)
                HORSE -> TrailAttributes(horse = trail.horse)
                WHEELCHAIR -> TrailAttributes(wheelchair = trail.wheelchair)
                SEASON ->
                    TrailAttributes(
                        seasons = trail.seasons,
                        seasonal = trail.seasonal,
                        openFrom = trail.openFrom,
                        openUntil = trail.openUntil,
                    )
                OPERATOR -> TrailAttributes(operator = trail.operator)
                WEBSITE -> TrailAttributes(website = trail.website)
                else -> return null
            }
        return value.takeUnless { it.isEmpty }
    }

    /** [trail] with [detail]'s fields replaced by [value]'s (cleared when [value] is null), leaving every other field and the sources. */
    @Suppress("CyclomaticComplexMethod") // One fallback per field.
    fun replace(trail: TrailAttributes, detail: String, value: TrailAttributes?): TrailAttributes {
        val cleared = clear(trail, detail)
        val v = value ?: return cleared
        return cleared.copy(
            surfaces = v.surfaces ?: cleared.surfaces,
            smoothness = v.smoothness ?: cleared.smoothness,
            sacScale = v.sacScale ?: cleared.sacScale,
            mtbScale = v.mtbScale ?: cleared.mtbScale,
            mtbScaleImba = v.mtbScaleImba ?: cleared.mtbScaleImba,
            mtbScaleUphill = v.mtbScaleUphill ?: cleared.mtbScaleUphill,
            pisteDifficulty = v.pisteDifficulty ?: cleared.pisteDifficulty,
            pisteGrooming = v.pisteGrooming ?: cleared.pisteGrooming,
            trailClass = v.trailClass ?: cleared.trailClass,
            visibility = v.visibility ?: cleared.visibility,
            widthCm = v.widthCm ?: cleared.widthCm,
            gradePercent = v.gradePercent ?: cleared.gradePercent,
            lit = v.lit ?: cleared.lit,
            dog = v.dog ?: cleared.dog,
            horse = v.horse ?: cleared.horse,
            wheelchair = v.wheelchair ?: cleared.wheelchair,
            seasons = v.seasons ?: cleared.seasons,
            seasonal = v.seasonal ?: cleared.seasonal,
            openFrom = v.openFrom ?: cleared.openFrom,
            openUntil = v.openUntil ?: cleared.openUntil,
            operator = v.operator ?: cleared.operator,
            website = v.website ?: cleared.website,
        )
    }

    @Suppress("CyclomaticComplexMethod")
    private fun clear(trail: TrailAttributes, detail: String): TrailAttributes =
        when (detail) {
            SURFACE -> trail.copy(surfaces = null)
            SMOOTHNESS -> trail.copy(smoothness = null)
            SAC_SCALE -> trail.copy(sacScale = null)
            MTB_SCALE -> trail.copy(mtbScale = null)
            MTB_SCALE_IMBA -> trail.copy(mtbScaleImba = null)
            MTB_SCALE_UPHILL -> trail.copy(mtbScaleUphill = null)
            PISTE_DIFFICULTY -> trail.copy(pisteDifficulty = null)
            PISTE_GROOMING -> trail.copy(pisteGrooming = null)
            TRAIL_CLASS -> trail.copy(trailClass = null)
            VISIBILITY -> trail.copy(visibility = null)
            WIDTH -> trail.copy(widthCm = null)
            GRADE -> trail.copy(gradePercent = null)
            LIT -> trail.copy(lit = null)
            DOG -> trail.copy(dog = null)
            HORSE -> trail.copy(horse = null)
            WHEELCHAIR -> trail.copy(wheelchair = null)
            SEASON -> trail.copy(seasons = null, seasonal = null, openFrom = null, openUntil = null)
            OPERATOR -> trail.copy(operator = null)
            WEBSITE -> trail.copy(website = null)
            else -> trail
        }

    /** The details [trail] holds a value for, in [all]'s order. */
    fun detailsOf(trail: TrailAttributes): List<String> = all.filter { valueOf(trail, it) != null }

    /**
     * Whether [value] is a value a rider may suggest for [detail]: a [suggestable] detail, only its fields, each in its vocabulary, with
     * one surface at 100%, a width kind's stored value, and one form of season (open dates, seasons, or seasonal).
     */
    fun isSuggestion(detail: String, value: TrailAttributes): Boolean =
        detail in suggestable &&
            value.sources == null &&
            valueOf(value, detail)?.let { replace(value, detail, null).isEmpty && fits(detail, value) } == true

    @Suppress("CyclomaticComplexMethod")
    private fun fits(detail: String, v: TrailAttributes): Boolean =
        when (detail) {
            SURFACE -> v.surfaces?.singleOrNull()?.let { it.percent == FULL && it.surface in TrailSurfaces.all } == true
            SMOOTHNESS -> v.smoothness in TrailSmoothness.all
            SAC_SCALE -> v.sacScale in TrailSacScale.all
            MTB_SCALE -> v.mtbScale in 0..TrailAttributes.MAX_MTB_SCALE
            MTB_SCALE_IMBA -> v.mtbScaleImba in 0..TrailAttributes.MAX_MTB_SCALE_IMBA
            MTB_SCALE_UPHILL -> v.mtbScaleUphill in 0..TrailAttributes.MAX_MTB_SCALE_UPHILL
            PISTE_DIFFICULTY -> v.pisteDifficulty in TrailPisteDifficulty.all
            PISTE_GROOMING -> v.pisteGrooming in TrailPisteGrooming.all
            VISIBILITY -> v.visibility in TrailVisibility.all
            WIDTH -> v.widthCm in setOf(SINGLETRACK_CM, DOUBLETRACK_CM, ROAD_WIDTH_CM)
            LIT -> v.lit in TrailLit.all
            DOG -> v.dog in TrailDog.all
            HORSE -> v.horse in TrailHorse.all
            WHEELCHAIR -> v.wheelchair in TrailWheelchair.all
            SEASON -> isSeason(v)
            else -> false
        }

    private fun isSeason(v: TrailAttributes): Boolean {
        val dates = v.openFrom != null || v.openUntil != null
        val forms = listOf(dates, v.seasons != null, v.seasonal != null).count { it }
        return forms == 1 &&
            (!dates || (v.openFrom?.let(TrailAttributes::isMonthDay) == true && v.openUntil?.let(TrailAttributes::isMonthDay) == true)) &&
            (v.seasons == null ||
                (v.seasons.isNotEmpty() && v.seasons.all { it in TrailSeason.all } && v.seasons.size == v.seasons.toSet().size)) &&
            (v.seasonal == null || v.seasonal)
    }

    private const val FULL = 100
}

/** `app.activitytracker.feed.trailDetailVote#direction` values. */
object TrailDetailVoteDirection {
    /** Agrees with a suggestion. */
    const val UP = "up"
    /** Disagrees with a suggestion. */
    const val DOWN = "down"
    /** Says a value in effect is still right. */
    const val STILL = "still"
    /** Says a value in effect has changed. */
    const val CHANGED = "changed"
}
