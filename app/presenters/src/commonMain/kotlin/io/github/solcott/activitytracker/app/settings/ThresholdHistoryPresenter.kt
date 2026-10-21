package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.activityfile.TimeZones
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.mapContent
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.profile.removeThresholdEntry
import io.github.solcott.activitytracker.data.training.SportZones
import io.github.solcott.activitytracker.data.training.TrainingHistory
import io.github.solcott.activitytracker.data.training.TrainingHistory.key
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.UnitsRepository
import io.github.solcott.activitytracker.lexicon.app.Threshold
import io.github.solcott.activitytracker.lexicon.app.ThresholdEntry
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.dataresult.mapData
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@CircuitInject(ThresholdHistoryScreen::class, SessionScope::class)
@Composable
fun ThresholdHistoryPresenter(
    navigator: Navigator,
    profiles: ProfileRepository,
    units: UnitsRepository,
    clock: Clock,
): ThresholdHistoryState {
    val timeZone = remember { TimeZones.device() }
    val unitSystem = units.units.collectAsState().value
    var loadKey by remember { mutableIntStateOf(0) }
    // Retained, so coming back shows the history at once; a retry reloads under it.
    val loaded =
        produceRetainedContentState<TrainingDetails?>(null, loadKey) {
            profiles.ownProfileOutcome().map { outcome ->
                outcome.mapData { TrainingHistory.backfilled(it.details.training ?: TrainingDetails()) }
            }
        }
    // Values removed here since the last load; the next load reads the history back from the PDS without them.
    var removed by remember(loaded.data) { mutableStateOf(emptyList<ThresholdEntry>()) }
    val training = loaded.data?.let { removed.fold(it, TrainingHistory::remove) }
    val latestTraining by rememberUpdatedState(training)
    // Why removing a value failed; never a loading failure, which is [loaded]'s.
    var error by remember { mutableStateOf<UiError?>(null) }
    var removing by remember { mutableStateOf<ThresholdEntry?>(null) }
    // Group ids (a sport, or DEFAULTS) and section ids; lists, so they save in a Bundle. The defaults start expanded.
    var expanded by rememberSaveable { mutableStateOf(listOf(DEFAULTS)) }
    var showingAll by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val flights = rememberSingleFlight()

    val view = HistoryView(unitSystem, timeZone, clock.now(), expanded.toSet(), showingAll.toSet())
    val content = loaded.mapContent { _ -> training?.let { view.groups(it).toImmutableList() } ?: persistentListOf() }
    return ThresholdHistoryState(content = content, removing = removing, error = error) { event ->
        when (event) {
            ThresholdHistoryEvent.Back -> {
                navigator.pop()
            }

            is ThresholdHistoryEvent.ToggleGroup -> {
                expanded = expanded.toggled(groupId(event.sport))
            }

            ThresholdHistoryEvent.ToggleAll -> {
                // From the state now, not this composition's groups: an older event sink may send it.
                val all = latestTraining?.history.orEmpty().map { groupId(it.sport) }.distinct()
                expanded = if (expanded.any { it in all }) emptyList() else all
            }

            is ThresholdHistoryEvent.ToggleShowAll -> {
                showingAll = showingAll.toggled(sectionId(event.sport, event.threshold))
            }

            ThresholdHistoryEvent.Retry -> {
                loadKey++
            }

            is ThresholdHistoryEvent.Remove -> {
                if (training != null) {
                    flights.launch(ThresholdHistoryEvent.Remove::class) {
                        error = null
                        removing = event.entry
                        runSuspendCatching { profiles.removeThresholdEntry(event.entry) }
                            .onSuccess { removed = removed + event.entry }
                            .onFailure { error = uiErrorOf(UiError.Kind.SaveZones, it) }
                        removing = null
                    }
                }
            }
        }
    }
}

/** The id of the defaults' group; sports' are their tokens. */
private const val DEFAULTS = ""

private fun groupId(sport: String?) = sport ?: DEFAULTS

private fun sectionId(sport: String?, threshold: String) = "${groupId(sport)}/$threshold"

private fun List<String>.toggled(id: String) = if (id in this) this - id else this + id

/** The order thresholds show in: as on the zones overview. */
private val order =
    listOf(
        Threshold.THRESHOLD_SPEED,
        Threshold.THRESHOLD_HEART_RATE,
        Threshold.MAX_HEART_RATE,
        Threshold.RESTING_HEART_RATE,
        Threshold.FUNCTIONAL_THRESHOLD_POWER,
    )

/** Builds the groups as they look now: in [units] and [timeZone], charted up to [now], with [expanded] groups and [showingAll] sections. */
private class HistoryView(
    val units: UnitSystem,
    val timeZone: TimeZone,
    val now: Instant,
    val expanded: Set<String>,
    val showingAll: Set<String>,
) {
    /**
     * A group for the defaults, then each sport in saved order, then sports with history and no override left, each holding a section per
     * threshold with history. Thresholds this client doesn't know go after the known ones.
     */
    fun groups(training: TrainingDetails): List<ThresholdHistoryGroup> {
        val sportOrder = listOf(null) + training.sports.map { it.sport }
        return training.history
            .map { it.key }
            .distinct()
            .groupBy { it.sport }
            .entries
            .sortedWith(compareBy({ sportOrder.indexOf(it.key).takeIf { i -> i >= 0 } ?: sportOrder.size }, { it.key }))
            .map { (sport, keys) ->
                ThresholdHistoryGroup(
                    sport = sport,
                    expanded = groupId(sport) in expanded,
                    thresholds =
                        keys
                            .sortedWith(compareBy({ order.indexOf(it.threshold).takeIf { i -> i >= 0 } ?: order.size }, { it.threshold }))
                            .map { section(training, it) }
                            .toImmutableList(),
                )
            }
    }

    private fun section(training: TrainingDetails, key: TrainingHistory.Key): ThresholdHistorySection {
        val entries = TrainingHistory.entries(training.history, key)
        val newest = entries.first()
        // Only the newest entry can be current, and only while the threshold still has its value.
        val current = newest.takeIf { it.value == TrainingHistory.value(training, key) }
        val dated = entries.mapNotNull { entry -> entry.setAt?.let { entry to it } }.reversed()
        return ThresholdHistorySection(
            threshold = key.threshold,
            sport = key.sport,
            paceUnit = SportZones.paceUnit(key.sport, units),
            rows = entries.map { ThresholdHistoryRow(it, it.setAt?.let(::date), current = it == current) }.toImmutableList(),
            showingAll = sectionId(key.sport, key.threshold) in showingAll,
            chart = chart(dated, current),
            undatedCount = entries.size - dated.size,
            change =
                dated
                    .firstOrNull()
                    ?.takeIf { (first) -> first != newest && newest.setAt != null }
                    ?.let { (first, at) -> ThresholdTrend(first.value, newest.value, date(at)) },
        )
    }

    /** [dated] entries with their dates (oldest first) placed from the first to [now]; null with fewer than two. */
    private fun chart(dated: List<Pair<ThresholdEntry, Instant>>, current: ThresholdEntry?): ThresholdChart? {
        if (dated.size < 2) return null
        val start = dated.first().second
        val span = (now - start).inWholeMilliseconds.coerceAtLeast(1).toFloat()
        val lowest = dated.minOf { (entry) -> entry.value }
        val highest = dated.maxOf { (entry) -> entry.value }
        val range = (highest - lowest).toFloat()
        val points = dated.map { (entry, at) ->
            ChartPoint(
                x = ((at - start).inWholeMilliseconds / span).coerceIn(0f, 1f),
                y = if (range == 0f) FLAT else (entry.value - lowest) / range,
                value = entry.value,
                current = entry == current,
            )
        }
        return ThresholdChart(points.toImmutableList(), date(start), lowest, highest)
    }

    private fun date(at: Instant): HistoryDate =
        at.toLocalDateTime(timeZone).date.let { HistoryDate(it.year, it.month.ordinal + 1, it.day) }
}

/** Where a chart that never changed draws its line: midway up. */
private const val FLAT = 0.5f
