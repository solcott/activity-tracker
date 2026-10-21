package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.data.segments.SegmentStars
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import io.github.solcott.activitytracker.location.SegmentGeometry
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@CircuitInject(SegmentScreen::class, SessionScope::class)
@Composable
fun SegmentPresenter(screen: SegmentScreen, navigator: Navigator, segments: SegmentRepository, stars: SegmentStars): SegmentState {
    val uri = AtUri(screen.uri)
    var segment by remember { mutableStateOf<SegmentView?>(null) }
    var polyline by remember { mutableStateOf<String?>(null) }
    var efforts by remember { mutableStateOf<ImmutableList<EffortView>>(persistentListOf()) }
    var error by remember { mutableStateOf<UiError?>(null) }
    var starred by remember { mutableStateOf<Boolean?>(null) }
    var loadKey by remember { mutableIntStateOf(0) }
    val flights = rememberSingleFlight()

    LaunchedEffect(loadKey) {
        error = null
        runSuspendCatching { segments.segment(uri) }
            .onSuccess { loaded ->
                polyline = mapLine(loaded.record.line)
                segment = loaded
            }
            .onFailure { error = UiError.of(UiError.Kind.LoadSegment, it) }
    }
    // The viewer's own efforts are extra: the segment shows without them when they fail to load.
    LaunchedEffect(loadKey) {
        runSuspendCatching { segments.efforts(uri) }.onSuccess { efforts = it.efforts.sortedBy { e -> e.elapsed }.toImmutableList() }
    }
    // So is the star: unknown (null) hides it rather than showing it unstarred.
    LaunchedEffect(loadKey) { if (!flights.isRunning(SegmentEvent.ToggleStar)) starred = stars.isStarred(uri) }

    return SegmentState(
        segment = segment,
        polyline = polyline,
        efforts = efforts,
        isLoading = segment == null && error == null,
        error = error,
        starred = starred,
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
            SegmentEvent.OpenLeaderboard ->
                segment
                    ?.takeIf { SegmentStatus.of(it.status) == SegmentStatus.ACTIVE }
                    ?.let { navigator.goTo(SegmentLeaderboardScreen(it.uri.value, it.record.name, it.record.sport)) }
        }
    }
}

/** A segment's precision-6 [line] at precision 5, for the maps; null when it doesn't decode. */
private fun mapLine(line: String): String? = SegmentGeometry.decodePolyline(line)?.let(SegmentGeometry::toPrecision5)
