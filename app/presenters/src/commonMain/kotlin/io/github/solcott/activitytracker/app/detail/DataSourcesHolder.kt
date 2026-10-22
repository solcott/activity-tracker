package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.slack.circuit.retained.rememberRetained
import io.github.solcott.activitytracker.app.SingleFlight
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.ChartAttribution
import io.github.solcott.activitytracker.data.activity.CombinedSources
import io.github.solcott.activitytracker.data.activity.DataKind
import io.github.solcott.activitytracker.data.activity.SourceChoice
import io.github.solcott.activitytracker.data.outcomeFlow
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CombinedChannel
import io.github.solcott.activitytracker.lexicon.app.StreamChannel
import io.github.solcott.uistate.circuit.produceRetainedContentState
import io.github.solcott.uistate.errorOrNull
import io.github.solcott.uistate.hasLoaded
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull

/** The owner's choice of where each kind of a combined activity's data comes from: [state] is null on anything else. */
internal class DataSourcesHolder(val state: DataSources?, private val onEvent: (ActivityDetailEvent) -> Unit) {
    fun on(event: ActivityDetailEvent) = onEvent(event)
}

/**
 * Where each kind of the owner's combined [activity]'s data comes from (null for anything else), read from its members as stored, reloaded
 * with [loadKey] and retained with the page. A read that fails before any answered says so; one that fails after keeps what was shown,
 * quietly. A choice rebuilds it ([ActivityManager.setSources]) one at a time, then [reload]s the page for its new figures and charts.
 */
@Composable
internal fun rememberDataSources(
    activity: ActivityView?,
    loadKey: Int,
    manager: ActivityManager,
    flights: SingleFlight,
    reload: () -> Unit,
): DataSourcesHolder {
    val combined = activity?.takeIf { it.record.combined != null }
    val latest by rememberUpdatedState(combined)
    // Retained with the page: a read is keyed on it.
    var readKey by rememberRetained { mutableIntStateOf(0) }
    val read = remember {
        snapshotFlow { latest }.filterNotNull().distinctUntilChangedBy { it.uri }
    }
        .produceRetainedContentState<ActivityView, CombinedSources?>(null, loadKey, readKey) { own -> outcomeFlow { manager.sources(own) } }
    // A rebuild's sources, shown at once and until the next read answers.
    var saved by remember { mutableStateOf<Saved?>(null) }
    val sources = saved?.takeIf { it.over === read.data }?.sources ?: read.data
    var saveError by remember { mutableStateOf<UiError?>(null) }
    val error = saveError ?: read.errorOrNull?.takeIf { !read.hasLoaded }?.let { UiError(UiError.Kind.LoadDataSources, it) }
    // The choice that failed, for a retry to repeat.
    var failed by remember { mutableStateOf<ActivityDetailEvent?>(null) }
    var pending by remember { mutableStateOf<DataKind?>(null) }
    combined ?: return DataSourcesHolder(null) {}

    fun choose(event: ActivityDetailEvent, kind: DataKind, choice: SourceChoice) {
        flights.launch(SOURCES) {
            pending = kind
            saveError = null
            failed = null
            val over = read.data
            runSuspendCatching { manager.setSources(combined, kind, choice) }
                .onSuccess {
                    saved = Saved(over, it)
                    reload()
                }
                .onFailure {
                    failed = event
                    saveError = UiError.of(UiError.Kind.SaveDataSource, it)
                }
        }
    }
    fun on(event: ActivityDetailEvent) {
        when (event) {
            is ActivityDetailEvent.ChooseSource -> choose(event, event.kind, SourceChoice.Member(event.member))
            is ActivityDetailEvent.SetIncluded ->
                choose(event, event.kind, if (event.included) SourceChoice.Default else SourceChoice.Exclude)
            ActivityDetailEvent.RetrySources -> failed?.let(::on) ?: run { readKey++ }
            else -> Unit
        }
    }
    return DataSourcesHolder(sourcesState(sources, error, pending.takeIf { flights.isRunning(SOURCES) }), ::on)
}

private fun sourcesState(sources: CombinedSources?, error: UiError?, saving: DataKind?): DataSources {
    val byUri = sources?.members.orEmpty().associateBy { it.uri }
    val rows =
        sources?.kinds.orEmpty().map { kind ->
            DataSourceRow(
                kind = kind.kind,
                included = !kind.excluded,
                chosen = kind.chosen?.let { byUri[it] },
                offered = kind.members.takeIf { it.size >= 2 }?.mapNotNull { byUri[it] }.orEmpty().toImmutableList(),
            )
        }
    return DataSources(
        kinds = rows.toImmutableList(),
        isLoading = sources == null && error == null,
        saving = saving,
        error = error,
    )
}

/** The [sources] a rebuild saved, over the read it answered: a later read's answer replaces them. */
private class Saved(val over: CombinedSources?, val sources: CombinedSources)

/** One rebuild at a time, whichever kind it's for. */
private object SOURCES

/**
 * On a combined activity, the members each of [sections]' cards came from, in time order ([ChartAttribution]): every viewer is served the
 * ranking and the members' spans, so every viewer who sees a card sees the same caption. Empty for anything else.
 */
internal fun chartSources(activity: ActivityView?, sections: List<DetailSection>): ImmutableList<ChartSource> {
    val combined = activity?.record?.combined ?: return persistentListOf()
    val members = activity.members.orEmpty()
    val byUri = members.associateBy { it.uri }
    return sections
        .mapNotNull { section ->
            SECTION_CHANNELS[section].orEmpty()
                .asSequence()
                .map { ChartAttribution.membersOf(it, combined, members) }
                .firstOrNull { it.isNotEmpty() }
                ?.let { uris -> ChartSource(section, uris.mapNotNull { byUri[it] }.toImmutableList()) }
                ?.takeIf { it.members.isNotEmpty() }
        }
        .toImmutableList()
}

/** The channels whose source each card names, the first one ranked counting. */
private val SECTION_CHANNELS: Map<DetailSection, List<String>> =
    mapOf(
        DetailSection.HeartRate to listOf(StreamChannel.HEART_RATE),
        DetailSection.Power to listOf(StreamChannel.POWER),
        DetailSection.CyclingDynamics to
            listOf(StreamChannel.TORQUE_EFFECTIVENESS_LEFT, StreamChannel.PEDAL_SMOOTHNESS_LEFT, StreamChannel.PCO_LEFT),
        DetailSection.Speed to listOf(StreamChannel.SPEED, StreamChannel.DISTANCE),
        DetailSection.Cadence to listOf(StreamChannel.CADENCE),
        DetailSection.Elevation to listOf(StreamChannel.ALTITUDE),
        DetailSection.Temperature to listOf(StreamChannel.TEMPERATURE),
        DetailSection.Physiology to listOf(StreamChannel.CORE_TEMPERATURE, StreamChannel.SM_O2, StreamChannel.TOTAL_HEMOGLOBIN),
        DetailSection.Depth to listOf(StreamChannel.DEPTH),
        DetailSection.Oxygen to listOf(StreamChannel.PP_O2, StreamChannel.CNS),
        DetailSection.TankPressure to listOf(CombinedChannel.DIVE),
        DetailSection.Respiration to listOf(StreamChannel.RESPIRATION_RATE),
        DetailSection.RunningDynamics to
            listOf(
                StreamChannel.VERTICAL_OSCILLATION,
                StreamChannel.STANCE_TIME,
                StreamChannel.STEP_LENGTH,
                StreamChannel.VERTICAL_RATIO,
                StreamChannel.STANCE_TIME_BALANCE,
                StreamChannel.FORM_POWER,
                StreamChannel.LEG_SPRING_STIFFNESS,
            ),
        DetailSection.EBike to listOf(StreamChannel.EBIKE_BATTERY, StreamChannel.EBIKE_ASSIST_MODE, StreamChannel.EBIKE_ASSIST_LEVEL),
        DetailSection.Gears to listOf(StreamChannel.GEARS),
        DetailSection.Legs to listOf(CombinedChannel.LEGS),
        DetailSection.Lengths to listOf(CombinedChannel.LENGTHS),
        DetailSection.Sets to listOf(CombinedChannel.SETS),
        DetailSection.Laps to listOf(CombinedChannel.LAPS),
    )
