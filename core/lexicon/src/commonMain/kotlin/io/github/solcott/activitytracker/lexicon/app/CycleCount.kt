package io.github.solcott.activitytracker.lexicon.app

/**
 * What an activity's `cycles` counts, by [Sport]: the lexicon names it per sport, and other sports' counts (a ride's crank revolutions)
 * aren't shown.
 */
object CycleCount {
    const val STEPS = "steps"
    const val STROKES = "strokes"
    const val PUSHES = "pushes"

    private val STEP_SPORTS = setOf(Sport.RUN, Sport.WALK, Sport.HIKE, Sport.RUCKING, Sport.MOUNTAINEERING, Sport.ORIENTEERING)
    private val STROKE_SPORTS = setOf(Sport.SWIM, Sport.ROW, Sport.KAYAK, Sport.CANOE, Sport.SUP)

    /** [STEPS], [STROKES] or [PUSHES] for [sport]; null for a sport whose count isn't shown. */
    fun unitOf(sport: String): String? =
        when (sport) {
            in STEP_SPORTS -> STEPS
            in STROKE_SPORTS -> STROKES
            Sport.WHEELCHAIR -> PUSHES
            else -> null
        }
}
