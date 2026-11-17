package io.github.solcott.activitytracker.app.browse

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.app.MapBounds
import io.github.solcott.activitytracker.app.MapCameraRequest
import io.github.solcott.activitytracker.app.MapViewport
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.location.GeoPoint
import kotlin.math.roundToInt

/**
 * A map of many lines and a list that share a selection (segments/segment-browse, segments/path-page, dive-sites/site-browse): the map's
 * area, the row selected on either, and where the map was last sent. Hovering is each UI's own. A presenter holds one in `rememberRetained`
 * and hands it the events that only change it.
 */
@Stable
open class MapSelection {
    var viewport by mutableStateOf<MapViewport?>(null)
        protected set

    var selected by mutableStateOf<AtUri?>(null)
    var camera by mutableStateOf<MapCameraRequest?>(null)
        private set

    /** The map stopped and shows [shown]. */
    open fun viewportChanged(shown: MapViewport) {
        viewport = shown
    }

    /**
     * Selects the row [uri] whose line or place is [points] (null when it can't lie off the map), framing them when none is in the map's
     * view.
     */
    fun selectRow(uri: AtUri, points: List<GeoPoint>?) {
        selected = uri
        val shown = viewport ?: return
        if (points.isNullOrEmpty() || points.any { shown.holds(it) }) return
        camera = MapCameraRequest.fit(nextCameraId(), MapBounds.of(points))
    }

    /** Sends the map to frame [points] whole. */
    fun frame(points: List<GeoPoint>) {
        if (points.isNotEmpty()) camera = MapCameraRequest.fit(nextCameraId(), MapBounds.of(points))
    }

    /** Sends the map to centre on [at]. */
    protected fun centreOn(at: GeoPoint) {
        camera = MapCameraRequest(id = nextCameraId(), lat = at.lat, lng = at.lng)
    }

    private fun nextCameraId(): Int = (camera?.id ?: 0) + 1

    private companion object {
        const val E7 = 1e7

        /** Whether [point] lies in this viewport's box. */
        fun MapViewport.holds(point: GeoPoint): Boolean =
            (point.lat * E7).roundToInt() in minLat..maxLat && (point.lng * E7).roundToInt() in minLng..maxLng
    }
}
