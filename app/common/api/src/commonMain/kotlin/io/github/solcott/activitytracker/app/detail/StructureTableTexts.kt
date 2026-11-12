package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import kotlinx.collections.immutable.toImmutableList

/**
 * The legs, lengths and sets tables (feed/multisport-legs, feed/swim-lengths, feed/strength-sets, dev/shared-display-text): their headers,
 * each row's cells, a rest's or transition's row with its time alone, and each row's spoken sentence. Both UIs ask them.
 */
object StructureTableTexts {
    /** A pool swim's lengths: each one's number, stroke, time, stroke count and SWOLF; a rest, its time only. */
    fun lengths(lengths: List<LengthRow>): DetailTable {
        val headers = listOf("length_number", "length_stroke", "lap_time", "length_strokes", "length_swolf").map(AppText::named)
        val rows = lengths.map { length ->
            val time = time(length.seconds)
            if (length.rest) {
                listOf(rest, missing, time, missing, missing)
            } else {
                listOf(
                    AppText.written("${length.number}"),
                    length.stroke?.let(::stroke) ?: missing,
                    time,
                    whole(length.strokes),
                    whole(length.swolf),
                )
            }
        }
        return table(headers, rows, wideColumn = 1)
    }

    /** A workout's sets: each one's number, exercise, reps, weight and time; a rest, its time only. */
    fun sets(sets: List<SetRow>, units: UnitSystem): DetailTable {
        val headers = listOf("set_number", "set_exercise", "set_reps", "set_weight", "lap_time").map(AppText::named)
        val rows = sets.map { set ->
            val time = time(set.seconds)
            if (set.rest) {
                listOf(rest, missing, missing, missing, time)
            } else {
                listOf(
                    AppText.written("${set.number}"),
                    exercise(set),
                    whole(set.reps),
                    set.weightGrams?.let { StatText.weight(it.toLong(), units) } ?: missing,
                    time,
                )
            }
        }
        return table(headers, rows, wideColumn = 1)
    }

    /** A multisport activity's legs: each one's sport, time, distance and pace or speed as fits its sport; a transition, its time only. */
    fun legs(legs: List<LegRow>, units: UnitSystem): DetailTable {
        val headers = listOf("leg_sport", "leg_time", "leg_distance", "leg_rate").map(AppText::named)
        val rows = legs.map { leg ->
            val time = time(leg.seconds)
            if (leg.transition) {
                listOf(AppText.named("leg_transition"), time, missing, missing)
            } else {
                val meters = leg.distanceMeters?.toDouble()
                listOf(
                    LabelText.activityType(ActivityType(leg.sport, leg.subSport)),
                    time,
                    meters?.let { StatText.distance(it, units) } ?: missing,
                    StatText.rate(leg.sport, meters, leg.seconds.toLong(), units)?.value ?: missing,
                )
            }
        }
        return table(headers, rows, wideColumn = -1)
    }

    /** A swim stroke's name, by its token; an unknown one readably. */
    fun stroke(token: String): AppText = AppText.Named("stroke_$token", fallback = AppText.written(LapTableTexts.readableToken(token)))

    /** A set's exercise: the app's name for it or its category, else the exercise's token readably, else its category's. */
    fun exercise(set: SetRow): AppText {
        val exercise = set.exercise
        val category = set.category
        return when {
            exercise != null -> AppText.Named("exercise_$exercise", fallback = AppText.written(LapTableTexts.readableToken(exercise)))
            category != null -> AppText.Named("exercise_$category", fallback = AppText.written(LapTableTexts.readableToken(category)))
            else -> AppText.named("set_unknown_exercise")
        }
    }

    private fun table(headers: List<AppText>, rows: List<List<AppText>>, wideColumn: Int): DetailTable =
        DetailTable(
            headers.toImmutableList(),
            rows
                .map { cells ->
                    val name = AppText.named("table_cell_description", headers.first(), cells.first())
                    TableRow(cells.toImmutableList(), LapTableTexts.spoken(name, headers.drop(1), cells.drop(1)))
                }
                .toImmutableList(),
            wideFirst = false,
            wideColumn = wideColumn,
        )

    private fun time(seconds: Int): AppText = AppText.written(StatText.duration(seconds.toLong()))

    private fun whole(value: Int?): AppText = value?.let { AppText.written(formatWhole(it)) } ?: missing

    private val rest = AppText.named("table_rest")
    private val missing = StatText.missing
}
