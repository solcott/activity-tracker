package io.github.solcott.activitytracker.app.ui.chart

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.multiplatform.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.common.Fill
import com.patrykandpatrick.vico.multiplatform.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.multiplatform.common.component.rememberTextComponent
import com.patrykandpatrick.vico.multiplatform.common.data.ExtraStore
import kotlinx.collections.immutable.ImmutableList

/** [dots] as Vico points on a line in [color]: filled on the current value, hollow (the surface inside the line's colour) on the rest. */
@Composable
internal fun rememberDots(dots: ImmutableList<SeriesDot>, color: Color): LineCartesianLayer.PointProvider? {
    val filled =
        LineCartesianLayer.Point(
            rememberShapeComponent(Fill(color), CircleShape, strokeFill = Fill(color), strokeThickness = DOT_STROKE),
            size = FILLED_DOT,
        )
    val hollow =
        LineCartesianLayer.Point(
            rememberShapeComponent(
                Fill(MaterialTheme.colorScheme.surface),
                CircleShape,
                strokeFill = Fill(color),
                strokeThickness = DOT_STROKE,
            ),
            size = HOLLOW_DOT,
        )
    return remember(dots, filled, hollow) { if (dots.isEmpty()) null else SeriesDots(dots, filled, hollow) }
}

/** Each of [dots]' labels above its point, the current value's in the text colour and the rest muted. */
@Composable
internal fun rememberDotLabels(dots: ImmutableList<SeriesDot>): PointLabels {
    val style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
    val current = rememberTextComponent(style.copy(color = MaterialTheme.colorScheme.onSurface))
    val past = rememberTextComponent(style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
    return remember(dots, current, past) {
        PointLabels(
            dots.mapNotNull { dot -> dot.label?.let { PointLabel(dot.x, dot.y, it, if (dot.filled) current else past) } },
            gap = LABEL_GAP,
        )
    }
}

/** The filled or hollow point at each dot's x; none elsewhere (a threshold's run-on to today). */
@Immutable
private data class SeriesDots(
    private val dots: ImmutableList<SeriesDot>,
    private val filled: LineCartesianLayer.Point,
    private val hollow: LineCartesianLayer.Point,
) : LineCartesianLayer.PointProvider {
    private val byX = dots.associate { it.x to it.filled }

    override fun getPoint(entry: LineCartesianLayerModel.Entry, seriesIndex: Int, extraStore: ExtraStore) =
        if (seriesIndex != 0) null
        else
            when (byX[entry.x]) {
                true -> filled
                false -> hollow
                null -> null
            }

    override fun getLargestPoint(extraStore: ExtraStore) = filled
}

private val FILLED_DOT = 12.dp
private val HOLLOW_DOT = 9.dp
private val DOT_STROKE = 2.dp
private val LABEL_GAP = 4.dp

/** Room at each side of a chart with dots, so one on the first or last point isn't cut off. */
internal val DOT_ROOM = 8.dp
