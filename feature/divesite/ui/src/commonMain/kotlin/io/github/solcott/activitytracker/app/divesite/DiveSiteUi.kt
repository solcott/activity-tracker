package io.github.solcott.activitytracker.app.divesite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.RouteEnds
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.names.OriginalName
import io.github.solcott.activitytracker.app.names.PlaceNameDialogs
import io.github.solcott.activitytracker.app.names.PlaceNames
import io.github.solcott.activitytracker.app.segment.segmentStatusLabel
import io.github.solcott.activitytracker.app.settings.monthName
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.ui.ActivityCard
import io.github.solcott.activitytracker.app.ui.ActivityMap
import io.github.solcott.activitytracker.app.ui.Avatar
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.EmptyListText
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.adaptive.AppCard
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableList
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.handleLabel
import io.github.solcott.activitytracker.app.ui.pagedFooter
import io.github.solcott.activitytracker.app.ui.pagedRefreshStatus
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.dive_site_conditions
import io.github.solcott.activitytracker.composeApp.resources.dive_site_conditions_depth
import io.github.solcott.activitytracker.composeApp.resources.dive_site_conditions_from
import io.github.solcott.activitytracker.composeApp.resources.dive_site_conditions_month
import io.github.solcott.activitytracker.composeApp.resources.dive_site_conditions_temperature
import io.github.solcott.activitytracker.composeApp.resources.dive_site_conditions_visibility
import io.github.solcott.activitytracker.composeApp.resources.dive_site_dives
import io.github.solcott.activitytracker.composeApp.resources.dive_site_duplicate_help
import io.github.solcott.activitytracker.composeApp.resources.dive_site_no_dives
import io.github.solcott.activitytracker.composeApp.resources.dive_site_radius
import io.github.solcott.activitytracker.composeApp.resources.dive_site_regulars_card
import io.github.solcott.activitytracker.composeApp.resources.dive_site_regulars_none
import io.github.solcott.activitytracker.composeApp.resources.dive_site_regulars_yours
import io.github.solcott.activitytracker.composeApp.resources.dive_site_status_archived_help
import io.github.solcott.activitytracker.composeApp.resources.dive_site_status_proposed_help
import io.github.solcott.activitytracker.composeApp.resources.dive_site_visits
import io.github.solcott.activitytracker.composeApp.resources.segment_created_by
import io.github.solcott.activitytracker.composeApp.resources.segment_credit_license
import io.github.solcott.activitytracker.composeApp.resources.segment_credit_osm
import io.github.solcott.activitytracker.composeApp.resources.segment_open_original
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.DiveSiteConditions
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView
import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

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
    PlaceNameDialogs(
        sheet = state.nameSheet,
        confirmingDelete = state.confirmDelete != null,
        onSheetEvent = { state.eventSink(DiveSiteEvent.NameSheet(it)) },
        onConfirmDelete = { state.eventSink(DiveSiteEvent.ConfirmDelete(it)) },
    )
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
                val proposals = remember(site.nameProposals) { site.nameProposals.toImmutableList() }
                val history = remember(site.nameHistory) { site.nameHistory.toImmutableList() }
                PlaceNames(
                    proposals = proposals,
                    history = history,
                    canPropose = state.canPropose,
                    canVote = state.canVote,
                    canVoteHere = state.canVoteHere,
                    error = state.nameError,
                    self = state.self,
                    onPropose = { state.eventSink(DiveSiteEvent.ProposeName) },
                    onEdit = { state.eventSink(DiveSiteEvent.EditProposal(it)) },
                    onDelete = { state.eventSink(DiveSiteEvent.DeleteProposal(it)) },
                    onVote = { proposal, direction -> state.eventSink(DiveSiteEvent.Vote(proposal, direction)) },
                )
            }
            dives(state)
        }
    }
}

/** The site's radius, creator, data credit, status and top Regular. */
@Composable
private fun About(state: DiveSiteState, site: DiveSiteView) {
    Column(Modifier.padding(vertical = 12.dp)) {
        state.originalName?.let { OriginalName(it, Modifier.padding(bottom = 4.dp)) }
        Text(
            stringResource(Res.string.dive_site_radius, Format.elevation(site.record.radius.toDouble())),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(Res.string.segment_created_by, handleLabel(site.creator.handle.value)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Credit(site)
        Status(site) { state.eventSink(DiveSiteEvent.OpenOriginal) }
        state.regulars?.let { regulars ->
            RegularsCard(regulars, onClick = { state.eventSink(DiveSiteEvent.OpenRegulars) }, Modifier.padding(top = 12.dp))
        }
    }
}

/** Credit for where the site's position came from: OpenStreetMap's attribution for an imported one, and its licence. */
@Composable
private fun Credit(site: DiveSiteView) {
    if (site.record.source == SegmentSource.OSM) {
        val uriHandler = LocalUriHandler.current
        AppTextButton(onClick = { uriHandler.openUri(OSM_COPYRIGHT_URL) }) { Text(stringResource(Res.string.segment_credit_osm)) }
    }
    if (site.record.license.isNotBlank()) {
        Text(
            stringResource(Res.string.segment_credit_license, site.record.license),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A site that isn't active: its status and what it means, and for a duplicate a link to the original. */
@Composable
private fun Status(site: DiveSiteView, onOpenOriginal: () -> Unit) {
    val status = SegmentStatus.of(site.status)
    val help =
        when (status) {
            SegmentStatus.PROPOSED -> Res.string.dive_site_status_proposed_help
            SegmentStatus.DUPLICATE -> Res.string.dive_site_duplicate_help
            SegmentStatus.ARCHIVED -> Res.string.dive_site_status_archived_help
            else -> return
        }
    Column(Modifier.padding(top = 8.dp)) {
        segmentStatusLabel(site.status)?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
        }
        Text(stringResource(help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (status == SegmentStatus.DUPLICATE && site.duplicateOf != null) {
            AppTextButton(onClick = onOpenOriginal) { Text(stringResource(Res.string.segment_open_original)) }
        }
    }
}

/** An active site's top Regular and their count, or that it has none yet, and the viewer's own count; tapping it opens the board. */
@Composable
private fun RegularsCard(regulars: RegularsSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth().clickable(onClick = onClick).handCursor()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(Res.string.dive_site_regulars_card),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
            val leader = regulars.leader
            if (leader == null) {
                Text(
                    stringResource(Res.string.dive_site_regulars_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val name = leader.athlete.shownName
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(leader.athlete.avatar, name, 36.dp)
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        pluralStringResource(Res.plurals.dive_site_visits, leader.visits, leader.visits),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            regulars.viewer?.let { own ->
                Text(
                    pluralStringResource(Res.plurals.dive_site_regulars_yours, own.visits, own.visits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The site's conditions in the viewer's units (dive-sites/site-conditions): water temperature, visibility and depth, with how many dives
 * they come from, and month by month when more than one month has dives. Each line only when some dive recorded it.
 */
@Composable
private fun Conditions(conditions: DiveSiteConditions, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.dive_site_conditions), style = MaterialTheme.typography.titleSmall)
            Text(
                pluralStringResource(Res.plurals.dive_site_conditions_from, conditions.dives, conditions.dives),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            conditions.medianTemperature?.let { median ->
                Text(
                    stringResource(
                        Res.string.dive_site_conditions_temperature,
                        Format.temperature(median),
                        Format.temperature(conditions.minTemperature),
                        Format.temperature(conditions.maxTemperature),
                    )
                )
            }
            conditions.medianVisibility?.let {
                Text(stringResource(Res.string.dive_site_conditions_visibility, Format.depth(it / CM_PER_M)))
            }
            conditions.medianMaxDepth?.let { median ->
                Text(
                    stringResource(
                        Res.string.dive_site_conditions_depth,
                        Format.depth(median / CM_PER_M),
                        Format.depth(conditions.maxDepth?.let { it / CM_PER_M }),
                    )
                )
            }
            if (conditions.months.size > 1) {
                for (month in conditions.months) {
                    Text(
                        stringResource(
                            Res.string.dive_site_conditions_month,
                            monthName(month.month),
                            Format.temperature(month.medianTemperature),
                            Format.depth(month.medianVisibility?.let { it / CM_PER_M }),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** The dives at the site, newest first, each opening its page, then the next page's spinner or failure, or that there are none. */
private fun LazyListScope.dives(state: DiveSiteState) {
    item {
        Text(stringResource(Res.string.dive_site_dives), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
    }
    pagedRefreshStatus(state.dives, UiError.Kind.LoadDiveSiteDives) { state.eventSink(DiveSiteEvent.RetryDives) }
    items(state.dives.items, key = { it.uri.value }) { dive ->
        val open = { state.eventSink(DiveSiteEvent.OpenDive(dive.uri)) }
        ActivityCard(dive, onClick = open, onAuthorClick = null, onLike = null, onOpenMedia = { open() }, Modifier.padding(bottom = 12.dp))
    }
    pagedFooter(state.dives, UiError.Kind.LoadDiveSiteDives, onRetry = { state.eventSink(DiveSiteEvent.RetryDives) }) {
        EmptyListText(stringResource(Res.string.dive_site_no_dives), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val CM_PER_M = 100.0

/** OSM's attribution guideline asks for a link here. */
private const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
