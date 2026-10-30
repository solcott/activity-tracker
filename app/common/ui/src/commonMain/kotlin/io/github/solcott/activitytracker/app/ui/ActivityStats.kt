package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.stat_distance
import io.github.solcott.activitytracker.composeApp.resources.stat_elevation
import io.github.solcott.activitytracker.data.sport.RateKind
import io.github.solcott.activitytracker.data.sport.SportCatalog
import org.jetbrains.compose.resources.stringResource

/** The distance stat, or null for a sport without a rate whose distance is absent or zero, like a strength workout's 0 m. */
@Composable
fun distanceStat(sport: String, meters: Double?): Stat? =
    if (showsLength(sport, meters)) Stat(stringResource(Res.string.stat_distance), Format.distance(meters)) else null

/** The elevation gain stat, or null for a sport without a rate whose gain is absent or zero. */
@Composable
fun elevationStat(sport: String, meters: Double?): Stat? =
    if (showsLength(sport, meters)) Stat(stringResource(Res.string.stat_elevation), Format.elevation(meters)) else null

/** Sports with a rate (see [Format.rateStat]) always show distance and elevation, a dash when absent; the others only when above zero. */
internal fun showsLength(sport: String, meters: Double?): Boolean =
    SportCatalog.rate(sport) != RateKind.None || (meters != null && meters > 0)
