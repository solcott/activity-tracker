package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.MapCameraRequest
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.MapViewport
import io.github.solcott.activitytracker.location.TrackMath
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MapEvent
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

actual val interactiveMapSupported: Boolean = true

@Composable
actual fun SegmentsMap(
    segments: ImmutableList<MapSegmentLine>,
    selected: String?,
    camera: MapCameraRequest?,
    onViewport: (MapViewport) -> Unit,
    onSelect: (String?) -> Unit,
    modifier: Modifier,
) {
    // Previews, tests and screenshot tools draw the lines without MapLibre, its native view or a remote style.
    if (LocalInspectionMode.current) {
        SegmentsPreview(segments, selected, modifier)
        return
    }
    val runsJson = remember(segments) { featuresJson(segments.filterNot { it.lift }) }
    val liftsJson = remember(segments) { featuresJson(segments.filter { it.lift }) }
    val selectedJson = remember(segments, selected) { featuresJson(segments.filter { !it.lift && it.uri == selected }) }
    val runColor = MaterialTheme.colorScheme.primary
    val liftColor = MaterialTheme.colorScheme.outline
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnViewport by rememberUpdatedState(onViewport)
    val style = BaseStyle.Uri(if (isSystemInDarkTheme()) MapStyles.DARK else MapStyles.LIGHT)
    val mapState =
        rememberMapState(baseStyle = style) {
            // MapLibre Compose can't replace a source's data, so new lines make a new source and layer, in this place in the order.
            key(liftsJson) {
                LineLayer(
                    id = "segments-lifts",
                    source = rememberGeoJsonSource(GeoJsonData.JsonString(liftsJson)),
                    color = const(liftColor),
                    width = const(2.dp),
                    // MapLibre measures dashes in line widths.
                    dasharray = const(listOf(LIFT_DASH, LIFT_GAP)),
                    cap = const(LineCap.Butt),
                )
            }
            key(runsJson) {
                val runs = rememberGeoJsonSource(GeoJsonData.JsonString(runsJson))
                LineLayer(
                    id = "segments-runs-casing",
                    source = runs,
                    color = const(androidx.compose.ui.graphics.Color.White),
                    width = const(6.dp),
                    cap = const(LineCap.Round),
                    join = const(LineJoin.Round),
                )
                LineLayer(
                    id = RUNS_LAYER,
                    source = runs,
                    color = const(runColor),
                    width = const(4.dp),
                    cap = const(LineCap.Round),
                    join = const(LineJoin.Round),
                )
            }
            key(selectedJson) {
                LineLayer(
                    id = "segments-selected",
                    source = rememberGeoJsonSource(GeoJsonData.JsonString(selectedJson)),
                    color = const(selectedColor),
                    width = const(6.dp),
                    cap = const(LineCap.Round),
                    join = const(LineJoin.Round),
                )
            }
        }
    // Where the map last stopped and the last request it took, kept while its screen is, so a map made again on coming back opens
    // where it was left rather than where it was first sent.
    var applied by rememberSaveable { mutableIntStateOf(NO_REQUEST) }
    var stopped by rememberSaveable(stateSaver = CameraSaver) { mutableStateOf<CameraPosition?>(null) }
    // Each request moves the map once, however often it recomposes.
    SideEffect(mapState, camera?.id) {
        val left = stopped
        when {
            camera != null && camera.id != applied -> {
                applied = camera.id
                mapState.setCameraPosition(CameraPosition(center = Position(camera.lng, camera.lat), zoom = camera.zoom))
            }
            left != null -> mapState.setCameraPosition(left)
        }
    }
    // What the map shows, once it has a viewport and then whenever the camera stops.
    LaunchedEffect(mapState) {
        mapState.awaitViewport()
        mapState.viewportNow()?.let(currentOnViewport)
        mapState.events.collect { event ->
            if (event is MapEvent.CameraMoveEnded) {
                stopped = mapState.cameraPosition
                mapState.viewportNow()?.let(currentOnViewport)
            }
        }
    }
    val scope = rememberCoroutineScope()
    val interactions =
        remember(mapState) {
            // North-up, like every other map in the app.
            MapInteractions(MapInteractions.Standard) {
                camera {
                    rotate { enabled = false }
                    pitch { enabled = false }
                }
                callbacks {
                    click {
                        // The run nearest a tap within a finger's width, or none, which clears the choice. A box query, not a wide clear
                        // layer to catch taps: MapLibre leaves a fully transparent layer out of its hit testing, and any opacity at all
                        // builds
                        // into a haze where many segments overlap.
                        onUnhandled { event ->
                            val at = event.screenOffset
                            val box = DpRect(at.x - TAP_SLOP, at.y - TAP_SLOP, at.x + TAP_SLOP, at.y + TAP_SLOP)
                            scope.launch {
                                val hit = mapState.queryRenderedFeatures(box, setOf(RUNS_LAYER))
                                currentOnSelect(hit.firstNotNullOfOrNull { it.properties?.get("uri")?.jsonPrimitive?.content })
                            }
                            ClickResult.Pass
                        }
                    }
                }
            }
        }
    MaplibreMap(modifier, state = mapState, interactions = interactions, overlay = { RouteMapOverlay(mapState) })
}

/** The box the map shows and its centre, in 1e-7 degrees; null while it has no viewport. */
private fun MapState.viewportNow(): MapViewport? {
    val bounds = getVisibleBounds() ?: return null
    val target = cameraPosition.center
    return MapViewport(
        minLat = e7(bounds.south),
        minLng = e7(bounds.west),
        maxLat = e7(bounds.north),
        maxLng = e7(bounds.east),
        centerLat = e7(target.latitude),
        centerLng = e7(target.longitude),
    )
}

private fun e7(degrees: Double): Int = (degrees * E7).roundToInt()

/** A GeoJSON FeatureCollection of [lines], each a LineString carrying its segment's URI; GeoJSON orders positions longitude first. */
private fun featuresJson(lines: List<MapSegmentLine>): String =
    lines.joinToString(",", prefix = """{"type":"FeatureCollection","features":[""", postfix = "]}") { line ->
        val points = TrackMath.decodePolylineOrNull(line.polyline).orEmpty()
        val coordinates = points.joinToString(",", prefix = "[", postfix = "]") { (lat, lng) -> "[$lng,$lat]" }
        // A URI is an at:// string of DID and record-key characters, so it needs no escaping in a JSON string.
        """{"type":"Feature","properties":{"uri":"${line.uri}"},"geometry":{"type":"LineString","coordinates":$coordinates}}"""
    }

/** Saves a camera position as its centre and zoom, all the browse map changes. */
private val CameraSaver =
    Saver<CameraPosition?, List<Double>>(
        save = { position -> position?.let { listOf(it.center.latitude, it.center.longitude, it.zoom) } },
        restore = { (lat, lng, zoom) -> CameraPosition(center = Position(lng, lat), zoom = zoom) },
    )

private const val NO_REQUEST = -1
private const val E7 = 1e7
private const val LIFT_DASH = 3
private const val LIFT_GAP = 2
private const val RUNS_LAYER = "segments-runs"
private val TAP_SLOP = 12.dp
