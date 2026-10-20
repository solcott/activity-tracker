package io.github.solcott.activitytracker.app.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.ActivityMap
import io.github.solcott.activitytracker.app.ui.activityTypeLabel
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.data.di.SessionScope

/** The activity's route on a map filling the screen, which the viewer pans and zooms, recenters on the route, and leaves with back. */
@CircuitInject(RouteMapScreen::class, SessionScope::class)
@Composable
fun RouteMapUi(state: RouteMapState, modifier: Modifier = Modifier) {
    val title = state.title ?: state.activityType?.let { activityTypeLabel(it) }
    AppScaffold(
        modifier = modifier,
        topBar = { AppTopBar(title.orEmpty(), onBack = { state.eventSink(RouteMapEvent.Back) }) },
    ) { padding ->
        val error = state.error
        val route = state.route
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                route != null ->
                    ActivityMap(
                        route.polyline,
                        Modifier.fillMaxSize(),
                        // A height clamps to the box it is in, so this fills it.
                        height = Dp.Infinity,
                        gaps = route.gaps,
                        ends = route.ends,
                        interactive = true,
                    )
                error != null ->
                    Text(errorText(error), Modifier.align(Alignment.Center).padding(24.dp), color = MaterialTheme.colorScheme.error)
                state.isLoading -> AppProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}
