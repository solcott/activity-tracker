package io.github.solcott.activitytracker.app.divesite

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.ui.LoadMoreAtEnd
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.MapBrowseAreaList
import io.github.solcott.activitytracker.app.ui.MapBrowseHeader
import io.github.solcott.activitytracker.app.ui.MapBrowseHover
import io.github.solcott.activitytracker.app.ui.MapBrowseLayout
import io.github.solcott.activitytracker.app.ui.MapBrowseListPosition
import io.github.solcott.activitytracker.app.ui.MapBrowseLocationRequest
import io.github.solcott.activitytracker.app.ui.MapBrowseMap
import io.github.solcott.activitytracker.app.ui.MapBrowseNameResults
import io.github.solcott.activitytracker.app.ui.MapBrowseRows
import io.github.solcott.activitytracker.app.ui.SelectableRow
import io.github.solcott.activitytracker.app.ui.areaRowsBefore
import io.github.solcott.activitytracker.app.ui.mapBrowseAreaRows
import io.github.solcott.activitytracker.app.ui.rememberMapBrowseHover
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView

/**
 * Dive sites around a place and sites found by name (dive-sites/site-browse), laid out by [MapBrowseLayout] as segment browse is. It has no
 * bar of its own: the Search tab hosts it. The site under a mouse pointer, in a row or on the map, is this UI's own: highlighted on the map
 * and marked in the list while it's there.
 */
@CircuitInject(DiveSiteBrowseScreen::class, SessionScope::class)
@Composable
fun DiveSiteBrowseUi(state: DiveSiteBrowseState, modifier: Modifier = Modifier) {
    val browse = state.browse
    MapBrowseLocationRequest(browse)
    val listState = rememberLazyListState()
    val hover = rememberMapBrowseHover()
    val retry = { state.eventSink(DiveSiteBrowseEvent.Retry) }
    LoadMoreAtEnd(listState, browse.searchingNames && state.results.canLoadMore) { state.eventSink(DiveSiteBrowseEvent.LoadMore) }
    MapBrowseListPosition(
        browse,
        listState,
        firstResult = if (browse.searchingNames) state.results.items.firstOrNull()?.uri?.value else null,
    ) { selected ->
        if (browse.searchingNames) state.results.items.indexOfFirst { it.uri == selected }
        else
            state.area.data.sites
                .indexOfFirst { it.site.uri == selected }
                .takeIf { it >= 0 }
                ?.let { it + areaRowsBefore(state.area.data.capped) } ?: -1
    }
    MapBrowseLayout(
        header = { MapBrowseHeader(browse, DiveSiteBrowseTexts.searchLabel) },
        map = { MapBrowseMap(browse, hover, it) },
        rows = { rowsModifier ->
            MapBrowseRows(
                browse,
                message = DiveSiteBrowseTexts::message,
                names = {
                    MapBrowseNameResults(
                        browse,
                        state.results,
                        UiError.Kind.SearchDiveSites,
                        onRetry = retry,
                        noResults = DiveSiteBrowseTexts.noResults(state),
                        listState = listState,
                        key = { it.uri.value },
                    ) { site ->
                        SiteRow(state, hover, site, DiveSiteBrowseTexts.details(site, LocalUnitSystem.current))
                    }
                },
                area = {
                    MapBrowseAreaList(state.area, UiError.Kind.LoadAreaDiveSites, retry, listState) { area ->
                        mapBrowseAreaRows(
                            area.sites,
                            area.capped,
                            DiveSiteBrowseTexts.capped,
                            DiveSiteBrowseTexts.noneHere,
                            key = { it.site.uri.value },
                        ) { browsed ->
                            SiteRow(state, hover, browsed.site, DiveSiteBrowseTexts.details(browsed, LocalUnitSystem.current))
                        }
                    }
                },
                modifier = rowsModifier,
            )
        },
        modifier = modifier,
    )
}

/** A site: its current name, then its [details] line, selected and opened as segment browse's rows are. */
@Composable
private fun SiteRow(state: DiveSiteBrowseState, hover: MapBrowseHover, site: DiveSiteView, details: AppText) {
    SelectableRow(
        key = site.uri.value,
        name = AppText.written(site.currentName),
        details = details,
        selected = state.isSelected(site),
        hover = hover,
        onTap = { state.eventSink(state.tapRowEvent(site)) },
        onOpen = { state.eventSink(state.openEvent(site)) },
    )
}
