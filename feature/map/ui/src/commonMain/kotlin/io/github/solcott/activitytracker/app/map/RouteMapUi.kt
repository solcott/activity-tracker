package io.github.solcott.activitytracker.app.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.ActivityMap
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.activityTypeLabel
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.data.di.SessionScope

/** The activity's route on a map filling the screen, which the viewer pans and zooms, recenters on the route, and leaves with back. */
@CircuitInject(RouteMapScreen::class, SessionScope::class)
@Composable
fun RouteMapUi(state: RouteMapState, modifier: Modifier = Modifier) {
    val loaded = state.content.data
    val title = loaded?.let { it.title ?: activityTypeLabel(it.activityType) }
    AppScaffold(
        modifier = modifier,
        topBar = { AppTopBar(title.orEmpty(), onBack = { state.eventSink(RouteMapEvent.Back) }) },
    ) { padding ->
        ContentStateLayout(
            state.content,
            UiError.Kind.LoadActivity,
            onRetry = { state.eventSink(RouteMapEvent.Retry) },
            contentPadding = padding,
        ) { content ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                content?.route?.let { route ->
                    ActivityMap(
                        route.polyline,
                        Modifier.fillMaxSize(),
                        // A height clamps to the box it is in, so this fills it.
                        height = Dp.Infinity,
                        gaps = route.gaps,
                        ends = route.ends,
                        interactive = true,
                    )
                }
                RefreshStatus(
                    state.content,
                    UiError.Kind.LoadActivity,
                    onRetry = { state.eventSink(RouteMapEvent.Retry) },
                    Modifier.align(Alignment.TopCenter).padding(8.dp),
                )
            }
        }
    }
}
