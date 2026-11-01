package io.github.solcott.activitytracker.app.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.activitylist.ActivityListEvent
import io.github.solcott.activitytracker.app.activitylist.interactionErrorOf
import io.github.solcott.activitytracker.app.activitylist.rememberActivityListEventSink
import io.github.solcott.activitytracker.app.moderation.rememberRevealed
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.uistate.circuit.rememberRetainedPaging
import kotlinx.coroutines.flow.flow

@CircuitInject(ImportBatchScreen::class, SessionScope::class)
@Composable
fun ImportBatchPresenter(
    screen: ImportBatchScreen,
    navigator: Navigator,
    feed: FeedRepository,
    activities: ActivityRepository,
    profiles: ProfileRepository,
): ImportBatchState {
    val page =
        rememberRetainedPaging(screen, idOf = { activity: ActivityView -> activity.uri }) { cursor ->
            flow { emit(feed.importBatchPage(screen.actor, screen.batch, cursor)) }
        }
    var error by rememberRetained { mutableStateOf<UiError?>(null) }
    val activityEvents = rememberActivityListEventSink(navigator, activities) { error = it }
    val flights = rememberSingleFlight()
    return ImportBatchState(screen.name, page.state, profiles.did.value, rememberRevealed(), error) { event ->
        when (event) {
            ImportBatchEvent.Back -> {
                navigator.pop()
            }

            ImportBatchEvent.LoadMore -> {
                page.loadMore()
            }

            ImportBatchEvent.Retry -> {
                page.retry()
            }

            is ImportBatchEvent.Activity -> {
                val like = event.event as? ActivityListEvent.ToggleLike
                if (like == null) {
                    activityEvents(event.event)
                } else {
                    // The pages aren't cached, so the like is laid over the listed activity here once it's queued.
                    flights.launch(like.activity.uri) {
                        runSuspendCatching { activities.toggleLike(like.activity) }
                            .onSuccess {
                                page.update { list -> list.map { if (it.uri == like.activity.uri) feed.withLocalChanges(it) else it } }
                            }
                            .onFailure { interactionErrorOf(it)?.let { e -> error = e } }
                    }
                }
            }
        }
    }
}
