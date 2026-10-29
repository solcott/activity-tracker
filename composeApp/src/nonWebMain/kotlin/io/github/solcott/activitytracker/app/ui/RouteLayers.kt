package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource

/**
 * An activity's route as the map draws it, shared by the live map and the feed's map images: [routeJson] (a [lineJson]) in [color] on a
 * white casing, then [above] (the live map's highlighted stretch), then [markers] on top.
 */
@Composable
internal fun RouteLayers(
    routeJson: String,
    markers: RouteMarkers<Pair<Double, Double>>?,
    color: Color,
    above: @Composable () -> Unit = {},
) {
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
    above()
    markers?.let { RouteMarkerLayers(it) }
}

/**
 * [markers] above the route, as `RoutePreview` draws them: each break's dashed connector, its stop and resume markers, then the start, then
 * the end on top.
 */
@Composable
private fun RouteMarkerLayers(markers: RouteMarkers<Pair<Double, Double>>) {
    val connectorJson = remember(markers.breaks) { lineJson(markers.breaks.map { listOf(it.stop, it.resume) }) }
    val connectors = rememberGeoJsonSource(GeoJsonData.JsonString(connectorJson))
    LineLayer(
        id = "route-break-connectors",
        source = connectors,
        color = const(RouteMarkers.BREAK_COLOR),
        width = const(RouteMarkers.CONNECTOR_WIDTH),
        // MapLibre measures dashes in line widths.
        dasharray =
            const(
                listOf(
                    RouteMarkers.CONNECTOR_DASH / RouteMarkers.CONNECTOR_WIDTH,
                    RouteMarkers.CONNECTOR_SPACE / RouteMarkers.CONNECTOR_WIDTH,
                )
            ),
        cap = const(LineCap.Butt),
    )
    val stops = remember(markers.breaks) { markers.breaks.map { it.stop }.toImmutableList() }
    val resumes = remember(markers.breaks) { markers.breaks.map { it.resume }.toImmutableList() }
    MarkerLayer("route-break-stops", stops, RouteMarkers.BREAK_RADIUS, fill = RouteMarkers.BREAK_COLOR, ring = Color.White)
    MarkerLayer(
        "route-break-resumes",
        resumes,
        RouteMarkers.BREAK_RADIUS,
        fill = Color.White,
        ring = RouteMarkers.BREAK_COLOR,
        ringWidth = RouteMarkers.RESUME_RING,
    )
    // A hidden end still gets its (empty) layer, so the layers keep their order when it shows.
    val start = listOfNotNull(markers.start).toImmutableList()
    val end = listOfNotNull(markers.end).toImmutableList()
    MarkerLayer("route-start", start, RouteMarkers.END_RADIUS, fill = RouteMarkers.START_COLOR, ring = Color.White)
    MarkerLayer("route-end", end, RouteMarkers.END_RADIUS, fill = RouteMarkers.END_COLOR, ring = Color.White)
}

/** A dot at each of [at] of [radius], ringed [ringWidth] wide (MapLibre strokes outside a circle's radius, so the ring is taken off it). */
@Composable
private fun MarkerLayer(
    id: String,
    at: ImmutableList<Pair<Double, Double>>,
    radius: Dp,
    fill: Color,
    ring: Color,
    ringWidth: Dp = RouteMarkers.RING,
) {
    val source = rememberGeoJsonSource(GeoJsonData.JsonString(pointsJson(at)))
    CircleLayer(
        id = id,
        source = source,
        color = const(fill),
        radius = const(radius - ringWidth),
        strokeColor = const(ring),
        strokeWidth = const(ringWidth),
    )
}

/** A GeoJSON MultiPoint of [points], longitude first. */
internal fun pointsJson(points: List<Pair<Double, Double>>): String =
    points.joinToString(",", prefix = """{"type":"MultiPoint","coordinates":[""", postfix = "]}") { (lat, lng) -> "[$lng,$lat]" }

/** A GeoJSON MultiLineString of [pieces]; GeoJSON orders each position longitude first. */
internal fun lineJson(pieces: List<List<Pair<Double, Double>>>): String =
    pieces.joinToString(",", prefix = """{"type":"MultiLineString","coordinates":[""", postfix = "]}") { piece ->
        piece.joinToString(",", prefix = "[", postfix = "]") { (lat, lng) -> "[$lng,$lat]" }
    }
