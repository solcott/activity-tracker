package io.github.solcott.activitytracker.lexicon.app

/**
 * What an activity's `cycles` counts, by [Sport]: the lexicon names it per sport, and other sports' counts (a handcycle's crank
 * revolutions, turned by hand) aren't shown.
 */
object CycleCount {
    const val STEPS = "steps"
    const val STROKES = "strokes"
    const val PUSHES = "pushes"
    const val PEDAL_STROKES = "pedalStrokes"

    private val STEP_SPORTS = setOf(Sport.RUN, Sport.WALK, Sport.HIKE, Sport.RUCKING, Sport.MOUNTAINEERING, Sport.ORIENTEERING)
    private val STROKE_SPORTS = setOf(Sport.SWIM, Sport.ROW, Sport.KAYAK, Sport.CANOE, Sport.SUP)

    /** [STEPS], [STROKES], [PUSHES] or [PEDAL_STROKES] for [sport]; null for a sport whose count isn't shown. */
    fun unitOf(sport: String): String? =
        when (sport) {
            in STEP_SPORTS -> STEPS
            in STROKE_SPORTS -> STROKES
            Sport.WHEELCHAIR -> PUSHES
            // A ride's crank revolutions, whatever its sub-sport (mountain, gravel, indoor, e-bike, velomobile).
            Sport.RIDE -> PEDAL_STROKES
            else -> null
        }
}
