package io.github.solcott.activitytracker.lexicon.app

/**
 * Which [SegmentSportGroup] an activity type can match segments in. An activity type maps to at most one group; indoor and virtual types,
 * lift-served skiing and whitewater map to none, so they never match a segment.
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
                sport == Sport.SKI -> SegmentSportGroup.SKI.takeIf { subSport == SubSport.BACKCOUNTRY }
                else -> GROUPS[sport]
            }
        return group?.let { Grouped(it, eBike = it == SegmentSportGroup.RIDE && subSport in E_BIKE) }
    }
}
