package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.RouteEnds
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.map_expand
import io.github.solcott.activitytracker.composeApp.resources.map_recenter
import io.github.solcott.activitytracker.data.activity.SegmentedPolyline
import io.github.solcott.activitytracker.location.TrackMath
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.camera.CameraFit
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.interaction.ClickResult
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

/** The margin left around the route when it's framed. */
private val FIT_MARGIN = 24.dp

@Composable
actual fun ActivityMap(
    polyline: String,
    modifier: Modifier,
    height: Dp,
    gaps: ImmutableList<Int>?,
    ends: RouteEnds?,
    onExpand: (() -> Unit)?,
    interactive: Boolean,
    highlight: String?,
    overlayTop: Dp,
) {
    // Previews, tests and screenshot tools draw the route without MapLibre, its native view or a remote style.
    if (LocalInspectionMode.current) {
        RoutePreview(polyline, modifier, height, gaps, ends, highlight)
        return
    }
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
    val highlightColor = MaterialTheme.colorScheme.tertiary
    val highlightJson =
        remember(highlight) {
            highlight
                ?.let { runCatching { TrackMath.decodePolyline(it) }.getOrNull() }
                ?.takeIf { it.size >= 2 }
                ?.let { lineJson(listOf(it)) }
        }
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
            // MapLibre Compose can't replace a source's data, so a new stretch makes a new source and layer, in this place in the order.
            key(highlightJson) {
                val stretch = rememberGeoJsonSource(GeoJsonData.JsonString(highlightJson ?: lineJson(emptyList())))
                LineLayer(
                    id = "route-highlight",
                    source = stretch,
                    color = const(highlightColor),
                    width = const(6.dp),
                    cap = const(LineCap.Round),
                    join = const(LineJoin.Round),
                )
            }
            markers?.let { RouteMarkerLayers(it) }
        }
    val fit =
        remember(overlayTop) {
            CameraFit(fitPadding = DpPadding(left = FIT_MARGIN, top = FIT_MARGIN + overlayTop, right = FIT_MARGIN, bottom = FIT_MARGIN))
        }
    // Jump straight to the route; cameraForBounds waits until the map has a viewport.
    LaunchedEffect(mapState, bounds, fit) { mapState.setCameraPosition(mapState.cameraForBounds(bounds, fit)) }
    val currentOnExpand by rememberUpdatedState(onExpand)
    val interactions =
        remember(interactive, onExpand != null) {
            when {
                // North-up, so the route reads as it does everywhere else.
                interactive ->
                    MapInteractions(MapInteractions.Standard) {
                        camera {
                            rotate { enabled = false }
                            pitch { enabled = false }
                        }
                    }
                // Static inside the detail list, so a drag scrolls the page instead of panning the map; a tap opens the enlarged one.
                onExpand != null ->
                    MapInteractions(MapInteractions.None) {
                        callbacks {
                            click {
                                onEvent {
                                    currentOnExpand?.invoke()
                                    ClickResult.Consume
                                }
                            }
                        }
                    }
                else -> MapInteractions.None
            }
        }
    val scope = rememberCoroutineScope()
    MaplibreMap(
        modifier.fillMaxWidth().height(height),
        state = mapState,
        interactions = interactions,
        overlay = {
            RouteMapOverlay(mapState) { buttonModifier ->
                when {
                    interactive ->
                        MapButton(
                            AppIcon.Recenter,
                            Res.string.map_recenter,
                            onClick = { scope.launch { mapState.animateCameraToBounds(bounds, fit) } },
                            buttonModifier,
                        )
                    onExpand != null ->
                        MapButton(AppIcon.Expand, Res.string.map_expand, onClick = { currentOnExpand?.invoke() }, buttonModifier)
                }
            }
        },
    )
}

/** A button over the map, on a disc so it reads over any tiles. */
@Composable
private fun MapButton(icon: AppIcon, label: StringResource, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = BUTTON_ALPHA), CircleShape)) {
        AppIconButton(icon, contentDescription = stringResource(label), onClick = onClick, tint = MaterialTheme.colorScheme.onSurface)
    }
}

private const val BUTTON_ALPHA = 0.9f

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
private fun pointsJson(points: List<Pair<Double, Double>>): String =
    points.joinToString(",", prefix = """{"type":"MultiPoint","coordinates":[""", postfix = "]}") { (lat, lng) -> "[$lng,$lat]" }

/** A GeoJSON MultiLineString of [pieces]; GeoJSON orders each position longitude first. */
private fun lineJson(pieces: List<List<Pair<Double, Double>>>): String =
    pieces.joinToString(",", prefix = """{"type":"MultiLineString","coordinates":[""", postfix = "]}") { piece ->
        piece.joinToString(",", prefix = "[", postfix = "]") { (lat, lng) -> "[$lng,$lat]" }
    }
