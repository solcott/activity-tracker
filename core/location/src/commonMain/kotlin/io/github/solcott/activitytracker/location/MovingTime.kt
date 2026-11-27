package io.github.solcott.activitytracker.location

/**
 * When an athlete counts as moving: the one rule the recorder, file import and trimming share, so moving time means the same wherever it's
 * worked out.
 */
object MovingTime {
    /** Slower than this, in meters per second, counts as stopped. */
    const val SPEED_MPS = 0.5

    /** Whether [speedMps] counts as moving. */
    fun isMoving(speedMps: Double): Boolean = speedMps >= SPEED_MPS

    /** Seconds spent moving along [samples], each step judged by its straight-line speed; null below two samples. */
    fun seconds(samples: List<LocationSample>): Double? =
        samples
            .takeIf { it.size >= 2 }
            ?.zipWithNext { a, b ->
                val seconds = (b.time - a.time).inWholeMilliseconds / MILLIS_PER_SECOND
                if (seconds > 0 && isMoving(TrackMath.distanceMeters(a, b) / seconds)) seconds else 0.0
            }
            ?.sum()

    private const val MILLIS_PER_SECOND = 1000.0
}
