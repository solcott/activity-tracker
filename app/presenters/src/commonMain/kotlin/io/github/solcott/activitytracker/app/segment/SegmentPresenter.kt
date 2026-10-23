package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.data.segments.SegmentStars
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.github.solcott.activitytracker.lexicon.app.MayorPeriod
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import io.github.solcott.dataresult.combineOutcomes
import io.github.solcott.dataresult.dataOrNull
import io.github.solcott.dataresult.mapData
import io.github.solcott.uistate.circuit.produceRetainedContentStates
import io.github.solcott.uistate.contentStatesOf
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map

@CircuitInject(SegmentScreen::class, SessionScope::class)
@Composable
fun SegmentPresenter(screen: SegmentScreen, navigator: Navigator, segments: SegmentRepository, stars: SegmentStars): SegmentState {
    val uri = AtUri(screen.uri)
    var starred by remember { mutableStateOf<Boolean?>(null) }
    var loadKey by remember { mutableIntStateOf(0) }
    val flights = rememberSingleFlight()

    // Retained, so coming back from an effort's activity shows the segment at once; a retry restarts both reads under it.
    val (content, ownEfforts) =
        produceRetainedContentStates(contentStatesOf<SegmentView?, ImmutableList<EffortView>>(null, persistentListOf()), loadKey) {
            combineOutcomes(
                segments.segmentOutcome(uri),
                segments.ownEffortsOutcome(uri).map { outcome ->
                    outcome.mapData { efforts -> efforts.sortedBy { it.elapsed }.toImmutableList() }
                },
            )
        }
    val segment = content.data
    val polyline = remember(segment) { segment?.let { mapLine(it.record.line) } }
    // The star is extra: unknown (null) hides it rather than showing it unstarred.
    LaunchedEffect(loadKey) { if (!flights.isRunning(SegmentEvent.ToggleStar)) starred = stars.isStarred(uri) }
    val active = segment?.let { SegmentStatus.of(it.status) == SegmentStatus.ACTIVE } == true
    // The Mayor is extra too: only an active segment has one, and a failed read leaves the last one shown, or none.
    var mayor by rememberRetained { mutableStateOf<MayorSummary?>(null) }
    LaunchedEffect(active, loadKey) {
        if (!active) {
            mayor = null
            return@LaunchedEffect
        }
        segments.mayorsPage(uri, MayorPeriod.LAST_90_DAYS, LeaderboardScope.EVERYONE, screen.board, limit = 1).dataOrNull()?.let {
            mayor = MayorSummary(it.items.firstOrNull(), it.meta.viewer)
        }
    }

    return SegmentState(
        content = content,
        polyline = polyline,
        // The viewer's own efforts are extra too: the segment shows without them, and no error, when they fail to load.
        efforts = ownEfforts.data,
        starred = starred,
        credit = segment?.record?.let(::creditOf),
        mayor = mayor,
    ) { event ->
        when (event) {
            SegmentEvent.Back -> navigator.pop()
            SegmentEvent.Retry -> loadKey++
            SegmentEvent.ToggleStar -> {
                val loaded = segment
                val was = starred
                if (loaded != null && was != null) {
                    flights.launch(SegmentEvent.ToggleStar) {
                        // Shown at once; the write is queued, so only a refusal (a lost grant) takes it back.
                        starred = !was
                        runSuspendCatching { stars.setStarred(StrongRef(loaded.uri, loaded.cid), starred = !was) }
                            .onFailure { starred = was }
                    }
                }
            }
            is SegmentEvent.OpenActivity -> navigator.goTo(ActivityDetailScreen(event.uri.value))
            SegmentEvent.OpenOriginal -> segment?.duplicateOf?.let { navigator.goTo(SegmentScreen(it.value)) }
            SegmentEvent.OpenMap -> navigator.goTo(SegmentMapScreen(screen.uri))
            SegmentEvent.OpenLeaderboard ->
                segment
                    ?.takeIf { SegmentStatus.of(it.status) == SegmentStatus.ACTIVE }
                    ?.let { navigator.goTo(SegmentLeaderboardScreen(it.uri.value, it.record.name, screen.board)) }
            SegmentEvent.OpenMayors ->
                segment?.takeIf { active }?.let { navigator.goTo(SegmentMayorsScreen(it.uri.value, it.record.name, screen.board)) }
        }
    }
}
