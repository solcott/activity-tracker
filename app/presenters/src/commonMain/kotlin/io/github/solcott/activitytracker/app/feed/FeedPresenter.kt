package io.github.solcott.activitytracker.app.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.app.record.ManualActivityScreen
import io.github.solcott.activitytracker.app.record.RecordScreen
import io.github.solcott.activitytracker.app.search.AthleteSearchScreen
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.location.LocationTracker
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@CircuitInject(FeedScreen::class, SessionScope::class)
@Composable
fun FeedPresenter(
    navigator: Navigator,
    feed: FeedRepository,
    activities: ActivityRepository,
    profiles: ProfileRepository,
    locationTracker: LocationTracker,
): FeedState {
    val items by remember(feed) { feed.timeline().map { it.toImmutableList() } }.collectAsState(persistentListOf())
    val pending by activities.pendingWrites.collectAsState(0)
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiError?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) {
        refreshing = true
        error =
            runSuspendCatching {
                activities.sync()
                feed.refreshTimeline()
            }
                .exceptionOrNull()
                ?.let { UiError.of(UiError.Kind.LoadFeed, it) }
        refreshing = false
    }

    return FeedState(items, refreshing, error, pending, profiles.did.value, locationTracker.isSupported) { event ->
        when (event) {
            FeedEvent.Refresh -> {
                refreshKey++
            }

            is FeedEvent.Open -> {
                navigator.goTo(ActivityDetailScreen(event.activity.uri.value))
            }

            is FeedEvent.OpenAuthor -> {
                navigator.goTo(ProfileScreen(event.activity.author.did.value))
            }

            is FeedEvent.ToggleKudos -> {
                scope.launch { runSuspendCatching { activities.toggleKudos(event.activity) } }
            }

            FeedEvent.Record -> {
                navigator.goTo(RecordScreen)
            }

            FeedEvent.AddManual -> {
                navigator.goTo(ManualActivityScreen)
            }

            FeedEvent.Import -> {
                navigator.goTo(ImportActivityScreen)
            }

            FeedEvent.OpenMyProfile -> {
                navigator.goTo(ProfileScreen(profiles.did.value))
            }

            FeedEvent.Search -> {
                navigator.goTo(AthleteSearchScreen)
            }
        }
    }
}
