package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.RouteEnds
import io.github.solcott.activitytracker.data.activity.SegmentedPolyline
import io.github.solcott.activitytracker.location.TrackMath
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.BoundingBox

private val FIT_PADDING = DpPadding(left = 24.dp, top = 24.dp, right = 24.dp, bottom = 24.dp)

@Composable
actual fun ActivityMap(polyline: String, modifier: Modifier, height: Dp, gaps: ImmutableList<Int>?, ends: RouteEnds?) {
    // (lat, lng) pairs, like RoutePreview.
    val points = remember(polyline) { runCatching { TrackMath.decodePolyline(polyline) }.getOrDefault(emptyList()) }
    if (points.size < 2) return
    val pieces = remember(points, gaps) { SegmentedPolyline.pieces(points, gaps) }
    val routeJson = remember(pieces) { lineJson(pieces) }
    val markers = remember(pieces, ends) { RouteMarkers.of(pieces)?.shownAt(ends) }
    val bounds =
        remember(points) {
            BoundingBox(
                west = points.minOf { it.second },
                south = points.minOf { it.first },
                east = points.maxOf { it.second },
                north = points.maxOf { it.first },
            )
        }
    val color = MaterialTheme.colorScheme.primary
    val style = BaseStyle.Uri(if (isSystemInDarkTheme()) MapStyles.DARK else MapStyles.LIGHT)
    val mapState =
        rememberMapState(baseStyle = style) {
            val route = rememberGeoJsonSource(GeoJsonData.JsonString(routeJson))
            LineLayer(
                id = "route-casing",
                source = route,
                color = const(Color.White),
                width = const(7.dp),
                cap = const(LineCap.Round),
                join = const(LineJoin.Round),
            )
            LineLayer(
                id = "route",
                source = route,
                color = const(color),
                width = const(4.dp),
                cap = const(LineCap.Round),
                join = const(LineJoin.Round),
            )
            markers?.let { RouteMarkerLayers(it, route = color) }
        }
    // Jump straight to the route; cameraForBounds waits until the map has a viewport.
    LaunchedEffect(mapState, bounds) { mapState.setCameraPosition(mapState.cameraForBounds(bounds, CameraFit(fitPadding = FIT_PADDING))) }
    // Static inside the detail list, so a drag scrolls the page instead of panning the map.
    MaplibreMap(
        modifier.fillMaxWidth().height(height),
        state = mapState,
        interactions = MapInteractions.None,
        overlay = { RouteMapOverlay(mapState) },
    )
}

/** [markers] above the route: break ends ringed in [route], then the start, then the end on top, as `RoutePreview` draws them. */
@Composable
private fun RouteMarkerLayers(markers: RouteMarkers<Pair<Double, Double>>, route: Color) {
    MarkerLayer("route-breaks", markers.breakEnds, RouteMarkers.BREAK_RADIUS, fill = Color.White, ring = route)
    // A hidden end still gets its (empty) layer, so the layers keep their order when it shows.
    val start = listOfNotNull(markers.start).toImmutableList()
    val end = listOfNotNull(markers.end).toImmutableList()
    MarkerLayer("route-start", start, RouteMarkers.END_RADIUS, fill = RouteMarkers.START_COLOR, ring = Color.White)
    MarkerLayer("route-end", end, RouteMarkers.END_RADIUS, fill = RouteMarkers.END_COLOR, ring = Color.White)
}

/** A dot at each of [at] of [radius] (MapLibre strokes outside a circle's radius, so the ring is taken off it). */
@Composable
private fun MarkerLayer(id: String, at: ImmutableList<Pair<Double, Double>>, radius: Dp, fill: Color, ring: Color) {
    val source = rememberGeoJsonSource(GeoJsonData.JsonString(pointsJson(at)))
    CircleLayer(
        id = id,
        source = source,
        color = const(fill),
        radius = const(radius - RouteMarkers.RING),
        strokeColor = const(ring),
        strokeWidth = const(RouteMarkers.RING),
    )
}

/** A GeoJSON MultiPoint of [points], longitude first. */
private fun pointsJson(points: List<Pair<Double, Double>>): String =
    points.joinToString(",", prefix = """{"type":"MultiPoint","coordinates":[""", postfix = "]}") { (lat, lng) -> "[$lng,$lat]" }

/** A GeoJSON MultiLineString of [pieces]; GeoJSON orders each position longitude first. */
private fun lineJson(pieces: List<List<Pair<Double, Double>>>): String =
    pieces.joinToString(",", prefix = """{"type":"MultiLineString","coordinates":[""", postfix = "]}") { piece ->
        piece.joinToString(",", prefix = "[", postfix = "]") { (lat, lng) -> "[$lng,$lat]" }
    }
