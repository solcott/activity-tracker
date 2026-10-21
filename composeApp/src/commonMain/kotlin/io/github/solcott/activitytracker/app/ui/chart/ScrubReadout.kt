package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.abs

/** The index of the point in [points] (sorted by x, not empty) whose x is closest to [x]; the earlier one on a tie. */
internal fun nearestIndex(points: List<SeriesPoint>, x: Double): Int {
    val found = points.binarySearch { it.x.compareTo(x) }
    if (found >= 0) return found
    val after = -found - 1
    return when {
        after == 0 -> 0
        after == points.size -> points.lastIndex
        abs(points[after].x - x) < abs(points[after - 1].x - x) -> after
        else -> after - 1
    }
}

/**
 * Where a readout for [point] puts its dots in [frame], [yRange] mapped onto its height: one on the main line, then one on each of [others]
 * that has a point at exactly [point]'s x, as Vico's marker marks every line with a value there.
 */
internal fun readoutDots(
    point: SeriesPoint,
    others: List<List<SeriesPoint>>,
    frame: AreaFrame,
    yRange: ClosedFloatingPointRange<Double>,
): List<Offset> {
    val x = frame.x(point.x)
    return buildList {
        add(Offset(x, frame.y(point.y, yRange)))
        others.forEach { line ->
            val at = line.binarySearch { it.x.compareTo(point.x) }
            if (at >= 0) add(Offset(x, frame.y(line[at].y, yRange)))
        }
    }
}

/**
 * The top left of a readout's label of [size] for dots at [dots]: centred over them, its bottom on the topmost one, or its top there when
 * it would otherwise cross the top of [bounds], and moved sideways to stay inside [bounds], as Vico's marker places its label around a
 * point.
 */
internal fun labelTopLeft(dots: List<Offset>, size: Size, bounds: Rect): Offset {
    val top = dots.minOf { it.y }
    val y = if (top - size.height < bounds.top) top else top - size.height
    val x = (dots.first().x - size.width / 2).coerceAtMost(bounds.right - size.width).coerceAtLeast(bounds.left)
    return Offset(x, y)
}
