package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.activity.SegmentedPolyline
import io.github.solcott.activitytracker.location.RouteCurve
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.map.MapSnapshotException
import org.maplibre.compose.map.MapSnapshotter
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.BoundingBox

/**
 * Draws feed cards' map images offscreen: the one place that calls MapLibre's snapshotter. Each theme keeps one snapshotter for the
 * process, so its style loads once, and each capture sets the route its style content reads before it captures.
 */
@OptIn(ExperimentalAtomicApi::class)
internal object RouteSnapshots {
    /** A route ready to draw: the line, its markers, the colour and where it lies. */
    private class Route(val routeJson: String, val markers: RouteMarkers<Pair<Double, Double>>?, val color: Color, val bounds: BoundingBox)

    private class Themed(style: String) {
        val route = mutableStateOf<Route?>(null)
        val snapshotter: MapSnapshotter by lazy {
            DefaultMapRuntime.instance.createSnapshotter(BaseStyle.Uri(style)) {
                route.value?.let { RouteLayers(it.routeJson, it.markers, it.color) }
            }
        }
    }

    private val light = Themed(MapStyles.LIGHT)
    private val dark = Themed(MapStyles.DARK)

    /** Held from setting a theme's route to the end of its capture, so a capture never draws another's route. */
    private val mutex = Mutex()

    private val failureLogged = AtomicBoolean(false)

    /**
     * [snapshot]'s route over the map, [width]×[height] pixels.
     *
     * @throws MapSnapshotException if it can't be drawn: no offscreen renderer, or a tile or the style failed to load. The first is logged.
     * @throws IllegalArgumentException if the polyline has fewer than two points.
     */
    suspend fun render(snapshot: RouteSnapshot, width: Int, height: Int): ImageBitmap {
        val points = TrackMath.decodePolyline(snapshot.polyline)
        require(points.size >= 2) { "A route needs two points" }
        val pieces = SegmentedPolyline.pieces(points, snapshot.gaps)
        val route =
            Route(
                // Drawn as curves through the stored points (feed/route-line-detail), as on the activity's page.
                routeJson = lineJson(pieces.map(RouteCurve::smooth)),
                markers = RouteMarkers.of(pieces),
                color = Color(snapshot.routeColor),
                bounds =
                    BoundingBox(
                        west = points.minOf { it.second },
                        south = points.minOf { it.first },
                        east = points.maxOf { it.second },
                        north = points.maxOf { it.first },
                    ),
            )
        val density = Density(snapshot.density)
        // The snapshotter lays maps out in whole dp and draws them at the density, so this is the size Coil asked for.
        val size = DpSize((width / snapshot.density).dp, (height / snapshot.density).dp)
        val themed = if (snapshot.dark) dark else light
        return mutex.withLock {
            Snapshot.withMutableSnapshot { themed.route.value = route }
            try {
                themed.snapshotter.capture(size) {
                    cameraPosition = fitCamera(route.bounds, size)
                    this.density = density
                }
            } catch (e: MapSnapshotException) {
                if (failureLogged.compareAndSet(expectedValue = false, newValue = true)) {
                    Logger.w(e) { "Couldn't draw a route's map image; feed cards show the line preview instead" }
                }
                throw e
            }
        }
    }
}
