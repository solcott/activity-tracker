package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.mapContent
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.outcomeFlow
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlin.time.Duration.Companion.hours
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@CircuitInject(CombinePickerScreen::class, SessionScope::class)
@Composable
fun CombinePickerPresenter(
    screen: CombinePickerScreen,
    navigator: Navigator,
    feed: FeedRepository,
    manager: ActivityManager,
): CombinePickerState {
    var loadKey by remember { mutableIntStateOf(0) }
    // One answer from two reads: the candidates need the activity's start, and neither is any use alone. Retained, so a retry keeps them.
    val loaded =
        produceRetainedContentState<CombinePick?>(null, loadKey) {
            outcomeFlow {
                val into = feed.activity(AtUri(screen.uri))
                val start = into.record.startedAt
                CombinePick(into, candidatesFor(into, feed.ownViewsStartedBetween(start - WINDOW, start + WINDOW)).toImmutableList())
            }
        }
    // Why combining failed; never a loading failure, which is [loaded]'s.
    var error by remember { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()
    var combining by remember { mutableStateOf<AtUri?>(null) }

    val pick = CombinePickerEvent.Pick::class
    return CombinePickerState(
        content = loaded.mapContent { it?.candidates ?: persistentListOf() },
        combining = combining.takeIf { flights.isRunning(pick) },
        error = error,
    ) { event ->
        when (event) {
            CombinePickerEvent.Back -> navigator.pop()
            CombinePickerEvent.Retry -> loadKey++
            is CombinePickerEvent.Pick -> {
                val into = loaded.data?.into
                val other = loaded.data?.candidates?.firstOrNull { it.uri == event.uri }
                if (into != null && other != null) {
                    flights.launch(pick) {
                        error = null
                        combining = other.uri
                        runSuspendCatching { manager.combine(into, other) }
                            .onSuccess { combined ->
                                // Back past this activity's page, which now shows a member, to the combined activity's.
                                navigator.pop()
                                navigator.pop()
                                navigator.goTo(ActivityDetailScreen(combined.value))
                            }
                            .onFailure { error = UiError.of(UiError.Kind.CombineActivity, it) }
                    }
                }
            }
        }
    }
}

/** The activity the pick combines [into], and what it can be combined with. */
@Immutable private data class CombinePick(val into: ActivityView, val candidates: ImmutableList<ActivityView>)

/**
 * What [into] can be combined with among [nearby]: the athlete's other activities that are neither combined activities nor members of one,
 * nor already [into]'s members, oldest first.
 */
internal fun candidatesFor(into: ActivityView, nearby: List<ActivityView>): List<ActivityView> {
    val members = into.record.combined?.members.orEmpty().toSet()
    return nearby
        .filter { it.uri != into.uri && it.uri !in members && it.record.combined == null && it.combinedInto == null }
        .sortedBy { it.record.startedAt }
}

/** How far before or after the activity's start another may start to be offered. */
private val WINDOW = 12.hours
