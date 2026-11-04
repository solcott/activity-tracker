package io.github.solcott.activitytracker.lexicon.app

/**
 * Which [SegmentSportGroup] an activity type can match segments in. An activity type maps to at most one group; indoor and virtual types
 * and whitewater map to none, so they never match a segment. Lift-served skiing and snowboarding are [SegmentSportGroup.ALPINE];
 * backcountry and cross-country skiing are [SegmentSportGroup.SKI].
 */
object SegmentSport {
    /** An activity type's [group], and whether it's an e-bike ride (marked on its efforts). */
    data class Grouped(val group: String, val eBike: Boolean = false)

    private val INDOOR = setOf(SubSport.INDOOR, SubSport.VIRTUAL, SubSport.TREADMILL, SubSport.INDOOR_TRACK)
    private val E_BIKE = setOf(SubSport.E_BIKE, SubSport.E_MOUNTAIN)

    private val GROUPS =
        mapOf(
            Sport.RIDE to SegmentSportGroup.RIDE,
            Sport.HANDCYCLE to SegmentSportGroup.RIDE,
            Sport.RUN to SegmentSportGroup.RUN,
            Sport.HIKE to SegmentSportGroup.HIKE,
            Sport.WALK to SegmentSportGroup.HIKE,
            Sport.RUCKING to SegmentSportGroup.HIKE,
            Sport.SNOWSHOE to SegmentSportGroup.HIKE,
            Sport.XC_SKI to SegmentSportGroup.SKI,
            Sport.SNOWBOARD to SegmentSportGroup.ALPINE,
            Sport.KAYAK to SegmentSportGroup.PADDLE,
            Sport.CANOE to SegmentSportGroup.PADDLE,
            Sport.SUP to SegmentSportGroup.PADDLE,
            Sport.ROW to SegmentSportGroup.PADDLE,
        )

    /** The group of an activity of [sport] and [subSport], or null when it can't match segments. */
    fun of(sport: String, subSport: String?): Grouped? {
        val group =
            when {
                subSport in INDOOR -> null
                sport == Sport.SKI -> if (subSport == SubSport.BACKCOUNTRY) SegmentSportGroup.SKI else SegmentSportGroup.ALPINE
                else -> GROUPS[sport]
            }
        return group?.let { Grouped(it, eBike = it == SegmentSportGroup.RIDE && subSport in E_BIKE) }
    }
}

/**
 * Which leaderboard an effort is ranked on: its activity's sport, except that e-bike rides have a board of their own, [E_BIKE]. An activity
 * that can't match segments has no board.
 */
object SegmentBoard {
    /** The e-bike and e-mountain-bike board. A sub-sport token, so no sport has this value. */
    const val E_BIKE = "eBike"

    /** Every board type: each sport [SegmentSport] groups, then [E_BIKE]. */
    val all =
        listOf(
            Sport.RIDE,
            Sport.HANDCYCLE,
            Sport.RUN,
            Sport.HIKE,
            Sport.WALK,
            Sport.RUCKING,
            Sport.SNOWSHOE,
            Sport.XC_SKI,
            Sport.SKI,
            Sport.SNOWBOARD,
            Sport.KAYAK,
            Sport.CANOE,
            Sport.SUP,
            Sport.ROW,
            E_BIKE,
        )

    /** The board of an activity of [sport] and [subSport], or null when it can't match segments. */
    fun of(sport: String, subSport: String?): String? = SegmentSport.of(sport, subSport)?.let { if (it.eBike) E_BIKE else sport }
}
