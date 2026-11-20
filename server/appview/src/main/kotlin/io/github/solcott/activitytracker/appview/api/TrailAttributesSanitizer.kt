package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.index.graphemes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.lexicon.app.TrailDog
import io.github.solcott.activitytracker.lexicon.app.TrailHorse
import io.github.solcott.activitytracker.lexicon.app.TrailLit
import io.github.solcott.activitytracker.lexicon.app.TrailPisteDifficulty
import io.github.solcott.activitytracker.lexicon.app.TrailPisteGrooming
import io.github.solcott.activitytracker.lexicon.app.TrailSacScale
import io.github.solcott.activitytracker.lexicon.app.TrailSeason
import io.github.solcott.activitytracker.lexicon.app.TrailSmoothness
import io.github.solcott.activitytracker.lexicon.app.TrailSurface
import io.github.solcott.activitytracker.lexicon.app.TrailSurfaces
import io.github.solcott.activitytracker.lexicon.app.TrailVisibility
import io.github.solcott.activitytracker.lexicon.app.TrailWheelchair

/**
 * Cleans untrusted [TrailAttributes] (segments/trail-attributes): when a candidate is staged and whenever a segment or path view serves
 * one, since a record can hold anything. Drops every field outside its vocabulary or range, open dates that aren't a pair, an operator over
 * its limits and a website that isn't an absolute http(s) URL; null when nothing is left.
 */
internal object TrailAttributesSanitizer {
    private const val MAX_PERCENT = 100

    fun clean(trail: TrailAttributes?): TrailAttributes? {
        if (trail == null) return null
        val dates = datesOf(trail.openFrom, trail.openUntil)
        val seasons = trail.seasons?.filter { it in TrailSeason.all }?.distinct()?.ifEmpty { null }
        val cleaned =
            TrailAttributes(
                surfaces = surfacesOf(trail.surfaces),
                smoothness = trail.smoothness.within(TrailSmoothness.all),
                sacScale = trail.sacScale.within(TrailSacScale.all),
                mtbScale = trail.mtbScale.within(0..TrailAttributes.MAX_MTB_SCALE),
                mtbScaleImba = trail.mtbScaleImba.within(0..TrailAttributes.MAX_MTB_SCALE_IMBA),
                mtbScaleUphill = trail.mtbScaleUphill.within(0..TrailAttributes.MAX_MTB_SCALE_UPHILL),
                pisteDifficulty = trail.pisteDifficulty.within(TrailPisteDifficulty.all),
                pisteGrooming = trail.pisteGrooming.within(TrailPisteGrooming.all),
                trailClass = trail.trailClass.within(1..TrailAttributes.MAX_TRAIL_CLASS),
                visibility = trail.visibility.within(TrailVisibility.all),
                widthCm = trail.widthCm.within(1..TrailAttributes.MAX_WIDTH_CM),
                gradePercent = trail.gradePercent.within(0..TrailAttributes.MAX_GRADE_PERCENT),
                lit = trail.lit.within(TrailLit.all),
                dog = trail.dog.within(TrailDog.all),
                horse = trail.horse.within(TrailHorse.all),
                wheelchair = trail.wheelchair.within(TrailWheelchair.all),
                seasons = seasons,
                // Only for a seasonal trail with no seasons or dates known, as the lexicon defines it.
                seasonal = true.takeIf { trail.seasonal == true && seasons == null && dates == null },
                openFrom = dates?.first,
                openUntil = dates?.second,
                operator = operatorOf(trail.operator),
                website = websiteOf(trail.website),
            )
        return cleaned.takeUnless { it.isEmpty }
    }

    /** [trail] cleaned and encoded for a `trail` column; null when nothing is left. */
    fun encoded(trail: TrailAttributes?): String? = clean(trail)?.let { AtJson.encodeToString(TrailAttributes.serializer(), it) }

    /** A `trail` column's value decoded and cleaned again; null for none or one that no longer decodes. */
    fun decoded(column: String?): TrailAttributes? =
        column?.let { runCatching { AtJson.decodeFromString(TrailAttributes.serializer(), it) }.getOrNull() }?.let(::clean)

    private fun String?.within(values: List<String>): String? = this?.takeIf { it in values }

    private fun Int?.within(range: IntRange): Int? = this?.takeIf { it in range }

    /**
     * Known surfaces with a share of 1 to 100, each once at its largest share, at most 8, largest first, kept only while their sum stays at
     * most 100.
     */
    private fun surfacesOf(surfaces: List<TrailSurface>?): List<TrailSurface>? {
        var total = 0
        return surfaces
            ?.filter { it.surface in TrailSurfaces.all && it.percent in 1..MAX_PERCENT }
            ?.sortedByDescending { it.percent }
            ?.distinctBy { it.surface }
            ?.take(TrailAttributes.MAX_SURFACES)
            ?.filter { (total + it.percent <= MAX_PERCENT).also { fits -> if (fits) total += it.percent } }
            ?.ifEmpty { null }
    }

    private fun datesOf(from: String?, until: String?): Pair<String, String>? =
        listOfNotNull(from, until).filter(TrailAttributes::isMonthDay).takeIf { it.size == 2 }?.let { it[0] to it[1] }

    private fun operatorOf(operator: String?): String? =
        operator
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= TrailAttributes.MAX_OPERATOR_LENGTH }
            ?.takeIf { graphemes(it) <= TrailAttributes.MAX_OPERATOR_GRAPHEMES }

    /** [website] trimmed, when it is one the lexicon holds ([TrailAttributes.isWebsite], the importer's rule too). */
    private fun websiteOf(website: String?): String? = website?.trim()?.takeIf(TrailAttributes::isWebsite)
}
