package io.github.solcott.activitytracker.app.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.detail.rememberOwnRoute
import io.github.solcott.activitytracker.app.detail.shownRoute
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.activityType
import io.github.solcott.activitytracker.lexicon.app.effectiveRecord

@CircuitInject(RouteMapScreen::class, SessionScope::class)
@Composable
fun RouteMapPresenter(
    screen: RouteMapScreen,
    navigator: Navigator,
    feed: FeedRepository,
    activities: ActivityRepository,
    profiles: ProfileRepository,
): RouteMapState {
    val uri = AtUri(screen.uri)
    var activity by remember { mutableStateOf<ActivityView?>(null) }
    var error by remember { mutableStateOf<UiError?>(null) }
    // The athlete's own edit shows here too, before the AppView has it.
    val rewrite by remember(uri) { feed.rewrites.observe(uri) }.collectAsState(null)
    val flights = rememberSingleFlight()

    LaunchedEffect(uri) {
        runSuspendCatching { feed.activity(uri) }
            .onSuccess { activity = it }
            .onFailure { error = UiError.of(UiError.Kind.LoadActivity, it) }
    }

    val shown = activity?.let { a -> rewrite?.let { feed.rewrites.rewritten(a, it) } ?: a }
    // As the detail page draws it: every detail the viewer was served, and the owner's full track once read.
    val record = remember(shown) { shown?.effectiveRecord() }
    val isOwn = shown != null && shown.author.did == profiles.did
    val ownRoute = rememberOwnRoute(shown?.uri?.takeIf { isOwn }, activities, flights, combined = record?.combined != null)
    val route = remember(record, ownRoute.map) { record?.let { shownRoute(it, ownRoute.map) } }
    return RouteMapState(
        route = route,
        title = record?.title,
        activityType = record?.activityType,
        isLoading = shown == null && error == null,
        error = error,
    ) { event ->
        when (event) {
            RouteMapEvent.Back -> navigator.pop()
        }
    }
}
