package io.github.solcott.activitytracker.app.divesite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.names.ProposePlaceNameOuterEvent
import io.github.solcott.activitytracker.app.names.ProposePlaceNameScreen
import io.github.solcott.activitytracker.app.names.sharedPlaceOf
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.app.names.rememberPlaceNames
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.divesites.DiveSiteRepository
import io.github.solcott.activitytracker.data.segments.SegmentNames
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.DiveSiteConditions
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.github.solcott.activitytracker.lexicon.app.RegularsPeriod
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.location.siteOutline
import io.github.solcott.dataresult.dataOrNull
import io.github.solcott.dataresult.mapData
import io.github.solcott.uistate.circuit.produceRetainedContentState
import io.github.solcott.uistate.circuit.rememberRetainedPaging
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

@CircuitInject(DiveSiteScreen::class, SessionScope::class)
@Composable
@Suppress("LongMethod") // An event per action on the site and its dives.
fun DiveSitePresenter(screen: DiveSiteScreen, navigator: Navigator, sites: DiveSiteRepository, names: SegmentNames): DiveSiteState {
    val uri = AtUri(screen.uri)
    var loadKey by remember { mutableIntStateOf(0) }

    // Retained, so coming back from a dive shows the site at once; the viewer's own name changes show over each read.
    val content =
        produceRetainedContentState<DiveSiteView?>(null, loadKey) {
            combine(sites.siteOutcome(uri), names.changes) { outcome, _ -> outcome.mapData(names::applyTo) }
        }
    val site = content.data
    val outline = remember(site?.record) { site?.record?.let { siteOutline(it.lat, it.lng, it.radius) } }
    val dives =
        rememberRetainedPaging(uri, idOf = { dive: ActivityView -> dive.uri }) { cursor -> flow { emit(sites.divesPage(uri, cursor)) } }
    val active = site?.let { SegmentStatus.of(it.status) == SegmentStatus.ACTIVE } == true

    // Conditions and the top Regular are extra: a failed read leaves the last shown, or none, and never an error.
    var conditions by rememberRetained { mutableStateOf<DiveSiteConditions?>(null) }
    LaunchedEffect(loadKey) { sites.conditions(uri).dataOrNull()?.let { conditions = it } }
    var regulars by rememberRetained { mutableStateOf<RegularsSummary?>(null) }
    LaunchedEffect(active, loadKey) {
        if (!active) {
            regulars = null
            return@LaunchedEffect
        }
        sites.regularsPage(uri, RegularsPeriod.ALL_TIME, LeaderboardScope.EVERYONE, limit = 1).dataOrNull()?.let {
            regulars = RegularsSummary(it.items.firstOrNull(), it.meta.viewer)
        }
    }

    val placeNames =
        rememberPlaceNames(
            place = uri,
            currentName = site?.currentName.orEmpty(),
            proposals = remember(site?.nameProposals) { site?.nameProposals.orEmpty().toImmutableList() },
            canPropose = site?.viewer?.canProposeName == true,
            canVote = site?.viewer?.canVoteOnNames == true,
            sharedPlace = site?.let(::sharedPlaceOf),
            names = names,
        )

    return DiveSiteState(
        content = content,
        outline = outline,
        conditions = conditions,
        regulars = regulars,
        dives = dives.state,
        names = placeNames,
    ) { event ->
        when (event) {
            DiveSiteEvent.Back -> navigator.pop()
            DiveSiteEvent.Retry -> {
                loadKey++
                dives.retry()
            }
            is DiveSiteEvent.OpenDive -> navigator.goTo(ActivityDetailScreen(event.uri.value))
            DiveSiteEvent.LoadMoreDives -> dives.loadMore()
            DiveSiteEvent.RetryDives -> dives.retry()
            DiveSiteEvent.OpenOriginal -> site?.duplicateOf?.let { navigator.goTo(DiveSiteScreen(it.value)) }
            DiveSiteEvent.OpenRegulars ->
                site?.takeIf { active }?.let { navigator.goTo(DiveSiteRegularsScreen(it.uri.value, it.currentName)) }
        }
    }
}
