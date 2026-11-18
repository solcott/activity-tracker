package io.github.solcott.activitytracker.data.chart

import kotlin.math.abs

/**
 * The index of the item in this list (sorted by [xOf], not empty) whose x is closest to [x]; the earlier one on a tie, and the first or
 * last item for an [x] before or past them all. A chart's readout follows it.
 */
fun <T> List<T>.nearestIndexBy(x: Double, xOf: (T) -> Double): Int {
    require(isNotEmpty()) { "no item is nearest in an empty list" }
    val found = binarySearch { xOf(it).compareTo(x) }
    if (found >= 0) return found
    val after = -found - 1
    return when {
        after == 0 -> 0
        after == size -> lastIndex
        abs(xOf(this[after]) - x) < abs(xOf(this[after - 1]) - x) -> after
        else -> after - 1
    }
}
