package io.github.solcott.activitytracker.location

import kotlin.math.PI
import kotlin.math.cos
import kotlin.time.Instant

private const val BISECTION_STEPS = 30
private const val HALF_TURN_DEGREES = 180.0

/**
 * [points] without the parts inside any of [circles] or within [endsMeters] of either end (measured along the track), cut where the track
 * crosses each boundary: the cut point is interpolated on the shown side, so none of the result is hidden. The track is the lines between
 * its points, so a line between two points outside a zone that passes through it is cut too. A middle pass through a zone becomes a
 * straight line from where the track entered to where it left. Empty when everything is hidden.
 */
fun TrackMath.trim(points: List<LocationSample>, circles: List<PrivacyCircle>, endsMeters: Double = 0.0): List<LocationSample> =
    trimPieces(points, circles, endsMeters).flatten()

/**
 * [points] trimmed as [trim] does, but split into the stretches that are shown: a new piece starts wherever the track comes back out of
 * what is hidden, including a zone crossed between two points outside it, so nothing joins one piece to the next. Empty when everything is
 * hidden.
 */
fun TrackMath.trimPieces(points: List<LocationSample>, circles: List<PrivacyCircle>, endsMeters: Double = 0.0): List<List<LocationSample>> {
    if (points.isEmpty() || (circles.isEmpty() && endsMeters <= 0.0)) return listOf(points).filter { it.isNotEmpty() }
    val along = DoubleArray(points.size)
    for (i in 1..points.lastIndex) along[i] = along[i - 1] + distanceMeters(points[i - 1], points[i])
    val total = along.last()

    val pieces = mutableListOf<List<LocationSample>>()
    val piece = mutableListOf<LocationSample>()
    if (shownAtStart(points.first(), circles, endsMeters)) piece += points.first()
    for (i in 1..points.lastIndex) {
        val a = points[i - 1]
        val b = points[i]
        val spans = hiddenSpans(a, b, along[i - 1], along[i], total, circles, endsMeters)
        for (span in spans) {
            if (!span.fromStart) {
                piece += pointAt(a, b, span.start)
                pieces += piece.toList()
                piece.clear()
            }
            if (!span.toEnd) piece += pointAt(a, b, span.end)
        }
        if (spans.none { it.toEnd }) piece += b
    }
    pieces += piece.toList()
    return pieces.filter { it.isNotEmpty() }
}

/** Whether a track's first point, [first], is shown: in no zone, and no hidden start, since any hides the point it starts from. */
private fun shownAtStart(first: LocationSample, circles: List<PrivacyCircle>, endsMeters: Double): Boolean =
    endsMeters <= 0.0 && circles.none { it.contains(first.latitude, first.longitude) }

/**
 * The stretches of the line [a]–[b] (its ends [alongA] and [alongB] meters along a track [total] long) that a zone or a hidden end covers,
 * in order and joined where they overlap.
 */
@Suppress("LongParameterList") // The line, where it lies along the track, and what hides it.
private fun hiddenSpans(
    a: LocationSample,
    b: LocationSample,
    alongA: Double,
    alongB: Double,
    total: Double,
    circles: List<PrivacyCircle>,
    endsMeters: Double,
): List<Span> {
    fun alongAt(t: Double) = if (t == 1.0) alongB else alongA + (alongB - alongA) * t
    return buildList {
        if (endsMeters > 0.0) {
            hiddenSpan { t -> alongAt(t) < endsMeters }?.let(::add)
            hiddenSpan { t -> total - alongAt(t) < endsMeters }?.let(::add)
        }
        for (circle in circles) {
            val inside = { t: Double -> pointAt(a, b, t).let { circle.contains(it.latitude, it.longitude) } }
            hiddenSpan(closestFraction(a, b, circle), inside)?.let(::add)
        }
    }
        .merged()
}

/** The point [t] of the way from [a] to [b]: [a] and [b] themselves at the ends, so a point is judged the same on both its lines. */
private fun pointAt(a: LocationSample, b: LocationSample, t: Double): LocationSample =
    when (t) {
        0.0 -> a
        1.0 -> b
        else -> interpolate(a, b, t)
    }

/**
 * The fractions of a line between [start] and [end] that one zone or hidden end covers, each cut on the shown side; [fromStart] and [toEnd]
 * when it reaches the line's first or last point.
 */
private class Span(val start: Double, val end: Double, val fromStart: Boolean, val toEnd: Boolean)

/**
 * Where [hiddenAt] holds along a line, for a region it holds on in one stretch (a circle, or a hidden end): from the line's ends when they
 * are hidden, else around [inner], the fraction most likely hidden. Null when the line doesn't enter it.
 */
private inline fun hiddenSpan(inner: Double = 0.5, hiddenAt: (Double) -> Boolean): Span? {
    val fromStart = hiddenAt(0.0)
    val toEnd = hiddenAt(1.0)
    val middle =
        when {
            fromStart -> 0.0
            toEnd -> 1.0
            hiddenAt(inner) -> inner
            else -> return null
        }
    return Span(
        start = if (fromStart) 0.0 else edge(shown = 0.0, hidden = middle, hiddenAt),
        end = if (toEnd) 1.0 else edge(shown = 1.0, hidden = middle, hiddenAt),
        fromStart = fromStart,
        toEnd = toEnd,
    )
}

/** [this] sorted, with overlapping spans joined. */
private fun List<Span>.merged(): List<Span> = sortedBy {
    it.start
}
    .fold(mutableListOf<Span>()) { merged, span ->
        val last = merged.lastOrNull()
        if (last != null && span.start <= last.end) {
            val toEnd = span.toEnd || last.toEnd
            merged[merged.lastIndex] = Span(last.start, if (toEnd) 1.0 else maxOf(last.end, span.end), last.fromStart, toEnd)
        } else {
            merged += span
        }
        merged
    }

/**
 * The fraction along [a]–[b] that comes closest to [circle]'s center, in a flat projection around it: lines between samples are far shorter
 * than the distance at which it drifts by a meter.
 */
private fun closestFraction(a: LocationSample, b: LocationSample, circle: PrivacyCircle): Double {
    val scale = cos(circle.latitude * PI / HALF_TURN_DEGREES)
    fun x(s: LocationSample) = wrapped(s.longitude - circle.longitude) * scale
    fun y(s: LocationSample) = s.latitude - circle.latitude
    val dx = x(b) - x(a)
    val dy = y(b) - y(a)
    val length = dx * dx + dy * dy
    return if (length == 0.0) 0.0 else (-(x(a) * dx + y(a) * dy) / length).coerceIn(0.0, 1.0)
}

/** [degrees] of longitude difference taken the short way round. */
private fun wrapped(degrees: Double): Double = ((degrees + HALF_TURN_DEGREES).mod(2 * HALF_TURN_DEGREES)) - HALF_TURN_DEGREES

/** The fraction between [shown] and [hidden] where [hiddenAt] starts to hold, found by bisection, on the shown side. */
private inline fun edge(shown: Double, hidden: Double, hiddenAt: (Double) -> Boolean): Double {
    var outside = shown
    var inside = hidden
    repeat(BISECTION_STEPS) {
        val mid = (outside + inside) / 2
        if (hiddenAt(mid)) inside = mid else outside = mid
    }
    return outside
}

private fun interpolate(a: LocationSample, b: LocationSample, t: Double): LocationSample {
    fun mix(x: Double, y: Double) = x + (y - x) * t
    val millis = a.time.toEpochMilliseconds() + ((b.time.toEpochMilliseconds() - a.time.toEpochMilliseconds()) * t).toLong()
    return LocationSample(
        latitude = mix(a.latitude, b.latitude),
        longitude = mix(a.longitude, b.longitude),
        altitude = if (a.altitude != null && b.altitude != null) mix(a.altitude, b.altitude) else null,
        speed = null,
        accuracy = null,
        time = Instant.fromEpochMilliseconds(millis),
    )
}
