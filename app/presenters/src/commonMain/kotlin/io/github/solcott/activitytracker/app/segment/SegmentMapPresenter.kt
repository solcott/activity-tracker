package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.mapContent
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.uistate.circuit.produceRetainedContentState

@CircuitInject(SegmentMapScreen::class, SessionScope::class)
@Composable
fun SegmentMapPresenter(screen: SegmentMapScreen, navigator: Navigator, segments: SegmentRepository): SegmentMapState {
    val uri = AtUri(screen.uri)
    var loadKey by remember { mutableIntStateOf(0) }
    // Retained, so coming back shows the map at once; a retry reloads under it.
    val loaded = produceRetainedContentState<SegmentView?>(null, uri, loadKey) { segments.segmentOutcome(uri) }
    val segment = loaded.data
    // As the segment screen draws and credits it.
    val content = remember(segment) { segment?.let { SegmentMapContent(mapLine(it.record.line), it.currentName, creditOf(it.record)) } }
    return SegmentMapState(content = loaded.mapContent { _ -> content }) { event ->
        when (event) {
            SegmentMapEvent.Back -> navigator.pop()
            SegmentMapEvent.Retry -> loadKey++
        }
    }
}
