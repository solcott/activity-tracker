package io.github.solcott.activitytracker.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.solcott.activitytracker.app.TileFit
import io.github.solcott.activitytracker.location.GeoPoint

/**
 * Where each point goes on a canvas of [size] to fit all of [points] inside [pad]: scaled together, centred, north up, with longitudes
 * shrunk by the cosine of the first point's latitude so shapes keep their proportions (`TileFit`, which the native route tile places with
 * too). Null for no points. The map stand-ins draw routes and segments with it ([RoutePreview], [SegmentsPreview]).
 */
internal fun canvasFit(points: List<GeoPoint>, size: Size, pad: Float): ((GeoPoint) -> Offset)? {
    val fit = TileFit.of(points, size.width.toDouble(), size.height.toDouble(), pad.toDouble()) ?: return null
    return { point -> fit.place(point).let { Offset(it.x.toFloat(), it.y.toFloat()) } }
}
