package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.gear.GearScreen
import io.github.solcott.activitytracker.app.mapContent
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.data.units.UnitsRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.effectiveRecord
import io.github.solcott.uistate.circuit.produceRetainedContentState

/**
 * An activity's All data page: the activity, its charts and its lift rides as the activity page loads them, so its stats, tables and
 * sources match the page it was opened from, the owner's own edit laid over it.
 */
@CircuitInject(ActivityDataScreen::class, SessionScope::class)
@Composable
fun ActivityDataPresenter(
    screen: ActivityDataScreen,
    navigator: Navigator,
    feed: FeedRepository,
    profiles: ProfileRepository,
    units: UnitsRepository,
    segmentEfforts: SegmentRepository,
): ActivityDataState {
    val uri = AtUri(screen.uri)
    var loadKey by rememberRetained { mutableIntStateOf(0) }
    val content = produceRetainedContentState<ActivityView?>(null, loadKey) { feed.activityOutcome(uri) }
    val rewrite by remember(screen.uri) { feed.rewrites.observe(uri) }.collectAsState(null)
    val unitSystem by units.units.collectAsState()
    val charts = rememberCharts(uri, loadKey, unitSystem, feed)
    val segments = rememberSegments(uri, loadKey, segmentEfforts)

    val served = content.data?.let { view -> rewrite?.let { feed.rewrites.rewritten(view, it) } ?: view }
    val shown = remember(served) { served?.let { it.copy(record = it.effectiveRecord()) } }
    val isOwn = shown?.author?.did == profiles.did
    val sport = shown?.record?.sport
    val shownCharts = remember(sport, charts.charts, segments.liftEfforts) { chartsFor(sport, charts.charts, segments.liftEfforts) }
    val narrower = narrowerCategories(served?.takeIf { isOwn }, charts.streams)
    val page = rememberPage(shown, shownCharts, narrower)
    val sources = remember(shown, shownCharts) { chartSources(shown, shown?.let { detailSections(it, shownCharts) }.orEmpty()) }
    val start = DataSection.named(screen.section)
    val data = shown?.let { ActivityData.of(page, sources, start) }

    return ActivityDataState(content.mapContent { data }) { event ->
        when (event) {
            ActivityDataEvent.Back -> navigator.pop()
            ActivityDataEvent.Retry -> loadKey++
            is ActivityDataEvent.OpenGear -> navigator.goTo(GearScreen(event.uri))
        }
    }
}
