package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.record.DepthPreviewPoint
import kotlinx.collections.immutable.ImmutableList

/** A dive's depth over time: the surface along the top, deeper further down, filled beneath the surface line. */
@Composable
fun DepthProfile(profile: ImmutableList<DepthPreviewPoint>, description: String, modifier: Modifier = Modifier, height: Dp = 160.dp) {
    if (profile.size < 2) return
    val line = MaterialTheme.colorScheme.primary
    val fill = line.copy(alpha = FILL_ALPHA)
    val surface = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        val first = profile.first().seconds
        val span = (profile.last().seconds - first).coerceAtLeast(1).toFloat()
        val deepest = profile.maxOf { it.depthMeters }.coerceAtLeast(1.0).toFloat()
        fun x(p: DepthPreviewPoint) = (p.seconds - first) / span * size.width
        fun y(p: DepthPreviewPoint) = p.depthMeters.toFloat() / deepest * size.height
        val path =
            Path().apply {
                moveTo(x(profile.first()), y(profile.first()))
                profile.drop(1).forEach { lineTo(x(it), y(it)) }
            }
        val area =
            Path().apply {
                addPath(path)
                lineTo(x(profile.last()), 0f)
                lineTo(x(profile.first()), 0f)
                close()
            }
        drawPath(area, fill)
        drawLine(surface, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx())
        drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

private const val FILL_ALPHA = 0.2f
