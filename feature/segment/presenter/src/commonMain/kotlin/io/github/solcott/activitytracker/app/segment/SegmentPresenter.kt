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
import io.github.solcott.activitytracker.app.mapContent
import io.github.solcott.activitytracker.app.names.rememberPlaceNames
import io.github.solcott.activitytracker.app.names.sharedPlaceOf
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.SegmentNames
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.data.segments.SegmentStars
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.github.solcott.activitytracker.lexicon.app.MayorPeriod
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import io.github.solcott.dataresult.combineOutcomes
import io.github.solcott.dataresult.dataOrNull
import io.github.solcott.dataresult.mapData
import io.github.solcott.uistate.circuit.produceRetainedContentStates
import io.github.solcott.uistate.contentStatesOf
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@CircuitInject(SegmentScreen::class, SessionScope::class)
@Composable
@Suppress("LongMethod") // An event per action on the segment and its board.
fun SegmentPresenter(
    screen: SegmentScreen,
    navigator: Navigator,
    segments: SegmentRepository,
    stars: SegmentStars,
    names: SegmentNames,
): SegmentState {
    // The segment shown: the screen's, or another of its route chosen on the page (segments/segment-routes), swapped in place so the page
    // keeps its place and back still leaves it.
    var shown by rememberRetained { mutableStateOf(screen.uri) }
    val uri = AtUri(shown)
    var starred by remember { mutableStateOf<Boolean?>(null) }
    var loadKey by remember { mutableIntStateOf(0) }
    val flights = rememberSingleFlight()

    // Retained, so coming back from an effort's activity shows the segment at once; a retry restarts both reads under it.
    val (detail, ownEfforts) =
        produceRetainedContentStates(
            contentStatesOf<GetSegment.Output?, ImmutableList<EffortView>>(null, persistentListOf()),
            shown,
            loadKey,
        ) {
            combineOutcomes(
                // The viewer's own proposals, edits and votes show at once, laid over each read until the AppView catches up.
                combine(segments.segmentDetailOutcome(uri), names.changes) { outcome, _ ->
                    outcome.mapData { it.copy(segment = names.applyTo(it.segment)) }
                },
                segments.ownEffortsOutcome(uri).map { outcome ->
                    outcome.mapData { efforts -> efforts.sortedBy { it.elapsed }.toImmutableList() }
                },
            )
        }
    // The segment and its profile come from one read; the screen's content is the segment.
    val content = detail.mapContent { it?.segment }
    val profile = remember(detail.data) { detail.data?.let(::segmentProfileOf) }
    val segment = content.data
    val polyline = remember(segment) { segment?.let { mapLine(it.record.line) } }
    val route = remember(segment, detail.data?.routeMembers) { segment?.let { RouteSwitcher.of(it, detail.data?.routeMembers.orEmpty()) } }
    // The star is extra: unknown (null) hides it rather than showing it unstarred.
    LaunchedEffect(shown, loadKey) { if (!flights.isRunning(SegmentEvent.ToggleStar)) starred = stars.isStarred(uri) }
    // A lift has no boards (segments/segment-lifts), so it's never treated as one that could.
    val active = segment?.let { SegmentStatus.of(it.status) == SegmentStatus.ACTIVE && SegmentKind.isRanked(it.record.kind) } == true
    // The Mayor is extra too: only an active run has one, and a failed read leaves the last one shown, or none.
    var mayor by rememberRetained { mutableStateOf<MayorSummary?>(null) }
    LaunchedEffect(active, shown, loadKey) {
        if (!active) {
            mayor = null
            return@LaunchedEffect
        }
        segments.mayorsPage(uri, MayorPeriod.LAST_90_DAYS, LeaderboardScope.EVERYONE, screen.board, limit = 1).dataOrNull()?.let {
            mayor = MayorSummary(it.items.firstOrNull(), it.meta.viewer)
        }
    }

    val placeNames =
        rememberPlaceNames(
            place = uri,
            currentName = segment?.currentName.orEmpty(),
            proposals = remember(segment?.nameProposals) { segment?.nameProposals.orEmpty().toImmutableList() },
            canPropose = segment?.viewer?.canProposeName == true,
            canVote = segment?.viewer?.canVoteOnNames == true,
            sharedPlace = segment?.let(::sharedPlaceOf),
            names = names,
        )

    return SegmentState(
        content = content,
        polyline = polyline,
        // The viewer's own efforts are extra too: the segment shows without them, and no error, when they fail to load.
        efforts = ownEfforts.data,
        starred = starred,
        credit = segment?.record?.let(::creditOf),
        mayor = mayor,
        names = placeNames,
        profile = profile,
        route = route,
        shown = uri,
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
            SegmentEvent.OpenMap -> navigator.goTo(SegmentMapScreen(shown))
            SegmentEvent.OpenLeaderboard ->
                segment
                    ?.takeIf { SegmentStatus.of(it.status) == SegmentStatus.ACTIVE }
                    ?.let { navigator.goTo(SegmentLeaderboardScreen(it.uri.value, it.currentName, screen.board)) }
            SegmentEvent.OpenMayors ->
                segment?.takeIf { active }?.let { navigator.goTo(SegmentMayorsScreen(it.uri.value, it.currentName, screen.board)) }
            // Shown in place: the page keeps its scroll and bar while the chosen segment loads over the last one (ui/content-loading).
            is SegmentEvent.SwitchRoute -> shown = shownAfter(route, event, shown)
        }
    }
}

/** The segment to show after [switch]: the one it names when it is in [route], else still [shown]. */
private fun shownAfter(route: RouteSwitcher?, switch: SegmentEvent.SwitchRoute, shown: String): String =
    route?.offering(switch.uri)?.value ?: shown
