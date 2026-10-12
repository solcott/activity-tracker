package io.github.solcott.activitytracker.app.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.activityfile.TimeZones
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.feedfilter.FeedFilter
import io.github.solcott.activitytracker.app.feedfilter.FeedFilterScreen
import io.github.solcott.activitytracker.app.media.MediaGalleryScreen
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.app.record.ManualActivityScreen
import io.github.solcott.activitytracker.app.record.RecordScreen
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.location.LocationTracker
import kotlin.time.Clock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.datetime.todayIn

@CircuitInject(FeedScreen::class, SessionScope::class)
@Composable
fun FeedPresenter(
    navigator: Navigator,
    feed: FeedRepository,
    activities: ActivityRepository,
    profiles: ProfileRepository,
    locationTracker: LocationTracker,
    clock: Clock,
): FeedState {
    val viewerDid = profiles.did.value
    // Retained, so the scope and filter survive opening an activity and coming back.
    var scope by rememberRetained { mutableStateOf(FeedScope.Following) }
    var filter by rememberRetained { mutableStateOf(FeedFilter.Default) }
    // The control owns the live filter, so clearing from outside it re-seeds it with a new screen.
    var filterScreen by rememberRetained { mutableStateOf(FeedFilterScreen(filter)) }
    val zone = remember { TimeZones.device() }
    val query = remember(filter) { filter.toActivityFilter(clock.todayIn(zone), zone) }
    val mine = scope == FeedScope.Mine
    // Keyed by the query, so a new filter starts from its own cache instead of showing the last filter's activities.
    val items =
        key(mine, query) {
            remember { feed.feed(mine, viewerDid, query).map { it.toImmutableList() } }.collectAsState(persistentListOf()).value
        }
    val pending by activities.pendingWrites.collectAsState(0)
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiError?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var syncedFor by remember { mutableIntStateOf(-1) }
    val flights = rememberSingleFlight()

    LaunchedEffect(refreshKey, mine, query) {
        refreshing = true
        error =
            runSuspendCatching {
                // The outbox drains on the first load and on an explicit refresh, not on every filter change.
                if (syncedFor != refreshKey) {
                    activities.sync()
                    syncedFor = refreshKey
                }
                feed.refreshFeed(mine, viewerDid, query)
            }
                .exceptionOrNull()
                ?.let { UiError.of(UiError.Kind.LoadFeed, it) }
        refreshing = false
    }

    return FeedState(items, refreshing, error, pending, viewerDid, locationTracker.isSupported, scope, Scopes, filter, filterScreen) { event
        ->
        when (event) {
            FeedEvent.Refresh -> {
                refreshKey++
            }

            is FeedEvent.SetScope -> {
                scope = event.scope
            }

            is FeedEvent.FilterChanged -> {
                filter = event.filter
            }

            FeedEvent.ClearFilters -> {
                // One snapshot, so the feed never shows the cleared filter with the old control.
                Snapshot.withMutableSnapshot {
                    filter = FeedFilter.Default
                    filterScreen = FeedFilterScreen(FeedFilter.Default)
                }
            }

            is FeedEvent.Open -> {
                navigator.goTo(ActivityDetailScreen(event.activity.uri.value))
            }

            is FeedEvent.OpenMedia -> {
                navigator.goTo(MediaGalleryScreen(event.activity.uri.value, event.index))
            }

            is FeedEvent.OpenAuthor -> {
                navigator.goTo(ProfileScreen(event.activity.author.did.value))
            }

            is FeedEvent.ToggleKudos -> {
                flights.launch(event.activity.uri) { runSuspendCatching { activities.toggleKudos(event.activity) } }
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
        }
    }
}

/** Every scope, until clubs make the list the viewer's. */
private val Scopes = FeedScope.entries.toImmutableList()
