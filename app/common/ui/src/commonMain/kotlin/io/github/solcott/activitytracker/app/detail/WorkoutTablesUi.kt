package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.activityTypeLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.composeApp.resources.lap_time
import io.github.solcott.activitytracker.composeApp.resources.leg_distance
import io.github.solcott.activitytracker.composeApp.resources.leg_rate
import io.github.solcott.activitytracker.composeApp.resources.leg_sport
import io.github.solcott.activitytracker.composeApp.resources.leg_time
import io.github.solcott.activitytracker.composeApp.resources.leg_transition
import io.github.solcott.activitytracker.composeApp.resources.length_number
import io.github.solcott.activitytracker.composeApp.resources.length_stroke
import io.github.solcott.activitytracker.composeApp.resources.length_strokes
import io.github.solcott.activitytracker.composeApp.resources.length_swolf
import io.github.solcott.activitytracker.composeApp.resources.set_exercise
import io.github.solcott.activitytracker.composeApp.resources.set_number
import io.github.solcott.activitytracker.composeApp.resources.set_reps
import io.github.solcott.activitytracker.composeApp.resources.set_unknown_exercise
import io.github.solcott.activitytracker.composeApp.resources.set_weight
import io.github.solcott.activitytracker.composeApp.resources.table_rest
import io.github.solcott.activitytracker.composeApp.resources.value_missing
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/** A pool swim's lengths: each one's number, stroke, time, stroke count and SWOLF; a rest, its time only. */
@Composable
internal fun LengthTable(lengths: ImmutableList<LengthRow>) {
    val headers =
        persistentListOf(
            stringResource(Res.string.length_number),
            stringResource(Res.string.length_stroke),
            stringResource(Res.string.lap_time),
            stringResource(Res.string.length_strokes),
            stringResource(Res.string.length_swolf),
        )
    val rest = stringResource(Res.string.table_rest)
    val missing = stringResource(Res.string.value_missing)
    val rows = lengths.map { length ->
        val time = Format.duration(length.seconds.toLong())
        if (length.rest) {
            persistentListOf(rest, missing, time, missing, missing)
        } else {
            persistentListOf(
                "${length.number}",
                length.stroke?.let { strokeLabel(it) } ?: missing,
                time,
                filled(length.strokes) { formatWhole(it) },
                filled(length.swolf) { formatWhole(it) },
            )
        }
    }
    StructureTable(headers, rows.toImmutableList(), wideColumn = 1)
}

/** A workout's sets: each one's number, exercise, reps, weight and time; a rest, its time only. */
@Composable
internal fun SetTable(sets: ImmutableList<SetRow>) {
    val headers =
        persistentListOf(
            stringResource(Res.string.set_number),
            stringResource(Res.string.set_exercise),
            stringResource(Res.string.set_reps),
            stringResource(Res.string.set_weight),
            stringResource(Res.string.lap_time),
        )
    val rest = stringResource(Res.string.table_rest)
    val missing = stringResource(Res.string.value_missing)
    val rows = sets.map { row ->
        val time = Format.duration(row.seconds.toLong())
        if (row.rest) {
            persistentListOf(rest, missing, missing, missing, time)
        } else {
            persistentListOf(
                "${row.number}",
                exerciseLabel(row),
                filled(row.reps) { formatWhole(it) },
                filled(row.weightGrams) { Format.weight(it.toLong()) },
                time,
            )
        }
    }
    StructureTable(headers, rows.toImmutableList(), wideColumn = 1)
}

/** A multisport activity's legs: each one's sport, time, distance and pace or speed as fits its sport; a transition, its time only. */
@Composable
internal fun LegTable(legs: ImmutableList<LegRow>) {
    val headers =
        persistentListOf(
            stringResource(Res.string.leg_sport),
            stringResource(Res.string.leg_time),
            stringResource(Res.string.leg_distance),
            stringResource(Res.string.leg_rate),
        )
    val transition = stringResource(Res.string.leg_transition)
    val missing = stringResource(Res.string.value_missing)
    val rows = legs.map { leg ->
        val time = Format.duration(leg.seconds.toLong())
        if (leg.transition) {
            persistentListOf(transition, time, missing, missing)
        } else {
            val meters = leg.distanceMeters?.toDouble()
            persistentListOf(
                activityTypeLabel(ActivityType(leg.sport, leg.subSport)),
                time,
                filled(meters) { Format.distance(it) },
                Format.rateStat(leg.sport, meters, leg.seconds.toLong())?.value ?: missing,
            )
        }
    }
    StructureTable(headers, rows.toImmutableList(), firstWidth = ROW_NAME_WIDTH)
}

/** A header row, then each of [rows], each read by a screen reader as one sentence; the [wideColumn] (a name) gets twice the width. */
@Composable
private fun StructureTable(
    headers: ImmutableList<String>,
    rows: ImmutableList<ImmutableList<String>>,
    firstWidth: Dp = ROW_NUMBER_WIDTH,
    wideColumn: Int = -1,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TableCells(headers, header = true, firstWidth = firstWidth, wideColumn = wideColumn)
        for (row in rows) {
            val spoken = spokenRow("${headers.first()} ${row.first()}", headers.drop(1).toImmutableList(), row.drop(1).toImmutableList())
            TableCells(row, header = false, Modifier.clearAndSetSemantics { contentDescription = spoken }, firstWidth, wideColumn)
        }
    }
}

/** [value] as [text] puts it, or a dash when the row has none. */
@Composable
private fun <T : Any> filled(value: T?, text: @Composable (T) -> String): String =
    value?.let { text(it) } ?: stringResource(Res.string.value_missing)

/** A swim stroke's name, by its token; an unknown one readably. */
@Composable
private fun strokeLabel(token: String): String = Res.allStringResources["stroke_$token"]?.let { stringResource(it) } ?: readableToken(token)

/** A set's exercise: the app's name for it or its category, else the exercise's token readably, else its category's. */
@Composable
internal fun exerciseLabel(row: SetRow): String {
    fun labelled(token: String?) = token?.let { Res.allStringResources["exercise_$it"] }
    val resource = labelled(row.exercise) ?: labelled(row.category).takeIf { row.exercise == null }
    if (resource != null) return stringResource(resource)
    val token = row.exercise ?: row.category
    return if (token != null) readableToken(token) else stringResource(Res.string.set_unknown_exercise)
}

/** A token as words: "single_arm_cable_row" is "Single arm cable row", "warmUp" is "Warm up". */
internal fun readableToken(token: String): String =
    token.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").replace('_', ' ').trim().lowercase().replaceFirstChar { it.uppercase() }

private val ROW_NUMBER_WIDTH = 56.dp
