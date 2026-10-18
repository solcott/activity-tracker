package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.ui.Stat
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.chart_battery_used
import io.github.solcott.activitytracker.composeApp.resources.chart_ebike_battery
import io.github.solcott.activitytracker.composeApp.resources.chart_left_right
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/** Each leg's average torque effectiveness and pedal smoothness: what a rider compares between legs. */
@Composable
internal fun cyclingDynamicsOverview(charts: ActivityCharts): List<Stat> =
    listOf(StreamKind.TorqueEffectiveness, StreamKind.PedalSmoothness).mapNotNull { kind ->
        val left = charts.line(kind) ?: return@mapNotNull null
        val unit = streamUnit(kind)
        val average = { series: LineSeries? -> series?.average?.let { unit.text(unit.convert(it)) } ?: "–" }
        Stat(stringResource(streamTitle(kind)), stringResource(Res.string.chart_left_right, average(left), average(left.right)))
    }

/** The battery from start to finish, and the average assist level. */
@Composable
internal fun eBikeOverview(charts: ActivityCharts): List<Stat> {
    val percent = streamUnit(StreamKind.EbikeBattery)
    val battery =
        charts
            .line(StreamKind.EbikeBattery)
            ?.points
            ?.takeIf { it.size >= 2 }
            ?.let { points ->
                Stat(
                    stringResource(Res.string.chart_ebike_battery),
                    stringResource(Res.string.chart_battery_used, percent.text(points.first().value), percent.text(points.last().value)),
                )
            }
    return listOfNotNull(battery) + averages(charts, persistentListOf(StreamKind.EbikeAssistLevel))
}
