package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.browse.MapSelection
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.GetPath
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlinx.collections.immutable.toImmutableList

/**
 * The Trail page (segments/path-page): loads the path and the segments on it, lists them one row per segment group, longest first, narrowed
 * to the browse filter it was opened with until the athlete shows every sport, and shares a selection between the map and the list as
 * browse does ([MapSelection]).
 */
@CircuitInject(PathScreen::class, SessionScope::class)
@Composable
fun PathPresenter(screen: PathScreen, navigator: Navigator, segments: SegmentRepository): PathState {
    var sport by rememberRetained { mutableStateOf(screen.sport) }
    var retry by rememberRetained { mutableIntStateOf(0) }
    val selection = rememberRetained { MapSelection() }
    // Retained, so coming back from a segment shows the page at once; a retry reloads under it. A new sport filter only regroups what
    // loaded, so it never asks again.
    val loaded = produceRetainedContentState<GetPath.Output?>(null, screen.uri, retry) { segments.pathOutcome(AtUri(screen.uri)) }
    val content =
        remember(loaded, sport) { ContentState(loaded.data?.let { contentOf(it, sport) } ?: PathContent(), loaded.origin, loaded.status) }

    // Once loaded, frame the whole path, once per path.
    val path = content.data.path
    var framed by rememberRetained { mutableStateOf<String?>(null) }
    SideEffect {
        if (path != null && framed != path.uri.value) {
            framed = path.uri.value
            selection.frame(path.lines.flatMap { SegmentGeometry.decodePolyline(it).orEmpty() })
        }
    }

    val mapLines =
        remember(content.data) {
            (content.data.path?.let { BrowseRow.Path(it).mapSegmentLines() }.orEmpty().map { it.muted() } +
                    content.data.groups.mapNotNull { it.shown.mapSegmentLine() })
                .toImmutableList()
        }

    fun open(uri: AtUri) = navigator.goTo(SegmentScreen(uri.value))

    return PathState(
        content = content,
        sport = sport,
        mapLines = mapLines,
        selected = selection.selected,
        camera = selection.camera,
    ) { event ->
        when (event) {
            PathEvent.Back -> navigator.pop()
            PathEvent.Retry -> retry++
            PathEvent.ShowAllSports -> sport = null
            is PathEvent.ViewportChanged -> selection.viewportChanged(event.viewport)
            is PathEvent.Select -> selection.selected = event.uri
            is PathEvent.Open -> open(event.uri)
            is PathEvent.TapRow ->
                if (event.uri == selection.selected) {
                    open(event.uri)
                } else {
                    val row = content.data.groups.find { it.shown.uri == event.uri }
                    selection.selectRow(event.uri, row?.let { SegmentGeometry.decodePolyline(it.shown.record.line) })
                }
        }
    }
}

/** [output] as the page lists it: its segments as segment groups of [sport] (or every group), longest first, then by name. */
internal fun contentOf(output: GetPath.Output, sport: String?): PathContent {
    val listed = output.segments.filter { sport == null || it.record.sport == sport }
    val groups =
        SegmentGroupRow.of(listed, sport)
            .sortedWith(compareByDescending<SegmentGroupRow> { it.shown.distanceMeters }.thenBy { it.shown.currentName })
    val path = output.path
    return PathContent(path, groups.toImmutableList(), creditOf(path.source, path.license, path.attribution))
}

/** Path lines are drawn muted under the segments, as lifts are on browse, so they're never selected or highlighted. */
private fun MapSegmentLine.muted(): MapSegmentLine = copy(lift = true)
