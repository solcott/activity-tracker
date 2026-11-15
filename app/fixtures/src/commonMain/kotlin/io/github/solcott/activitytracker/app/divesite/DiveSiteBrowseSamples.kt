package io.github.solcott.activitytracker.app.divesite

import io.github.solcott.activitytracker.app.MapCameraRequest
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.fixtures.ContentStates
import io.github.solcott.activitytracker.app.fixtures.PagedStates
import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.fixtures.contentSamples
import io.github.solcott.activitytracker.app.fixtures.fakeDiveSite
import io.github.solcott.activitytracker.app.fixtures.fakeDiveSiteRecord
import io.github.solcott.activitytracker.app.fixtures.pagedSamples
import io.github.solcott.activitytracker.app.segment.BrowseStart
import io.github.solcott.activitytracker.app.segment.NameScope
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView
import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.location.siteOutline
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.PagedState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Dive site browse (dive-sites/site-browse): an area's sites on a map, name results, and the states before either. */
object DiveSiteBrowseSamples {
    private fun osmSite(rkey: String, name: String, lat: Int, lng: Int) =
        fakeDiveSite(
            rkey,
            record = fakeDiveSiteRecord(name, lat = lat, lng = lng, radius = OSM_RADIUS, source = SegmentSource.OSM, license = "ODbL-1.0"),
        )

    private val thistlegorm = osmSite("3kthistle", "SS Thistlegorm", 278_136_000, 339_206_000)
    private val shagRock = osmSite("3kshag", "Shag Rock", 278_010_000, 339_480_000)
    private val dunraven = osmSite("3kdunraven", "Dunraven", 277_690_000, 339_850_000)

    val sites = persistentListOf(BrowsedSite(thistlegorm, 120), BrowsedSite(shagRock, 3_100), BrowsedSite(dunraven, 7_400))

    private val area = SiteArea(sites)

    private fun outlines(found: List<DiveSiteView>) = found.map {
        MapSegmentLine(it.uri.value, siteOutline(it.record.lat, it.record.lng, it.record.radius))
    }

    /** Browsing an area that loaded [content]; nothing typed. */
    private fun browsing(content: ContentState<SiteArea>) =
        DiveSiteBrowseState(
            query = textFieldStateOf(),
            hasArea = true,
            start = BrowseStart.Placed,
            area = content,
            results = PagedStates.end(emptyList(), Unit),
            mapLines = outlines(content.data.sites.map { it.site }).toImmutableList(),
            camera = MapCameraRequest(1, GUBAL_LAT, GUBAL_LNG),
        )

    /** Searching "thistle" in this area, with [results]. */
    private fun searching(results: PagedState<DiveSiteView, Unit>, scope: NameScope = NameScope.ThisArea) =
        browsing(ContentStates.loaded(area))
            .copy(
                query = textFieldStateOf("thistle"),
                scope = scope,
                results = results,
                mapLines = outlines(results.items).toImmutableList(),
            )

    /**
     * The area's states for the loading kinds a [ContentState] has, and name search's for those only a paged list has (ui/content-loading).
     */
    val all: Map<String, DiveSiteBrowseState>
        get() =
            contentSamples(SiteArea(), area, ::browsing) +
                pagedSamples(listOf(thistlegorm, shagRock), Unit) { searching(it) }
                    .filterKeys { it in setOf(SampleKind.EMPTY, SampleKind.LOADING_MORE, SampleKind.PAGE_FAILED, SampleKind.END) }

    val loaded: DiveSiteBrowseState
        get() = all.getValue(SampleKind.LOADED)

    /** A site tapped on the map, marked in the list. */
    val selected: DiveSiteBrowseState
        get() = loaded.copy(selected = shagRock.uri)

    /** Zoomed out past where sites are searched. */
    val tooWide: DiveSiteBrowseState
        get() = browsing(ContentStates.loading(SiteArea())).copy(tooWide = true, mapLines = persistentListOf())

    /** Name results in this area. */
    val nameResults: DiveSiteBrowseState
        get() = searching(PagedStates.end(listOf(thistlegorm), Unit))

    /** Nothing named so in this area: offers to search everywhere. */
    val nothingHere: DiveSiteBrowseState
        get() = searching(PagedStates.end(emptyList(), Unit))

    /** Nowhere to open: no location and no activity with a map. */
    val nowhere: DiveSiteBrowseState
        get() = browsing(ContentStates.loading(SiteArea())).copy(start = BrowseStart.Nowhere, hasArea = false, camera = null)

    /** Finding where the athlete is. */
    val locating: DiveSiteBrowseState
        get() = nowhere.copy(start = BrowseStart.Locating)

    /** Every shared sample and the browse states beyond content loading, for the native page's previews and snapshots. */
    val variants: Map<String, DiveSiteBrowseState>
        get() =
            all +
                mapOf(
                    "selected" to selected,
                    "tooWide" to tooWide,
                    "nameResults" to nameResults,
                    "nothingHere" to nothingHere,
                    "nowhere" to nowhere,
                    "locating" to locating,
                )
}

/** OpenStreetMap sites' radius, as the importer gives them (dive-sites/site-import). */
private const val OSM_RADIUS = 150

/** The Strait of Gubal: where the samples' map is centred. */
private const val GUBAL_LAT = 27.8136
private const val GUBAL_LNG = 33.9206
