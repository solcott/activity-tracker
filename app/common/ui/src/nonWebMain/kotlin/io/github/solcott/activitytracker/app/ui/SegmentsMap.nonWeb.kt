package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.github.solcott.activitytracker.app.MapCameraRequest
import io.github.solcott.activitytracker.app.MapSegmentLine
import io.github.solcott.activitytracker.app.MapViewport
import io.github.solcott.activitytracker.app.SegmentsMap
import io.github.solcott.activitytracker.app.segment.TrailRating
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.map.TrailRatingColors
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.map_zoom_in
import io.github.solcott.activitytracker.composeApp.resources.map_zoom_out
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.TrackMath
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.camera.CameraAnimation
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.expressions.ast.Expression
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.case
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.eq
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.switch
import org.maplibre.compose.expressions.value.ColorValue
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MapEvent
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.MapOverlay
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

actual val interactiveMapSupported: Boolean = true

@Suppress("LambdaParameterEventTrailing") // The expect declaration fixes the order: onHover, optional there, comes last.
@Composable
actual fun SegmentsMap(
    segments: ImmutableList<MapSegmentLine>,
    selected: String?,
    camera: MapCameraRequest?,
    onViewport: (MapViewport) -> Unit,
    onSelect: (String?) -> Unit,
    modifier: Modifier,
    hovered: String?,
    onHover: (String?) -> Unit,
    hoveredLines: ImmutableSet<String>,
    marker: GeoPoint?,
) {
    // Previews, tests and screenshot tools draw the lines without MapLibre, its native view or a remote style.
    if (LocalInspectionMode.current) {
        SegmentsPreview(segments, selected, modifier, hovered, hoveredLines, marker)
        return
    }
    // The legend of the levels the runs are drawn in (segments/trail-ratings), in the top-start corner, clear of the zoom buttons.
    val legend = remember(segments) { SegmentsMap.of(segments, selected = null).legend }
    Box(modifier) {
        SegmentsMapLibre(
            segments,
            selected,
            camera,
            onViewport,
            onSelect,
            hovered,
            onHover,
            hoveredLines,
            marker,
            Modifier.matchParentSize(),
        )
        RatingLegend(legend, Modifier.align(Alignment.TopStart).padding(MapOverlay.Spacing))
    }
}

@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod") // SegmentsMap's own parameters, and the layers in drawing order.
@Composable
private fun SegmentsMapLibre(
    segments: ImmutableList<MapSegmentLine>,
    selected: String?,
    camera: MapCameraRequest?,
    onViewport: (MapViewport) -> Unit,
    onSelect: (String?) -> Unit,
    hovered: String?,
    onHover: (String?) -> Unit,
    hoveredLines: ImmutableSet<String>,
    marker: GeoPoint?,
    modifier: Modifier = Modifier,
) {
    val runsJson = remember(segments) { featuresJson(segments.filterNot { it.lift }) }
    val liftsJson = remember(segments) { featuresJson(segments.filter { it.lift }) }
    // The selected and hovered runs: one small source, so a hover rebuilds a layer of a line or two.
    val highlighted = remember(selected, hovered, hoveredLines) { hoveredLines + listOfNotNull(selected, hovered) }
    val selectedJson = remember(segments, highlighted) { featuresJson(segments.filter { !it.lift && it.uri in highlighted }) }
    val ratings = TrailRatingColors.current()
    val plain = MaterialTheme.colorScheme.primary
    val liftColor = MaterialTheme.colorScheme.outline
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val markerRing = MaterialTheme.colorScheme.surface
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnHover by rememberUpdatedState(onHover)
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
                // Each run in its rating level's colour on that level's casing (segments/trail-ratings), by its feature's `level`.
                LineLayer(
                    id = "segments-runs-casing",
                    source = runs,
                    color = byLevel(plainColor = Color.White) { ratings.casing(it) },
                    width = const(LINE_WIDTH + CASING_EXTRA),
                    cap = const(LineCap.Round),
                    join = const(LineJoin.Round),
                )
                LineLayer(
                    id = RUNS_LAYER,
                    source = runs,
                    color = byLevel(plainColor = plain) { ratings.line(it) },
                    width = const(LINE_WIDTH),
                    cap = const(LineCap.Round),
                    join = const(LineJoin.Round),
                )
                // An Extreme run's thin centre line, which makes its black a double line.
                LineLayer(
                    id = "segments-runs-extreme",
                    source = runs,
                    filter = feature["level"].asString() eq const(TrailRating.EXTREME.name),
                    color = const(TrailRatingColors.EXTREME_CENTRE),
                    width = const(LINE_WIDTH / EXTREME_CENTRE_SHARE),
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
            // The spot a profile's readout marks (segments/path-profile), on top of every line.
            if (marker != null) {
                key(marker) {
                    MarkerLayer("segments-marker", persistentListOf(marker), MARKER_RADIUS, selectedColor, markerRing, MARKER_RING)
                }
            }
        }
    // Where the map last stopped and the last request it took, kept while its screen is, so a map made again on coming back opens
    // where it was left rather than where it was first sent.
    var applied by rememberSaveable { mutableIntStateOf(NO_REQUEST) }
    var stopped by rememberSaveable(stateSaver = CameraSaver) { mutableStateOf<CameraPosition?>(null) }
    // The map's size in dp, which a request to frame a box fits it to; read here so measuring recomposes and applies a waiting request.
    val density = LocalDensity.current
    var size by remember { mutableStateOf<DpSize?>(null) }
    val measured = size
    // Each request moves the map once, however often it recomposes; one framing a box waits until the map is measured.
    SideEffect(mapState, camera?.id) {
        val left = stopped
        when {
            camera != null && camera.id != applied && (camera.bounds == null || measured != null) -> {
                applied = camera.id
                val frame = camera.frame(measured?.width?.value?.toDouble() ?: 0.0, measured?.height?.value?.toDouble() ?: 0.0)
                mapState.setCameraPosition(frame.toCameraPosition())
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
                        // The run nearest a tap within a finger's width, or none, which clears the choice.
                        onUnhandled { event ->
                            scope.launch { currentOnSelect(mapState.runAt(event.screenOffset, TAP_SLOP)) }
                            ClickResult.Pass
                        }
                    }
                }
            }
        }
    // A mouse pointer's latest place over the map, or null once it leaves; one query runs at a time, for the latest place.
    val pointer = remember { Channel<DpOffset?>(Channel.CONFLATED) }
    LaunchedEffect(mapState) { pointer.receiveAsFlow().collectLatest { at -> currentOnHover(at?.let { mapState.runAt(it, HOVER_SLOP) }) } }
    MaplibreMap(
        modifier.onSizeChanged { size = with(density) { it.toSize().toDpSize() } }.pointerInput(Unit) { watchMouse(pointer) },
        state = mapState,
        interactions = interactions,
        overlay = {
            RouteMapOverlay(mapState) { buttonModifier ->
                // Zoom without gestures: for a mouse without a wheel, a simulator, or anyone who can't pinch.
                Column(buttonModifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MapButton(AppIcon.ZoomIn, Res.string.map_zoom_in, onClick = { scope.launch { mapState.zoomBy(1.0) } })
                    MapButton(AppIcon.ZoomOut, Res.string.map_zoom_out, onClick = { scope.launch { mapState.zoomBy(-1.0) } })
                }
            }
        },
    )
}

/**
 * Sends where a mouse moves over the map, unpressed, to [pointer], and null when it leaves. It watches the Initial pass and consumes
 * nothing, so the map's own gestures see every event as before; a finger or a drag hovers nothing.
 */
private suspend fun PointerInputScope.watchMouse(pointer: Channel<DpOffset?>) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull()?.takeIf { it.type == PointerType.Mouse }
            when {
                change == null -> Unit
                event.type == PointerEventType.Exit -> pointer.trySend(null)
                (event.type == PointerEventType.Enter || event.type == PointerEventType.Move) && !change.pressed ->
                    pointer.trySend(DpOffset(change.position.x.toDp(), change.position.y.toDp()))
            }
        }
    }
}

/**
 * The run whose line passes within [slop] of [at] on screen, or null. A box query, not a wide clear layer to catch taps: MapLibre leaves a
 * fully transparent layer out of its hit testing, and any opacity at all builds into a haze where many segments overlap.
 */
private suspend fun MapState.runAt(at: DpOffset, slop: Dp): String? =
    queryRenderedFeatures(DpRect(at.x - slop, at.y - slop, at.x + slop, at.y + slop), setOf(RUNS_LAYER)).firstNotNullOfOrNull {
        it.properties?.get("uri")?.jsonPrimitive?.content
    }

/** Zooms the map by [steps] levels about its centre, animated. */
internal suspend fun MapState.zoomBy(steps: Double) {
    val now = cameraPosition
    animateCamera(CameraUpdate(center = now.center, zoom = (now.zoom + steps).coerceIn(MIN_ZOOM, MAX_ZOOM)), CameraAnimation.Fly.Standard)
}

/** The box the map shows and its centre, in 1e-7 degrees; null while it has no viewport. */
private fun MapState.viewportNow(): MapViewport? {
    val bounds = getVisibleBounds() ?: return null
    val target = cameraPosition.center
    return MapViewport.ofDegrees(
        south = bounds.south,
        west = bounds.west,
        north = bounds.north,
        east = bounds.east,
        centerLat = target.latitude,
        centerLng = target.longitude,
    )
}

/** A GeoJSON FeatureCollection of [lines], each a LineString carrying its segment's URI; GeoJSON orders positions longitude first. */
private fun featuresJson(lines: List<MapSegmentLine>): String =
    lines.joinToString(",", prefix = """{"type":"FeatureCollection","features":[""", postfix = "]}") { line ->
        val points = TrackMath.decodePolylineOrNull(line.polyline).orEmpty()
        val coordinates = points.joinToString(",", prefix = "[", postfix = "]") { (lat, lng) -> "[$lng,$lat]" }
        // A URI is an at:// string of DID and record-key characters, so it needs no escaping in a JSON string.
        // The level is an enum name, "UNRATED" or "PLAIN" (not a trail), which need no escaping either.
        val level = if (!line.trail) PLAIN else line.rating?.name ?: UNRATED
        """{"type":"Feature","properties":{"uri":"${line.uri}","level":"$level"},"geometry":{"type":"LineString","coordinates":$coordinates}}"""
    }

/** Saves a camera position as its centre and zoom, all the browse map changes. */
private val CameraSaver =
    Saver<CameraPosition?, List<Double>>(
        save = { position -> position?.let { listOf(it.center.latitude, it.center.longitude, it.zoom) } },
        restore = { (lat, lng, zoom) -> CameraPosition(center = Position(lng, lat), zoom = zoom) },
    )

private const val NO_REQUEST = -1
private const val MIN_ZOOM = 0.0
private const val MAX_ZOOM = 20.0
private const val LIFT_DASH = 3
private const val LIFT_GAP = 2
private const val RUNS_LAYER = "segments-runs"
private val TAP_SLOP = 12.dp

/** A mouse pointer is precise: a few dp either side of a line hovers it. */
private val HOVER_SLOP = 4.dp

/** A run feature's `level` when nothing rates it. */
private const val UNRATED = "UNRATED"

/** A feature's `level` when it isn't a trail (a dive site's outline): drawn in the accent colour as before ratings. */
private const val PLAIN = "PLAIN"

/** An Extreme run's centre line is this share of its line's width, as [RatingSwatch] draws it. */
private const val EXTREME_CENTRE_SHARE = 3f

/**
 * A colour per run by its feature's `level`: [color] of each level's index (0 Easiest to 4 Extreme), of null for an unrated run, and
 * [plainColor] for a line that isn't a trail.
 */
private fun byLevel(plainColor: Color, color: (Int?) -> Color): Expression<ColorValue> =
    switch(
        feature["level"],
        TrailRating.entries.map { case(it.name, const(color(it.ordinal))) } + case(PLAIN, const(plainColor)),
        fallback = const(color(null)),
    )
