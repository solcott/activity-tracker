package io.github.solcott.activitytracker.app.divesite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.MapViewport
import io.github.solcott.activitytracker.app.browse.MapBrowse
import io.github.solcott.activitytracker.app.browse.areaContent
import io.github.solcott.activitytracker.app.browse.nameResults
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.divesites.DiveSiteRepository
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView
import io.github.solcott.activitytracker.lexicon.app.SearchDiveSiteNames
import io.github.solcott.activitytracker.lexicon.app.SearchDiveSites
import io.github.solcott.activitytracker.location.PlaceLocator
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.location.pointE7
import io.github.solcott.activitytracker.location.siteOutline
import io.github.solcott.activitytracker.textfield.rememberTextFieldState
import io.github.solcott.dataresult.mapData
import kotlin.math.roundToInt
import kotlinx.collections.immutable.toImmutableList

@CircuitInject(DiveSiteBrowseScreen::class, SessionScope::class)
@Composable
fun DiveSiteBrowsePresenter(
    navigator: Navigator,
    sites: DiveSiteRepository,
    feed: FeedRepository,
    locator: PlaceLocator,
): DiveSiteBrowseState {
    // Saveable, so the query (and with it the results) is still there on coming back from a site.
    val query = rememberTextFieldState()
    val trimmed = query.text.trim().toString()
    val browse = rememberRetained { MapBrowse(locator.permission()) }

    // Where to open, once: the athlete's location, else where their latest activity with a map started, else nowhere.
    LaunchedEffect(browse.permission) { browse.locate(locator) { feed.latestOwnStart() } }

    // The area's sites, while no name is searched and the map is close enough. A new area keeps the last one's outlines until it answers.
    val area =
        browse.areaContent(SiteArea(), searching = trimmed.isNotEmpty()) { request ->
            sites.areaSites(request.toSearchBox()).mapData { siteAreaOf(it.sites, request, it.capped) }
        }

    // Name results: a new query keeps the last results until it answers; a new scope drops them.
    val results =
        browse.nameResults(trimmed, idOf = DiveSiteView::uri) { box, viewport, cursor ->
            sites.namesPage(siteNameParams(trimmed, box, viewport, cursor))
        }

    val searching = trimmed.isNotEmpty()
    val mapLines =
        remember(searching, results.state.items, area.data) {
            val shown = if (searching) results.state.items else area.data.sites.map { it.site }
            shown.map { it.mapOutline() }.toImmutableList()
        }

    return DiveSiteBrowseState(browse = browse.state(query, mapLines), area = area, results = results.state) { event ->
        when (event) {
            DiveSiteBrowseEvent.Back -> navigator.pop()
            is DiveSiteBrowseEvent.Open -> navigator.goTo(DiveSiteScreen(event.uri.value))
            is DiveSiteBrowseEvent.TapRow ->
                browse.tapRow(
                    event.uri,
                    points = {
                        // Only a name result can lie off the map: every site an area lists is in it.
                        val site = if (searching) results.state.items.find { it.uri == event.uri } else null
                        site?.let { listOf(pointE7(it.record.lat, it.record.lng)) }
                    },
                    open = { navigator.goTo(DiveSiteScreen(event.uri.value)) },
                )
            DiveSiteBrowseEvent.LoadMore -> results.loadMore()
            DiveSiteBrowseEvent.Retry -> if (trimmed.isNotEmpty()) results.retry() else browse.areaRetry++
        }
    }
}

/** The viewport's box, as `feed.searchDiveSites` asks for it. */
private fun MapViewport.toSearchBox() = SearchDiveSites.Params(minLat = minLat, minLng = minLng, maxLat = maxLat, maxLng = maxLng)

/**
 * A name search's parameters: the [box] it covers (This area's, or none for everywhere), and the map's centre to order by when there is
 * one. Without a map (web), everywhere and by match alone.
 */
internal fun siteNameParams(q: String, box: MapViewport?, viewport: MapViewport?, cursor: String?) =
    SearchDiveSiteNames.Params(
        q = q,
        minLat = box?.minLat,
        minLng = box?.minLng,
        maxLat = box?.maxLat,
        maxLng = box?.maxLng,
        nearLat = viewport?.centerLat,
        nearLng = viewport?.centerLng,
        cursor = cursor,
    )

/** The area's sites, nearest the viewport's centre first (dive-sites/site-browse). */
internal fun siteAreaOf(found: List<DiveSiteView>, viewport: MapViewport, capped: Boolean): SiteArea {
    val centre = pointE7(viewport.centerLat, viewport.centerLng)
    return SiteArea(
        sites =
            found
                .map { site ->
                    val at = pointE7(site.record.lat, site.record.lng)
                    BrowsedSite(site, SegmentGeometry.distanceMeters(at, centre).roundToInt())
                }
                .sortedWith(compareBy(BrowsedSite::metersAway).thenBy { it.site.currentName })
                .toImmutableList(),
        capped = capped,
    )
}

/** [this] site as the browse map draws it: its radius as a closed outline. */
internal fun DiveSiteView.mapOutline(): MapSegmentLine =
    MapSegmentLine(uri.value, siteOutline(record.lat, record.lng, record.radius), trail = false)
