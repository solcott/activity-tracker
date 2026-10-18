package io.github.solcott.activitytracker.app.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.media.mediaItems
import io.github.solcott.activitytracker.app.ui.ActivityMap
import io.github.solcott.activitytracker.app.ui.ActivityTypeIcon
import io.github.solcott.activitytracker.app.ui.CategoryLocks
import io.github.solcott.activitytracker.app.ui.DiveSite
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.MediaRow
import io.github.solcott.activitytracker.app.ui.Stat
import io.github.solcott.activitytracker.app.ui.VisibilityBadge
import io.github.solcott.activitytracker.app.ui.activityTypeLabel
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.distanceStat
import io.github.solcott.activitytracker.app.ui.diveOverviewStats
import io.github.solcott.activitytracker.app.ui.elevationStat
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.handleLabel
import io.github.solcott.activitytracker.app.ui.rememberFileHandOff
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_retry
import io.github.solcott.activitytracker.composeApp.resources.detail_hide_likes
import io.github.solcott.activitytracker.composeApp.resources.detail_like
import io.github.solcott.activitytracker.composeApp.resources.detail_liked
import io.github.solcott.activitytracker.composeApp.resources.detail_no_likes
import io.github.solcott.activitytracker.composeApp.resources.detail_show_likes
import io.github.solcott.activitytracker.composeApp.resources.detail_title
import io.github.solcott.activitytracker.composeApp.resources.detail_zone_added
import io.github.solcott.activitytracker.composeApp.resources.like_count
import io.github.solcott.activitytracker.composeApp.resources.shifting_front_rear
import io.github.solcott.activitytracker.composeApp.resources.stat_aerobic_effect
import io.github.solcott.activitytracker.composeApp.resources.stat_anaerobic_effect
import io.github.solcott.activitytracker.composeApp.resources.stat_avg_cadence
import io.github.solcott.activitytracker.composeApp.resources.stat_avg_power
import io.github.solcott.activitytracker.composeApp.resources.stat_avg_stroke_distance
import io.github.solcott.activitytracker.composeApp.resources.stat_calories
import io.github.solcott.activitytracker.composeApp.resources.stat_descent
import io.github.solcott.activitytracker.composeApp.resources.stat_flow
import io.github.solcott.activitytracker.composeApp.resources.stat_ftp
import io.github.solcott.activitytracker.composeApp.resources.stat_grit
import io.github.solcott.activitytracker.composeApp.resources.stat_hrv
import io.github.solcott.activitytracker.composeApp.resources.stat_intensity_factor
import io.github.solcott.activitytracker.composeApp.resources.stat_jumps
import io.github.solcott.activitytracker.composeApp.resources.stat_max_cadence
import io.github.solcott.activitytracker.composeApp.resources.stat_max_power
import io.github.solcott.activitytracker.composeApp.resources.stat_moving_time
import io.github.solcott.activitytracker.composeApp.resources.stat_normalized_power
import io.github.solcott.activitytracker.composeApp.resources.stat_pool_length
import io.github.solcott.activitytracker.composeApp.resources.stat_pushes
import io.github.solcott.activitytracker.composeApp.resources.stat_runs
import io.github.solcott.activitytracker.composeApp.resources.stat_shifts
import io.github.solcott.activitytracker.composeApp.resources.stat_steps
import io.github.solcott.activitytracker.composeApp.resources.stat_strokes
import io.github.solcott.activitytracker.composeApp.resources.stat_temperature
import io.github.solcott.activitytracker.composeApp.resources.stat_tss
import io.github.solcott.activitytracker.composeApp.resources.stat_work
import io.github.solcott.activitytracker.composeApp.resources.unit_kcal
import io.github.solcott.activitytracker.composeApp.resources.unit_kj
import io.github.solcott.activitytracker.composeApp.resources.unit_ms
import io.github.solcott.activitytracker.composeApp.resources.unit_pool_m
import io.github.solcott.activitytracker.composeApp.resources.unit_pool_yd
import io.github.solcott.activitytracker.composeApp.resources.unit_rpm
import io.github.solcott.activitytracker.composeApp.resources.unit_watts
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.formatDecimal
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.CycleCount
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.activityType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@CircuitInject(ActivityDetailScreen::class, SessionScope::class)
@Composable
fun ActivityDetailUi(state: ActivityDetailState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.detail_title),
                onBack = { state.eventSink(ActivityDetailEvent.Back) },
                scrollBehavior = scrollBehavior,
                actions = {
                    (state as? ActivityDetailState.Loaded)?.manage?.let {
                        ManageMenu(it, state.canHideRouteEnds, state.export, state.eventSink)
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            is ActivityDetailState.Loading -> {
                Column(
                    Modifier.padding(padding).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppProgressIndicator(Modifier.padding(32.dp))
                }
            }

            is ActivityDetailState.Error -> {
                Column(Modifier.padding(padding).padding(16.dp)) {
                    Text(errorText(state.error), color = MaterialTheme.colorScheme.error)
                    AppTextButton(onClick = { state.eventSink(ActivityDetailEvent.Retry) }) {
                        Text(stringResource(Res.string.action_retry))
                    }
                }
            }

            is ActivityDetailState.Loaded -> {
                LoadedContent(state, padding)
            }
        }
    }
}

@Composable
private fun LoadedContent(state: ActivityDetailState.Loaded, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val handOff = rememberFileHandOff()
    val exported = state.export?.file
    SideEffect(exported) {
        if (exported != null) {
            handOff(exported)
            state.eventSink(ActivityDetailEvent.ExportHandedOff)
        }
    }
    val listState = rememberLazyListState()
    ReadableList(listState, modifier.fillMaxSize(), contentPadding) { listPadding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = listPadding + PaddingValues(vertical = CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
        ) {
            item(key = "overview") { Overview(state, Modifier.animateContentSize()) }
            if (state.sections.isNotEmpty()) {
                item(key = "sections") { SectionsHeader(state) }
                items(state.sections, key = { "section/${it.name}" }) { section ->
                    SectionCard(section, state, Modifier.animateContentSize())
                }
            }
            comments(state)
        }
    }
    LoadMoreNearTheEnd(listState, state)
}

/** What always shows: who and what, the headline figures, the map, and likes. */
@Composable
private fun Overview(state: ActivityDetailState.Loaded, modifier: Modifier = Modifier) {
    val record = state.activity.record
    DetailCard(modifier) {
        Column(Modifier.padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(CARD_PADDING)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.activity.author.displayName ?: state.activity.author.handle.value,
                        Modifier.clickable(role = Role.Button) { state.eventSink(ActivityDetailEvent.OpenAuthor) },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    VisibilityBadge(state.activity.visibilityOrPublic)
                }
                Text(record.title ?: activityTypeLabel(record.activityType), style = MaterialTheme.typography.headlineSmall)
                if (record.title != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ActivityTypeIcon(record.activityType, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            activityTypeLabel(record.activityType),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                record.dive?.site?.let { DiveSite(it, Modifier.padding(top = 2.dp)) }
                record.description?.let { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium) }
            }
            StatTiles(overviewStats(record, state.charts, state.poolLength))
            CategoryLocks(state.narrowerCategories)
            val media = remember(state.activity) { state.activity.mediaItems() }
            MediaRow(
                media,
                onOpen = { state.eventSink(ActivityDetailEvent.OpenMedia(it)) },
                height = 240.dp,
                // The owner sees their whole route; everyone else the polyline trimmed by the owner's privacy zones.
                map =
                    (state.ownRoute ?: record.polyline)?.let { polyline ->
                        { mapModifier, mapHeight -> ActivityMap(polyline, mapModifier, mapHeight) }
                    },
            )
            Recordings(state.activity, onOpen = { state.eventSink(ActivityDetailEvent.OpenActivity(it)) })
            state.manage?.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
            state.routeError?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
            if (state.zoneAdded) Text(stringResource(Res.string.detail_zone_added), style = MaterialTheme.typography.bodySmall)
            Likes(state)
        }
    }
}

@Composable
private fun overviewStats(record: ActivityRecord, charts: ActivityCharts?, poolLength: PoolLength?): ImmutableList<Stat> {
    if (record.sport == Sport.DIVE) {
        val depth = charts?.lines?.firstOrNull { it.kind == StreamKind.Depth }
        return diveOverviewStats(record, depth?.max, depth?.average)
    }
    val time = record.movingTime ?: record.elapsedTime
    return listOfNotNull(
            distanceStat(record.sport, record.distance?.toDouble()),
            Stat(stringResource(Res.string.stat_moving_time), Format.duration(time)),
            elevationStat(record.sport, record.elevationGain?.toDouble()),
            Format.rateStat(record.sport, record.distance?.toDouble(), time),
            record.avgTemperature?.let { Stat(stringResource(Res.string.stat_temperature), Format.temperature(it)) },
            poolLength?.let {
                Stat(
                    stringResource(Res.string.stat_pool_length),
                    stringResource(if (it.yards) Res.string.unit_pool_yd else Res.string.unit_pool_m, it.value),
                )
            },
            charts?.skiRuns?.let { Stat(stringResource(Res.string.stat_runs), "$it") },
        )
        .plus(fileStats(record))
        .plus(countStats(record))
        .plus(trainingLoadStats(charts?.trainingLoad))
        .toImmutableList()
}

/** What a FIT file adds to the summary: power, cadence, calories, descent, shifts and trail dynamics, each only where stored. */
@Composable
private fun fileStats(record: ActivityRecord): List<Stat> {
    val watts = stringResource(Res.string.unit_watts, "%s")
    return listOfNotNull(
        record.avgPower?.let { Stat(stringResource(Res.string.stat_avg_power), watts.replace("%s", "$it")) },
        record.normalizedPower?.let { Stat(stringResource(Res.string.stat_normalized_power), watts.replace("%s", "$it")) },
        record.maxPower?.let { Stat(stringResource(Res.string.stat_max_power), watts.replace("%s", "$it")) },
        record.work?.let { Stat(stringResource(Res.string.stat_work), stringResource(Res.string.unit_kj, it)) },
        record.avgCadence?.let { Stat(stringResource(Res.string.stat_avg_cadence), stringResource(Res.string.unit_rpm, it)) },
        record.maxCadence?.let { Stat(stringResource(Res.string.stat_max_cadence), stringResource(Res.string.unit_rpm, it)) },
        record.calories?.let { Stat(stringResource(Res.string.stat_calories), stringResource(Res.string.unit_kcal, it)) },
        record.elevationLoss?.let { Stat(stringResource(Res.string.stat_descent), Format.elevation(it.toDouble())) },
        record.rearShifts?.let { rear ->
            Stat(stringResource(Res.string.stat_shifts), stringResource(Res.string.shifting_front_rear, record.frontShifts ?: 0, rear))
        },
        record.jumps?.let { Stat(stringResource(Res.string.stat_jumps), "$it") },
        record.grit?.let { Stat(stringResource(Res.string.stat_grit), formatDecimal(it / TENTHS, 1)) },
        record.flow?.let { Stat(stringResource(Res.string.stat_flow), formatDecimal(it / TENTHS, 1)) },
    )
}

/** FTP, IF, TSS, training effect and HRV: only for a viewer the activity's zones record admits, as the AppView serves them. */
@Composable
private fun trainingLoadStats(load: TrainingLoadStats?): List<Stat> {
    load ?: return emptyList()
    return listOfNotNull(
        load.ftp?.let { Stat(stringResource(Res.string.stat_ftp), stringResource(Res.string.unit_watts, it)) },
        load.intensityFactor?.let { Stat(stringResource(Res.string.stat_intensity_factor), formatDecimal(it, 2)) },
        load.trainingStressScore?.let { Stat(stringResource(Res.string.stat_tss), formatDecimal(it, 0)) },
        load.aerobicTrainingEffect?.let { Stat(stringResource(Res.string.stat_aerobic_effect), formatDecimal(it, 1)) },
        load.anaerobicTrainingEffect?.let { Stat(stringResource(Res.string.stat_anaerobic_effect), formatDecimal(it, 1)) },
        load.hrvRmssd?.let { Stat(stringResource(Res.string.stat_hrv), stringResource(Res.string.unit_ms, it)) },
    )
}

private const val TENTHS = 10.0
private const val CENTIMETERS_PER_METER = 100.0

/**
 * The activity's cycle count, named for its sport (steps, strokes or pushes; none for a sport whose count isn't shown), and its average
 * stroke distance.
 */
@Composable
private fun countStats(record: ActivityRecord): List<Stat> {
    val label =
        when (CycleCount.unitOf(record.sport)) {
            CycleCount.STEPS -> Res.string.stat_steps
            CycleCount.STROKES -> Res.string.stat_strokes
            CycleCount.PUSHES -> Res.string.stat_pushes
            else -> null
        }
    return listOfNotNull(
        record.cycles?.let { cycles -> label?.let { Stat(stringResource(it), "$cycles") } },
        record.avgStrokeDistance
            ?.takeIf { it > 0 }
            ?.let { Stat(stringResource(Res.string.stat_avg_stroke_distance), Format.depth(it / CENTIMETERS_PER_METER)) },
    )
}

/** The like count or button, the toggle for who liked it, why a like couldn't be given, and the list once shown. */
@Composable
private fun Likes(state: ActivityDetailState.Loaded, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LikeButton(state, Modifier.weight(1f))
            // Hiding stays available if the last like goes away while the list is open.
            if (state.activity.likeCount > 0 || state.likesList != null) {
                AppTextButton(onClick = { state.eventSink(ActivityDetailEvent.ToggleLikesList) }) {
                    Text(stringResource(if (state.likesList != null) Res.string.detail_hide_likes else Res.string.detail_show_likes))
                }
            }
        }
        state.likeError?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
        state.likesList?.let { LikesList(it, state.eventSink) }
    }
}

@Composable
private fun LikeButton(state: ActivityDetailState.Loaded, modifier: Modifier = Modifier) {
    val count = state.activity.likeCount
    if (state.isOwn) {
        Text(pluralStringResource(Res.plurals.like_count, count.toInt(), count), modifier.padding(vertical = 12.dp))
    } else {
        Box(modifier) {
            AppTextButton(onClick = { state.eventSink(ActivityDetailEvent.ToggleLike) }) {
                Text(
                    stringResource(
                        if (state.activity.viewer?.like != null) Res.string.detail_liked else Res.string.detail_like,
                        count,
                    )
                )
            }
        }
    }
}

/** Who liked it, under the like row: a spinner, an error, the empty state, or one tappable row per athlete. */
@Composable
private fun LikesList(list: LikesList, eventSink: (ActivityDetailEvent) -> Unit, modifier: Modifier = Modifier) {
    val error = list.error
    Column(modifier.fillMaxWidth()) {
        when {
            list.isLoading -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { AppProgressIndicator() }
            error != null -> Text(errorText(error), color = MaterialTheme.colorScheme.error)
            list.athletes.isEmpty() -> Text(stringResource(Res.string.detail_no_likes), style = MaterialTheme.typography.bodyMedium)
            else -> {
                list.athletes.forEach { athlete ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { eventSink(ActivityDetailEvent.OpenProfile(athlete.did)) }
                            .padding(vertical = 6.dp)
                    ) {
                        Text(athlete.displayName ?: athlete.handle.value, style = MaterialTheme.typography.titleSmall)
                        Text(handleLabel(athlete.handle.value), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
