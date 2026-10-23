package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.ActivityMap
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.data.di.SessionScope

/**
 * The segment's line on a map filling the screen, which the viewer pans and zooms, recenters on the segment, and leaves with back. Its data
 * credit stays over the map's top-start corner, the one the map's own logo, attribution and recenter button leave free.
 */
@CircuitInject(SegmentMapScreen::class, SessionScope::class)
@Composable
fun SegmentMapUi(state: SegmentMapState, modifier: Modifier = Modifier) {
    val retry = { state.eventSink(SegmentMapEvent.Retry) }
    AppScaffold(
        modifier = modifier,
        topBar = { AppTopBar(state.content.data?.name.orEmpty(), onBack = { state.eventSink(SegmentMapEvent.Back) }) },
    ) { padding ->
        ContentStateLayout(state.content, UiError.Kind.LoadSegment, onRetry = retry, contentPadding = padding) { content ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                val density = LocalDensity.current
                // The credit's height, so the line is framed below it rather than under it.
                var creditHeight by remember { mutableStateOf(0.dp) }
                content?.polyline?.let { line ->
                    ActivityMap(
                        line,
                        Modifier.fillMaxSize(),
                        // A height clamps to the box it is in, so this fills it.
                        height = Dp.Infinity,
                        interactive = true,
                        overlayTop = if (content.credit != null) creditHeight + CREDIT_MARGIN else 0.dp,
                    )
                }
                Column(Modifier.fillMaxWidth().padding(CREDIT_MARGIN)) {
                    content?.credit?.let { credit ->
                        Column(
                            Modifier.widthIn(max = CREDIT_MAX_WIDTH)
                                .onSizeChanged { creditHeight = with(density) { it.height.toDp() } }
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = CREDIT_ALPHA), CREDIT_SHAPE)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Credit(credit)
                        }
                    }
                    RefreshStatus(state.content, UiError.Kind.LoadSegment, onRetry = retry, Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}

/** Narrow enough to leave the recenter button clear on a phone; a long agency name wraps. */
private val CREDIT_MAX_WIDTH = 260.dp
private val CREDIT_MARGIN = 8.dp
private const val CREDIT_ALPHA = 0.9f
private val CREDIT_SHAPE = RoundedCornerShape(8.dp)
