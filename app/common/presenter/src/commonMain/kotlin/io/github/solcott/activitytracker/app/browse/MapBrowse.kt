package io.github.solcott.activitytracker.app.browse

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.app.MapCameraRequest
import io.github.solcott.activitytracker.app.MapViewport
import io.github.solcott.activitytracker.app.segment.BrowseStart
import io.github.solcott.activitytracker.app.segment.NameScope
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.LocationTracker
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** How long typing must pause before a browse screen's name search runs, so each keystroke isn't a request. */
val NAME_SEARCH_DEBOUNCE = 300.milliseconds

/** How long browsing waits for a first location fix before opening at the latest activity instead. */
val LOCATION_TIMEOUT = 5.seconds

/**
 * What a map browse screen keeps between compositions (segments/segment-browse, dive-sites/site-browse): the map's area and where it was
 * sent, how browsing started, the item tapped on the map, the name search's scope, and the area's retry key. A presenter holds one in
 * `rememberRetained`, starts it with [locate], and hands it the events that only change it.
 */
@Stable
class MapBrowse(permission: Boolean?) {
    var scope by mutableStateOf(NameScope.ThisArea)
    var viewport by mutableStateOf<MapViewport?>(null)
        private set

    var selected by mutableStateOf<AtUri?>(null)
    var camera by mutableStateOf<MapCameraRequest?>(null)
        private set

    var start by mutableStateOf(BrowseStart.Locating)
        private set

    /** Whether location may be read: false where there's none, true once granted, null until the athlete is asked. */
    var permission by mutableStateOf(permission)
    var areaRetry by mutableIntStateOf(0)

    /** The map's area when the current name search's first page loaded, which its later pages search too. */
    var nameViewport: MapViewport? = null

    /** The map stopped and shows [shown]. */
    fun viewportChanged(shown: MapViewport) {
        viewport = shown
        // The athlete found somewhere themselves.
        if (start == BrowseStart.Nowhere && !shown.tooWide) start = BrowseStart.Placed
    }

    /**
     * Finds where to open, once permission is known: a first location fix within [LOCATION_TIMEOUT] when granted, else where the latest
     * activity with a map started, else nowhere.
     */
    suspend fun locate(tracker: LocationTracker, feed: FeedRepository) {
        val granted = permission ?: return
        if (start != BrowseStart.Locating) return
        val here =
            if (granted) withTimeoutOrNull(LOCATION_TIMEOUT) { runSuspendCatching { tracker.updates().first() }.getOrNull() } else null
        val at = here?.let { GeoPoint(it.latitude, it.longitude) } ?: feed.latestOwnStart()
        // The athlete may have moved the map meanwhile.
        if (start == BrowseStart.Locating) placeAt(at)
    }

    private fun placeAt(at: GeoPoint?) {
        if (at == null) {
            start = BrowseStart.Nowhere
        } else {
            camera = MapCameraRequest(id = (camera?.id ?: 0) + 1, lat = at.lat, lng = at.lng)
            start = BrowseStart.Placed
        }
    }

    /** The viewport an area browse loads: while no name is searched ([searching]), the map is close enough, and browsing has started. */
    fun areaRequest(searching: Boolean): MapViewport? = viewport?.takeIf { !searching && !it.tooWide && start != BrowseStart.Locating }

    /** The box a name search in [scope] covers: the map's in This area when it's close enough to search, else everywhere (null). */
    fun nameBox(viewport: MapViewport?): MapViewport? = viewport?.takeIf { scope == NameScope.ThisArea && !it.tooWide }

    companion object {
        /** Whether [tracker]'s location may be read, as a new [MapBrowse] starts: false where there's none, null until asked. */
        fun initialPermission(tracker: LocationTracker): Boolean? =
            when {
                !tracker.isSupported -> false
                tracker.hasPermission() -> true
                else -> null
            }
    }
}
