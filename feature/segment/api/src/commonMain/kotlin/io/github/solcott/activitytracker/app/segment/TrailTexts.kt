package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.DateText
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList

/**
 * One row of a Trail details section (segments/trail-attributes): what it is ([label]) and what the data says ([value]). [link] is the
 * http(s) URL a Website row opens in the system browser; null for every other row.
 */
@Immutable
data class TrailDetail(
    val label: AppText,
    val value: AppText,
    val link: String? = null,
    /** The detail the row shows, a lexicon `TrailDetail` name, which its sheet and suggestions name (segments/trail-detail-suggestions). */
    val key: String? = null,
    /** Where the value came from, under it: "OpenStreetMap", "Forest Service", "Imported"; null where sources aren't shown. */
    val source: AppText? = null,
    /** Whether riders adopted the value: [source] then reads as a badge, "Community · 12 riders". */
    val community: Boolean = false,
    /** A line after [source]: its open suggestions, or that riders report it may have changed. */
    val note: AppText? = null,
    /** Whether [value] is only suggested so far, shown as a question ("Paved?") until riders adopt it. */
    val question: Boolean = false,
    /** Whether tapping the row opens its sheet: a rider's, on a detail riders may change. */
    val opens: Boolean = false,
    /** On the Trail page, the segments riders changed this detail on to differ from the trail's (segments/trail-attributes). */
    val varying: ImmutableList<VaryingSegment> = persistentListOf(),
) {
    /** [varying] as a plain list, a copy, for Swift. */
    val shownVarying: List<VaryingSegment>
        get() = varying.toList()
}

/** A segment whose community value of a detail differs from its trail's: its [name], [value] and [source], opening its page by [uri]. */
@Immutable
data class VaryingSegment(val uri: String, val name: String, val value: AppText, val source: AppText) {
    /** The line under its name, both UIs': its [value] then its [source], "Gravel · Community". */
    val detail: AppText
        get() = AppText.named("segment_browse_details", value, source)
}

/**
 * One card of a Trail details section (segments/trail-attributes): its [title] and its [rows], in the section's order. Swift reads
 * [shownRows], a copy, as an array.
 */
@Immutable
data class TrailDetailGroup(val title: AppText, val rows: ImmutableList<TrailDetail>) {
    /** [rows] as a plain list, a copy, since the immutable library's shared empty list stops the iOS app when read through a `List`. */
    val shownRows: List<TrailDetail>
        get() = rows.toList()
}

/**
 * What a Trail details section says on the Segment and Trail pages (segments/trail-attributes, dev/shared-display-text), for the shared and
 * the native UI: one [TrailDetail] per attribute the served `trail` holds, in the spec's order, and none for one it lacks.
 */
@Suppress("TooManyFunctions") // One per attribute, so both UIs read the same words.
object TrailTexts {
    /** The section's heading. */
    val title: AppText = AppText.named("trail_details_title")

    /**
     * [trail]'s rows. On a segment page [sport] is its `SegmentSportGroup`, and only the difficulty scales that fit it are shown; on the
     * Trail page it is null and every scale is. Empty without a [trail], so the section is left out.
     */
    fun rows(trail: TrailAttributes?, sport: String?): List<TrailDetail> {
        if (trail == null) return emptyList()
        return surfaceRows(trail) + difficulties(trail, ScaleFilter(sport)) + measures(trail, ScaleFilter(sport)) + access(trail)
    }

    /**
     * [trail]'s rows in their cards: Difficulty (the scales, trail class and visibility), Surface & width (surface, width and typical
     * grade) and Access & season (the rest), each left out when it has no rows. [sport] filters the scales as in [rows]. Empty without a
     * [trail].
     */
    fun groups(trail: TrailAttributes?, sport: String?): List<TrailDetailGroup> {
        if (trail == null) return emptyList()
        val fits = ScaleFilter(sport)
        return listOf(
                "trail_group_difficulty" to difficulties(trail, fits),
                "trail_group_surface" to surfaceRows(trail) + measures(trail, fits),
                "trail_group_access" to access(trail),
            )
            .filter { (_, rows) -> rows.isNotEmpty() }
            .map { (title, rows) -> TrailDetailGroup(named(title), rows.toImmutableList()) }
    }

    /**
     * The Trail page's folded line (segments/trail-attributes): up to [SUMMARY_VALUES] of the surface, the kind of trail its width makes,
     * whether dogs may come and the season, joined with " · "; null when [trail] holds none of them.
     */
    fun summary(trail: TrailAttributes?): AppText? {
        if (trail == null) return null
        val values =
            listOfNotNull(
                surface(trail),
                trail.widthCm?.takeIf { it > 0 }?.let(::width),
                trail.dog?.takeIf { it in TrailDog.all }?.let { named("trail_dog_$it") },
                season(trail),
            )
        return values.take(SUMMARY_VALUES).reduceOrNull(::joined)
    }

    /** The Trail details control on the Trail page: its label, and what it does for a screen reader. */
    fun toggleLabel(expanded: Boolean): AppText = named(if (expanded) "trail_details_collapse" else "trail_details_expand")

    private fun surfaceRows(trail: TrailAttributes): List<TrailDetail> =
        listOfNotNull(surface(trail)?.let { row("trail_detail_surface", it) })

    /** The difficulty scales that fit [fits], then the trail class and visibility, which fit every sport group. */
    private fun difficulties(trail: TrailAttributes, fits: ScaleFilter): List<TrailDetail> =
        listOfNotNull(
            word("trail_detail_hiking", trail.sacScale?.takeIf { fits.hiking }, TrailSacScale.all, "trail_sac_"),
            scale("trail_detail_mtb", "trail_mtb_", trail.mtbScale?.takeIf { fits.ride }, TrailAttributes.MAX_MTB_SCALE),
            scale(
                "trail_detail_mtb_uphill",
                "trail_mtb_uphill_",
                trail.mtbScaleUphill?.takeIf { fits.ride },
                TrailAttributes.MAX_MTB_SCALE_UPHILL,
            ),
            trail.mtbScaleImba
                ?.takeIf { fits.ride && it in 0..TrailAttributes.MAX_MTB_SCALE_IMBA }
                ?.let { row("trail_detail_imba", named("trail_imba_$it")) },
            word("trail_detail_piste", trail.pisteDifficulty?.takeIf { fits.piste }, TrailPisteDifficulty.all, "trail_piste_"),
            word("trail_detail_grooming", trail.pisteGrooming?.takeIf { fits.grooming }, TrailPisteGrooming.all, "trail_grooming_"),
            trail.trailClass
                ?.takeIf { it in 1..TrailAttributes.MAX_TRAIL_CLASS }
                ?.let { row("trail_detail_class", named("trail_class_value", AppText.whole(it), named("trail_class_$it"))) },
            word("trail_detail_visibility", trail.visibility, TrailVisibility.all, "trail_visibility_"),
        )

    /** The kind of trail its width makes, where [fits] says so, and the typical grade. */
    private fun measures(trail: TrailAttributes, fits: ScaleFilter): List<TrailDetail> =
        listOfNotNull(
            trail.widthCm?.takeIf { it > 0 && fits.widthKind }?.let { row("trail_detail_width", width(it)) },
            trail.gradePercent?.takeIf { it >= 0 }?.let { row("trail_detail_grade", named("unit_percent", AppText.whole(it))) },
        )

    /** Lighting, dogs, horses, accessibility, the season, who maintains it and its website. */
    private fun access(trail: TrailAttributes): List<TrailDetail> =
        listOfNotNull(
            word("trail_detail_lit", trail.lit, TrailLit.all, "trail_lit_"),
            word("trail_detail_dog", trail.dog, TrailDog.all, "trail_dog_"),
            word("trail_detail_horse", trail.horse, TrailHorse.all, "trail_horse_"),
            word("trail_detail_wheelchair", trail.wheelchair, TrailWheelchair.all, "trail_wheelchair_"),
            season(trail)?.let { row("trail_detail_season", it) },
            trail.operator?.trim()?.takeIf { it.isNotEmpty() }?.let { row("trail_detail_operator", AppText.written(it)) },
            website(trail.website)?.let { TrailDetail(named("trail_detail_website"), AppText.written(it), link = it, key = "website") },
        )

    /** A row naming [value] by its string `<prefix><value>`, when it is one of [values]. */
    private fun word(label: String, value: String?, values: List<String>, prefix: String): TrailDetail? =
        value?.takeIf { it in values }?.let { row(label, named("$prefix$it")) }

    /** An MTB scale's row in words (`<prefix><value>`: "Intermediate: roots, rocks and small steps"), when [value] is 0 to [max]. */
    private fun scale(label: String, prefix: String, value: Int?, max: Int): TrailDetail? =
        value?.takeIf { it in 0..max }?.let { row(label, named("$prefix$it")) }

    /**
     * The Width row's value: the kind of trail [widthCm] makes, singletrack under [DOUBLETRACK_CM], doubletrack under [ROAD_CM], else road
     * width. The number itself isn't shown: riders and runners think in these terms.
     */
    fun width(widthCm: Int): AppText =
        named(
            when {
                widthCm < DOUBLETRACK_CM -> "trail_width_singletrack"
                widthCm < ROAD_CM -> "trail_width_doubletrack"
                else -> "trail_width_road"
            },
        )

    /**
     * The Surface row's value: the surfaces with their shares ("70% paved · 30% gravel"), or the surface alone at 100% ("Gravel"), then the
     * smoothness in words when known; null when neither is known.
     */
    fun surface(trail: TrailAttributes): AppText? {
        val surfaces = trail.surfaces.orEmpty().filter { it.surface in TrailSurfaces.all && it.percent in 1..PERCENT }
        val shares =
            if (surfaces.size == 1 && surfaces.single().percent == PERCENT) {
                named("trail_surface_only_${surfaces.single().surface}")
            } else {
                surfaces.map(::share).reduceOrNull(::joined)
            }
        val smoothness = trail.smoothness?.takeIf { it in TrailSmoothness.all }?.let { named("trail_smoothness_$it") }
        return listOfNotNull(shares, smoothness).reduceOrNull(::joined)
    }

    /**
     * The Season row's value: the open dates ("Open Jun 15 – Oct 15"), else the seasons ("Winter only", "Open in spring, summer"), else
     * "Seasonal"; null when none is known.
     */
    fun season(trail: TrailAttributes): AppText? {
        val from = monthDay(trail.openFrom)
        val until = monthDay(trail.openUntil)
        if (from != null && until != null) return named("trail_season_dates", from, until)
        val seasons = trail.seasons.orEmpty().filter { it in TrailSeason.all }.distinct()
        return when {
            seasons.size == 1 -> named("trail_season_only_${seasons.single()}")
            seasons.size > 1 -> AppText.joined(seasons.map { named("trail_season_$it") })?.let { named("trail_season_open_in", it) }
            trail.seasonal == true -> named("trail_season_seasonal")
            else -> null
        }
    }

    /** [website] when it is an http or https URL a row may open, else null: clients check the scheme again before opening anything. */
    fun website(website: String?): String? = website?.trim()?.takeIf(TrailAttributes::isWebsite)

    /** Which difficulty scales a segment of one sport group shows; every scale on the Trail page, where [sport] is null. */
    private class ScaleFilter(sport: String?) {
        // The scales a rating reads (segments/trail-ratings) come from the same rule, so the rows and the map can't disagree.
        private val scales = TrailScales(sport)
        val hiking = scales.hiking
        val ride = scales.ride
        val piste = scales.piste
        val grooming = sport == null || sport == SegmentSportGroup.SKI || sport == SegmentSportGroup.ALPINE
        /** Whether the width shows, as a kind of trail: not on ski and alpine segments, where singletrack means nothing. */
        val widthKind = sport != SegmentSportGroup.SKI && sport != SegmentSportGroup.ALPINE
    }

    private fun share(surface: TrailSurface): AppText =
        named("trail_surface_share", AppText.whole(surface.percent), named("trail_surface_${surface.surface}"))

    private fun monthDay(text: String?): AppText? {
        if (text == null || !TrailAttributes.isMonthDay(text)) return null
        return DateText.monthDay(text.take(2).toInt(), text.takeLast(2).toInt())
    }

    private fun row(label: String, value: AppText) = TrailDetail(named(label), value, key = LABEL_KEYS[label])

    /** Each row's label to the detail it shows. */
    private val LABEL_KEYS =
        persistentMapOf(
            "trail_detail_surface" to "surface",
            "trail_detail_hiking" to "sacScale",
            "trail_detail_mtb" to "mtbScale",
            "trail_detail_mtb_uphill" to "mtbScaleUphill",
            "trail_detail_imba" to "mtbScaleImba",
            "trail_detail_piste" to "pisteDifficulty",
            "trail_detail_grooming" to "pisteGrooming",
            "trail_detail_class" to "trailClass",
            "trail_detail_visibility" to "visibility",
            "trail_detail_width" to "width",
            "trail_detail_grade" to "grade",
            "trail_detail_lit" to "lit",
            "trail_detail_dog" to "dog",
            "trail_detail_horse" to "horse",
            "trail_detail_wheelchair" to "wheelchair",
            "trail_detail_season" to "season",
            "trail_detail_operator" to "operator",
            "trail_detail_website" to "website",
        )

    /** The row label of the detail [key] (a lexicon `TrailDetail` name). */
    fun labelOf(key: String): AppText = named(LABEL_KEYS.entries.firstOrNull { it.value == key }?.key ?: "trail_detail_smoothness")

    private fun joined(first: AppText, next: AppText): AppText = named("segment_browse_details", first, next)

    private fun named(name: String, vararg args: AppText): AppText = AppText.named(name, *args)

    private const val PERCENT = 100
    private const val SUMMARY_VALUES = 3
    /** The narrowest width that is doubletrack, in centimetres. */
    const val DOUBLETRACK_CM = 150
    /** The narrowest width that is road width, in centimetres. */
    const val ROAD_CM = 300
}
