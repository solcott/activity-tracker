package io.github.solcott.activitytracker.location

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

/** A position in degrees. */
data class GeoPoint(val lat: Double, val lng: Double)

/** A track sample: a position in degrees and [t], seconds on the track's clock. */
data class TimedPoint(val lat: Double, val lng: Double, val t: Double)

/** One pass over a segment from its start to its end, as interpolated track times in seconds. */
data class SegmentPass(val start: Double, val end: Double)

/**
 * The geometry segment matching shares between the AppView and clients: the precision-6 lines `feed.segment` stores, their length and
 * reference points, detecting a track's passes over them, and telling whether two segments are duplicates.
 */
@Suppress("TooManyFunctions") // The public geometry, and the plane math pass detection is built from.
object SegmentGeometry {
    /** How close a track must come to a segment's start for a pass to begin, in meters. */
    const val START_RADIUS_M = 25.0

    /** How close a track must come to a segment's end for a pass to finish, in meters. */
    const val END_RADIUS_M = 25.0

    /** How far a track may stray from a segment's line during a pass, in meters. */
    const val CORRIDOR_M = 30.0

    /** Spacing of a segment's reference points along its line, in meters. */
    const val REFERENCE_SPACING_M = 10.0

    /** How far apart two lines may get, followed from start to end, and still be duplicates, in meters: the matcher's corridor. */
    const val DUPLICATE_DISTANCE_M = CORRIDOR_M

    /** How many times longer one line may be than the other and still be its duplicate. */
    const val DUPLICATE_LENGTH_RATIO = 1.2

    /** Reference points a track sample may look ahead past the farthest its last step could reach. */
    private const val LOOKAHEAD = 3

    private const val POLYLINE_SCALE = 1e6
    private const val PRECISION_5_SCALE = 1e5
    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val DEGREES_PER_HALF_TURN = 180.0

    /** [line] as an encoded polyline at precision 6, the form `feed.segment#line` stores. */
    fun encodePolyline(line: List<GeoPoint>): String = TrackMath.encodePolyline(line.map { it.lat to it.lng }, POLYLINE_SCALE)

    /** A precision-6 encoded polyline's points, or null when it's malformed. */
    fun decodePolyline(encoded: String): List<GeoPoint>? =
        TrackMath.decodePolyline(encoded, POLYLINE_SCALE)?.map { (lat, lng) -> GeoPoint(lat, lng) }

    /** [line] at precision 5, for the maps that draw `feed.activity` polylines. */
    fun toPrecision5(line: List<GeoPoint>): String = TrackMath.encodePolyline(line.map { it.lat to it.lng }, PRECISION_5_SCALE)

    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double = TrackMath.distanceMeters(a.lat, a.lng, b.lat, b.lng)

    /** The length of [line] along its points, in meters. */
    fun lengthMeters(line: List<GeoPoint>): Double = line.zipWithNext { a, b -> distanceMeters(a, b) }.sum()

    /** Points every [every] meters along [line], from its first point to its last, both included. */
    fun resample(line: List<GeoPoint>, every: Double = REFERENCE_SPACING_M): List<GeoPoint> {
        if (line.size < 2) return line
        val out = mutableListOf(line.first())
        // Distance along the line still to go before the next reference point.
        var toNext = every
        for ((a, b) in line.zipWithNext()) {
            val length = distanceMeters(a, b)
            var at = 0.0
            while (length - at >= toNext) {
                at += toNext
                out += lerp(a, b, at / length)
                toNext = every
            }
            toNext -= length - at
        }
        if (distanceMeters(out.last(), line.last()) > 0.0) out += line.last()
        return out
    }

    /**
     * Each pass of [piece], one unbroken stretch of a track in time order, over the segment whose reference points ([resample]) are [ref].
     * A pass starts at the sample closest to the segment's start among a run of samples within [startRadius] of it. It then follows the
     * line forward, each sample within [corridor] of the line just ahead of how far it has got, and every reference point it passes within
     * [corridor] of the track, and finishes at the closest sample to the end within [endRadius] once it has reached the last stretch. Going
     * backwards, cutting a corner or leaving the corridor fails the pass. Its times are interpolated on the track at each endpoint.
     */
    fun passes(
        piece: List<TimedPoint>,
        ref: List<GeoPoint>,
        startRadius: Double = START_RADIUS_M,
        endRadius: Double = END_RADIUS_M,
        corridor: Double = CORRIDOR_M,
    ): List<SegmentPass> {
        if (piece.size < 2 || ref.size < 2) return emptyList()
        val start = ref.first()
        val passes = mutableListOf<SegmentPass>()
        var i = 0
        while (i < piece.size) {
            if (distanceMeters(piece[i].point, start) > startRadius) {
                i++
                continue
            }
            // The closest sample of this run within the start circle.
            var runEnd = i
            var closest = i
            while (runEnd + 1 < piece.size && distanceMeters(piece[runEnd + 1].point, start) <= startRadius) {
                runEnd++
                if (distanceMeters(piece[runEnd].point, start) < distanceMeters(piece[closest].point, start)) closest = runEnd
            }
            val end = follow(piece, closest, ref, endRadius, corridor)
            if (end == null) {
                i = runEnd + 1
            } else {
                passes += SegmentPass(timeAt(piece, closest, start), timeAt(piece, end, ref.last()))
                // A loop's end is its next lap's start.
                i = end
                if (end == closest) i++
            }
        }
        return passes
    }

    /**
     * Whether [a] and [b] are the same segment (segments/segment-records): they run the same direction, the longer is at most
     * [DUPLICATE_LENGTH_RATIO] times the shorter, and following both from start to end never takes them more than [distance] apart (their
     * discrete Fréchet distance, on points every [REFERENCE_SPACING_M]). The cheap length and endpoint tests run first.
     */
    @Suppress("ReturnCount") // Each cheap test that rules a pair out before the costly one.
    fun duplicates(a: List<GeoPoint>, b: List<GeoPoint>, distance: Double = DUPLICATE_DISTANCE_M): Boolean {
        if (a.size < 2 || b.size < 2) return false
        val (shorter, longer) = listOf(lengthMeters(a), lengthMeters(b)).sorted()
        if (shorter <= 0.0 || longer > shorter * DUPLICATE_LENGTH_RATIO) return false
        if (distanceMeters(a.first(), b.first()) > distance || distanceMeters(a.last(), b.last()) > distance) return false
        val origin = a.first()
        val pa = resample(a).map { local(it, origin) }
        val pb = resample(b).map { local(it, origin) }
        return frechetWithin(pa, pb, distance)
    }

    /**
     * Whether the discrete Fréchet distance between [a] and [b] (points in local meters) is at most [eps]. Only couplings within a band
     * around the diagonal, scaled to the two lengths, are tried: wide enough for one line to run ahead of the other by their whole
     * difference in points, which a duplicate can't exceed. A row with nothing reachable ends it early.
     */
    private fun frechetWithin(a: List<Pair<Double, Double>>, b: List<Pair<Double, Double>>, eps: Double): Boolean {
        val n = a.size
        val m = b.size
        val eps2 = eps * eps
        val half = ceil(2 * eps / REFERENCE_SPACING_M).toInt() + abs(n - m)
        var prev = BooleanArray(m)
        var cur = BooleanArray(m)
        for (i in 0 until n) {
            cur.fill(false)
            val center = i.toDouble() * (m - 1) / (n - 1)
            val lo = maxOf(0, floor(center).toInt() - half)
            val hi = minOf(m - 1, ceil(center).toInt() + half)
            var any = false
            for (j in lo..hi) {
                val dx = a[i].first - b[j].first
                val dy = a[i].second - b[j].second
                cur[j] = dx * dx + dy * dy <= eps2 && reachedFrom(i, j, prev, cur)
                any = any || cur[j]
            }
            if (!any) return false
            prev = cur.also { cur = prev }
        }
        return prev[m - 1]
    }

    /** Whether coupling [i] with [j] continues a coupling from the start: the first pair, or one step on from a reachable pair. */
    private fun reachedFrom(i: Int, j: Int, prev: BooleanArray, cur: BooleanArray): Boolean =
        (i == 0 && j == 0) || (i > 0 && (prev[j] || (j > 0 && prev[j - 1]))) || (j > 0 && cur[j - 1])

    /** The index of the sample a pass from [from] finishes at, or null when it fails. */
    @Suppress("ReturnCount") // Each way a pass fails, and where it finishes.
    private fun follow(piece: List<TimedPoint>, from: Int, ref: List<GeoPoint>, endRadius: Double, corridor: Double): Int? {
        val last = ref.lastIndex
        // The reference point reached so far.
        var reached = 0
        var k = from
        while (k + 1 < piece.size) {
            val prev = piece[k].point
            val p = piece[k + 1].point
            val step = distanceMeters(prev, p)
            val window = minOf(last, reached + ceil(step / REFERENCE_SPACING_M).toInt() + LOOKAHEAD)
            // The stretch of line in the window the sample is closest to.
            var nearest = reached
            var nearestDistance = Double.MAX_VALUE
            for (s in reached until window) {
                val d = toSegmentMeters(p, ref[s], ref[s + 1])
                if (d < nearestDistance) {
                    nearestDistance = d
                    nearest = s
                }
            }
            if (nearestDistance > corridor) return null
            val now = if (distanceMeters(p, ref[nearest + 1]) < distanceMeters(p, ref[nearest])) nearest + 1 else nearest
            // Every reference point skipped over must lie near the track between the two samples, or the track cut a corner.
            for (j in reached + 1..now) if (toSegmentMeters(ref[j], prev, p) > corridor) return null
            reached = maxOf(reached, now)
            k++
            if (reached >= last - 1 && distanceMeters(p, ref[last]) <= endRadius) return closestToEnd(piece, k, ref[last], endRadius)
        }
        return null
    }

    /** The sample closest to [end] from [k] on, while samples stay within [endRadius] of it and keep getting closer. */
    private fun closestToEnd(piece: List<TimedPoint>, k: Int, end: GeoPoint, endRadius: Double): Int {
        var best = k
        var j = k + 1
        while (j < piece.size) {
            val d = distanceMeters(piece[j].point, end)
            if (d > endRadius || d >= distanceMeters(piece[best].point, end)) break
            best = j
            j++
        }
        return best
    }

    /** The time [piece] passes [target], interpolated on whichever stretch next to sample [k] runs closer to it. */
    private fun timeAt(piece: List<TimedPoint>, k: Int, target: GeoPoint): Double {
        val candidates =
            listOfNotNull(
                (k - 1).takeIf { it >= 0 }?.let { piece[it] to piece[k] },
                (k + 1).takeIf { it < piece.size }?.let { piece[k] to piece[it] },
            )
        val (a, b) = candidates.minBy { (a, b) -> toSegmentMeters(target, a.point, b.point) }
        val f = projection(target, a.point, b.point)
        return a.t + (b.t - a.t) * f
    }

    /** How far along a→b, from 0 to 1, [p] projects. */
    private fun projection(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val (ax, ay) = local(a, p)
        val (bx, by) = local(b, p)
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 == 0.0) return 0.0
        return ((-ax) * dx + (-ay) * dy).div(len2).coerceIn(0.0, 1.0)
    }

    /** Distance from [p] to the stretch a→b, in meters. */
    private fun toSegmentMeters(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val f = projection(p, a, b)
        val (ax, ay) = local(a, p)
        val (bx, by) = local(b, p)
        val x = ax + (bx - ax) * f
        val y = ay + (by - ay) * f
        return sqrt(x * x + y * y)
    }

    /** [q] in meters east and north of [origin] (equirectangular, fine at segment scale). */
    private fun local(q: GeoPoint, origin: GeoPoint): Pair<Double, Double> {
        val x = (q.lng - origin.lng).toRadians() * cos(origin.lat.toRadians()) * EARTH_RADIUS_M
        val y = (q.lat - origin.lat).toRadians() * EARTH_RADIUS_M
        return x to y
    }

    private fun lerp(a: GeoPoint, b: GeoPoint, f: Double) = GeoPoint(a.lat + (b.lat - a.lat) * f, a.lng + (b.lng - a.lng) * f)

    private val TimedPoint.point
        get() = GeoPoint(lat, lng)

    private fun Double.toRadians() = this * PI / DEGREES_PER_HALF_TURN
}
