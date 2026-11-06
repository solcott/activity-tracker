package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.lexicon.app.SegmentElevationSource
import io.github.solcott.activitytracker.location.ElevationGain
import io.github.solcott.activitytracker.location.GainLoss
import io.github.solcott.activitytracker.location.SegmentPass
import io.github.solcott.activitytracker.location.TimedPoint
import kotlin.math.roundToLong
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update

/** A segment's elevation as served (segments/segment-elevation): gain and loss in meters, and where they came from. */
data class SegmentElevation(val gain: Long, val loss: Long, val source: String)

/**
 * The elevation [row] of [Segments] serves: its terrain's wherever terrain covers its whole line, else its efforts' once they come from at
 * least [activeAthletes] distinct athletes, else none. Never an effort's own.
 */
internal fun servedElevation(row: ResultRow, activeAthletes: Int): SegmentElevation? =
    elevationOf(row[Segments.terrainGainM], row[Segments.terrainLossM], SegmentElevationSource.TERRAIN)
        ?: elevationOf(row[Segments.effortGainM], row[Segments.effortLossM], SegmentElevationSource.EFFORTS).takeIf {
            row[Segments.effortAthletes] >= activeAthletes
        }

/** The heights [row] of [Segments] stores in [Segments.terrainProfile], in decimeters; null when it has none. */
internal fun terrainProfile(row: ResultRow): List<Int>? =
    row[Segments.terrainProfile]?.split(',')?.mapNotNull(String::toIntOrNull)?.takeIf { it.size >= 2 }

private fun elevationOf(gain: Long?, loss: Long?, source: String): SegmentElevation? =
    if (gain != null && loss != null) SegmentElevation(gain, loss, source) else null

/**
 * [pass]'s gain and loss over [piece] by its altitude: the samples it spans, plus the altitude interpolated where it starts and ends; null
 * when fewer than two of those are known.
 */
internal fun passElevation(piece: List<TimedPoint>, pass: SegmentPass): GainLoss? {
    if (piece.none { it.altM != null }) return null
    val inside = piece.filter { it.t > pass.start && it.t < pass.end }.map { it.altM }
    return ElevationGain.of(listOf(altitudeAt(piece, pass.start)) + inside + altitudeAt(piece, pass.end))
}

/** [piece]'s altitude at time [t], interpolated between the samples either side when both have one; null otherwise. */
private fun altitudeAt(piece: List<TimedPoint>, t: Double): Double? {
    val after = piece.indexOfFirst { it.t >= t }
    val b = piece.getOrNull(after)
    val a = piece.getOrNull(after - 1)
    // Other modules' properties don't smart-cast, so the altitudes are read once.
    val altA = a?.altM
    val altB = b?.altM
    return when {
        b == null -> null
        b.t == t -> altB
        a == null || altA == null || altB == null -> null
        else -> altA + (altB - altA) * (t - a.t) / (b.t - a.t)
    }
}

/**
 * Deletes the efforts [where] selects and recomputes the effort elevation of every segment they were on, so a segment's aggregate never
 * outlives the efforts it came from (segments/segment-elevation). Every effort delete goes through here or [rewriteEfforts].
 */
internal fun JdbcTransaction.deleteEfforts(where: SegmentEfforts.() -> Op<Boolean>) = rewriteEfforts(where) { emptyList() }

/**
 * Replaces the efforts [where] selects with those [insert] writes, which returns the segments they're on, and recomputes the effort
 * elevation of every segment either set was on once, after both ([deleteEfforts]).
 */
internal fun JdbcTransaction.rewriteEfforts(where: SegmentEfforts.() -> Op<Boolean>, insert: () -> Collection<String>) {
    val segments =
        SegmentEfforts.select(SegmentEfforts.segmentUri)
            .where { SegmentEfforts.where() }
            .withDistinct()
            .map { it[SegmentEfforts.segmentUri] }
    SegmentEfforts.deleteWhere { SegmentEfforts.where() }
    recomputeEffortElevation(segments + insert())
}

/**
 * Recomputes each of [segmentUris]' effort elevation: the median across athletes of each athlete's median effort gain and loss, over the
 * efforts with elevation, and how many athletes that is. A segment with none has null values and a count of 0.
 */
internal fun JdbcTransaction.recomputeEffortElevation(segmentUris: Collection<String>) {
    for (uri in segmentUris.toSet()) {
        val byAthlete =
            SegmentEfforts.select(SegmentEfforts.authorDid, SegmentEfforts.elevationGainM, SegmentEfforts.elevationLossM)
                .where {
                    (SegmentEfforts.segmentUri eq uri) and
                        SegmentEfforts.elevationGainM.isNotNull() and
                        SegmentEfforts.elevationLossM.isNotNull()
                }
                .groupBy({ it[SegmentEfforts.authorDid] }, { it[SegmentEfforts.elevationGainM] to it[SegmentEfforts.elevationLossM] })
        val gain = median(byAthlete.values.map { efforts -> median(efforts.map { it.first?.toDouble() }) })
        val loss = median(byAthlete.values.map { efforts -> median(efforts.map { it.second?.toDouble() }) })
        Segments.update({ Segments.uri eq uri }) {
            it[effortGainM] = gain?.roundToLong()
            it[effortLossM] = loss?.roundToLong()
            it[effortAthletes] = byAthlete.size
        }
    }
}

private fun median(values: List<Double?>): Double? {
    val sorted = values.filterNotNull().sorted()
    if (sorted.isEmpty()) return null
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}
