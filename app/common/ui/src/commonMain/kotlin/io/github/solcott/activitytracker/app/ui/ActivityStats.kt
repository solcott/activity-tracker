package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.text.CardStatsText
import io.github.solcott.activitytracker.app.text.LabeledText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.stat_elevation
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/** The distance stat, or null for a sport without a rate whose distance is absent or zero, like a strength workout's 0 m. */
@Composable fun distanceStat(sport: String, meters: Double?): Stat? = CardStatsText.distance(sport, meters, LocalUnitSystem.current)?.stat()

/** The elevation gain stat, or null for a sport without a rate whose gain is absent or zero. */
@Composable
fun elevationStat(sport: String, meters: Double?): Stat? =
    if (showsLength(sport, meters)) Stat(stringResource(Res.string.stat_elevation), Format.elevation(meters)) else null

/** Sports with a rate (see [Format.rateStat]) always show distance and elevation, a dash when absent; the others only when above zero. */
internal fun showsLength(sport: String, meters: Double?): Boolean = CardStatsText.showsLength(sport, meters)

/** These labelled figures as the shared UI's strings read them. */
@Composable fun List<LabeledText>.stats(): ImmutableList<Stat> = map { it.stat() }.toImmutableList()

/** This labelled figure as the shared UI's strings read it. */
@Composable fun LabeledText.stat(): Stat = Stat(label.string(), value.string())
