package io.github.solcott.activitytracker.app.divesite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.RouteEnds
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.names.OriginalName
import io.github.solcott.activitytracker.app.names.PlaceNameDialogs
import io.github.solcott.activitytracker.app.names.PlaceNames
import io.github.solcott.activitytracker.app.segment.CreatedBy
import io.github.solcott.activitytracker.app.segment.PlaceStatus
import io.github.solcott.activitytracker.app.segment.PlaceTexts
import io.github.solcott.activitytracker.app.ui.ActivityCard
import io.github.solcott.activitytracker.app.ui.ActivityMap
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.EmptyListText
import io.github.solcott.activitytracker.app.ui.LeaderCard
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.pagedFooter
import io.github.solcott.activitytracker.app.ui.pagedRefreshStatus
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.DiveSiteConditions
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView

/**
 * A dive site: its centre and radius on a map, its original name when renamed, its creator and data credit, its status, its top Regular,
 * its conditions in the viewer's units, its community names (dive-sites/site-names), then the dives at it the viewer may see.
 */
@CircuitInject(DiveSiteScreen::class, SessionScope::class)
@Composable
fun DiveSiteUi(state: DiveSiteState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = state.site?.currentName.orEmpty(),
                onBack = { state.eventSink(DiveSiteEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val retry = { state.eventSink(DiveSiteEvent.Retry) }
        ContentStateLayout(state.content, UiError.Kind.LoadDiveSite, onRetry = retry, contentPadding = padding) { loaded ->
            // Loaded content is never null; the placeholder before the first load is.
            if (loaded != null) Loaded(state, loaded, padding, retry)
        }
    }
    PlaceNameDialogs(state.names)
}

@Composable
private fun Loaded(state: DiveSiteState, site: DiveSiteView, padding: PaddingValues, onRetry: () -> Unit) {
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState, state.dives.canLoadMore) { state.eventSink(DiveSiteEvent.LoadMoreDives) }
    ReadableList(listState, Modifier.fillMaxSize(), padding) { listPadding ->
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = listPadding + PaddingValues(vertical = 12.dp)) {
            item { RefreshStatus(state.content, UiError.Kind.LoadDiveSite, onRetry) }
            state.outline?.let { outline -> item { ActivityMap(outline, Modifier.fillMaxWidth(), ends = RouteEnds.HIDDEN) } }
            item { About(state, site) }
            state.conditions?.let { conditions -> item { Conditions(conditions, Modifier.padding(bottom = 12.dp)) } }
            item {
                val proposals = remember(site.nameProposals) { state.proposals }
                val history = remember(site.nameHistory) { state.history }
                PlaceNames(
                    proposals = proposals,
                    history = history,
                    canPropose = state.canPropose,
                    canVote = state.canVote,
                    fields = state.names,
                )
            }
            dives(state)
        }
    }
}

/** The site's radius, creator, data credit, status and top Regular, as [DiveSiteTexts] words them. */
@Composable
private fun About(state: DiveSiteState, site: DiveSiteView) {
    Column(Modifier.padding(vertical = 12.dp)) {
        state.originalName?.let { OriginalName(it, Modifier.padding(bottom = 4.dp)) }
        Text(DiveSiteTexts.radius(site, LocalUnitSystem.current).string(), style = MaterialTheme.typography.titleMedium)
        CreatedBy(site.creator.handle.value)
        Credit(site)
        PlaceStatus(
            label = DiveSiteTexts.status(site),
            help = DiveSiteTexts.statusHelp(site),
            offersOriginal = DiveSiteTexts.offersOriginal(site),
            onOpenOriginal = { state.eventSink(DiveSiteEvent.OpenOriginal) },
        )
        state.regulars?.let { regulars ->
            RegularsCard(regulars, onClick = { state.eventSink(DiveSiteEvent.OpenRegulars) }, Modifier.padding(top = 12.dp))
        }
    }
}

/** Credit for where the site's position came from: OpenStreetMap's attribution for an imported one, and its licence. */
@Composable
private fun Credit(site: DiveSiteView) {
    DiveSiteTexts.osmCredit(site)?.let { credit ->
        val uriHandler = LocalUriHandler.current
        AppTextButton(onClick = { uriHandler.openUri(PlaceTexts.OSM_COPYRIGHT_URL) }) { Text(credit.string()) }
    }
    DiveSiteTexts.license(site)?.let {
        Text(it.string(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** An active site's top Regular and their count, or that it has none yet, and the viewer's own count; tapping it opens the board. */
@Composable
private fun RegularsCard(regulars: RegularsSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LeaderCard(
        title = DiveSiteTexts.regularsTitle.string(),
        leader = regulars.leader?.athlete,
        leaderValue = regulars.leader?.let { DiveSiteRegularsTexts.visits(it).string() }.orEmpty(),
        noneText = DiveSiteTexts.regularsNone.string(),
        viewerText = regulars.viewer?.let { DiveSiteTexts.yourVisits(it).string() },
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * The site's conditions in the viewer's units (dive-sites/site-conditions): water temperature, visibility and depth, with how many dives
 * they come from, and month by month when more than one month has dives. Each line only when some dive recorded it.
 */
@Composable
private fun Conditions(conditions: DiveSiteConditions, modifier: Modifier = Modifier) {
    val units = LocalUnitSystem.current
    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(DiveSiteTexts.conditionsTitle.string(), style = MaterialTheme.typography.titleSmall)
            Text(
                DiveSiteTexts.conditionsFrom(conditions).string(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (line in DiveSiteTexts.conditionLines(conditions, units)) Text(line.string())
            for (line in DiveSiteTexts.monthLines(conditions, units)) Text(line.string(), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The dives at the site, newest first, each opening its page, then the next page's spinner or failure, or that there are none. */
private fun LazyListScope.dives(state: DiveSiteState) {
    item { Text(DiveSiteTexts.dives.string(), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.titleMedium) }
    pagedRefreshStatus(state.dives, UiError.Kind.LoadDiveSiteDives) { state.eventSink(DiveSiteEvent.RetryDives) }
    items(state.dives.items, key = { it.uri.value }) { dive ->
        val open = { state.eventSink(DiveSiteEvent.OpenDive(dive.uri)) }
        ActivityCard(dive, onClick = open, onAuthorClick = null, onLike = null, onOpenMedia = { open() }, Modifier.padding(bottom = 12.dp))
    }
    pagedFooter(state.dives, UiError.Kind.LoadDiveSiteDives, onRetry = { state.eventSink(DiveSiteEvent.RetryDives) }) {
        EmptyListText(DiveSiteTexts.noDives.string(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
