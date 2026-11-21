package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.lexicon.app.TrailPisteDifficulty
import io.github.solcott.activitytracker.lexicon.app.TrailSacScale

/**
 * Which difficulty scales fit a sport group (segments/trail-attributes, segments/trail-ratings): the SAC scale for runs and hikes, the MTB
 * scales for rides, the piste difficulty for alpine. With no sport group ([sport] null) every scale fits.
 */
class TrailScales(sport: String?) {
    val hiking: Boolean = sport == null || sport == SegmentSportGroup.RUN || sport == SegmentSportGroup.HIKE
    val ride: Boolean = sport == null || sport == SegmentSportGroup.RIDE
    val piste: Boolean = sport == null || sport == SegmentSportGroup.ALPINE
}

/**
 * How hard a trail or segment is, in the five levels trail signs use (segments/trail-ratings), easiest first. A map draws a line in its
 * level's colour and a row shows its swatch; null where nothing rates it.
 */
enum class TrailRating {
    EASIEST,
    EASY,
    INTERMEDIATE,
    DIFFICULT,
    EXTREME;

    /** The level's name, for a legend and a swatch's description. */
    val label: AppText
        get() = AppText.named("trail_rating_${name.lowercase()}")

    companion object {
        /** What a legend calls a line nothing rates. */
        val unratedLabel: AppText = AppText.named("trail_rating_unrated")

        /**
         * [trail]'s level for [sport]'s scale: a ride's IMBA rating, else its MTB scale; a run's or hike's SAC scale; an alpine run's piste
         * difficulty. With no sport group the first scale [trail] has, in that order (IMBA, MTB, SAC, piste). Null without [trail], for
         * other sport groups, and when the scale has no value it knows.
         */
        fun of(trail: TrailAttributes?, sport: String?): TrailRating? {
            if (trail == null) return null
            val fits = TrailScales(sport)
            return (if (fits.ride) imba(trail.mtbScaleImba) ?: mtb(trail.mtbScale) else null)
                ?: (if (fits.hiking) sac(trail.sacScale) else null)
                ?: (if (fits.piste) piste(trail.pisteDifficulty) else null)
        }

        /** [segment]'s level for its own sport group; a lift is never rated. */
        fun of(segment: SegmentView): TrailRating? =
            if (SegmentKind.of(segment.record.kind) == SegmentKind.LIFT) null else of(segment.trail, segment.record.sport)

        /** IMBA 0 (easiest) to 4 (extremely difficult), one level each. */
        private fun imba(value: Int?): TrailRating? = value?.takeIf { it in 0..TrailAttributes.MAX_MTB_SCALE_IMBA }?.let { entries[it] }

        /** MTB scale 0 to 3 one level each; 4 and over Extreme. */
        private fun mtb(value: Int?): TrailRating? =
            value?.takeIf { it in 0..TrailAttributes.MAX_MTB_SCALE }?.let { entries[it.coerceAtMost(EXTREME.ordinal)] }

        /** SAC T1 Easy, T2 Intermediate, T3 Difficult, any alpine grade Extreme. */
        private fun sac(value: String?): TrailRating? =
            when (value) {
                TrailSacScale.HIKING -> EASY
                TrailSacScale.MOUNTAIN_HIKING -> INTERMEDIATE
                TrailSacScale.DEMANDING_MOUNTAIN_HIKING -> DIFFICULT
                in TrailSacScale.all -> EXTREME
                else -> null
            }

        /** Novice Easiest to advanced Difficult, then expert, freeride and extreme all Extreme. */
        private fun piste(value: String?): TrailRating? =
            TrailPisteDifficulty.all.indexOf(value).takeIf { it >= 0 }?.let { entries[it.coerceAtMost(EXTREME.ordinal)] }
    }
}
