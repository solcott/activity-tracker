package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.lexicon.app.Leg
import io.github.solcott.activitytracker.lexicon.app.Length
import io.github.solcott.activitytracker.lexicon.app.WorkoutSet
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** A swim's lengths, a workout's sets and a race's legs as the detail page lists them, for [ChartsBuilder]. */
internal object StructureRows {
    fun lengths(lengths: List<Length>): ImmutableList<LengthRow> =
        numbered(lengths, { it.rest == true }) { n, l -> lengthRow(n, l) }.toImmutableList()

    fun sets(sets: List<WorkoutSet>): ImmutableList<SetRow> = numbered(sets, { it.rest == true }) { n, s -> setRow(n, s) }.toImmutableList()

    fun legs(legs: List<Leg>): ImmutableList<LegRow> = legs.map(::legRow).toImmutableList()

    /** [items] each with its number among those that aren't [rest]s (null for a rest), counting from 1. */
    private fun <T, R> numbered(items: List<T>, rest: (T) -> Boolean, row: (Int?, T) -> R): List<R> {
        var number = 0
        return items.map { item -> row(if (rest(item)) null else ++number, item) }
    }

    private fun lengthRow(number: Int?, length: Length) =
        LengthRow(
            number = number,
            stroke = length.stroke,
            seconds = length.elapsed,
            strokes = length.strokes,
            rest = length.rest == true,
        )

    private fun setRow(number: Int?, set: WorkoutSet) =
        SetRow(
            number = number,
            rest = set.rest == true,
            category = set.category,
            exercise = set.exercise,
            reps = set.reps,
            weightGrams = set.weight,
            seconds = set.elapsed,
        )

    private fun legRow(leg: Leg) =
        LegRow(
            sport = leg.sport,
            subSport = leg.subSport,
            transition = leg.transition == true,
            seconds = leg.moving ?: leg.elapsed,
            distanceMeters = leg.distance.takeUnless { leg.transition == true },
        )
}
