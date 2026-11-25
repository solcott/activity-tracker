package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

/**
 * Fades out the start or end of a sideways [scroll]'s viewport, over [width], while there is more of it that way, so a column cut off at
 * the edge reads as more to scroll to. The iOS app's `sidewaysEdgeFades` draws the same.
 */
fun Modifier.edgeFades(scroll: ScrollState, width: Dp): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}
    .drawWithContent {
        drawContent()
        val fade = width.toPx()
        // Forward is toward the end: the right, or the left in a right-to-left layout.
        val ltr = layoutDirection == LayoutDirection.Ltr
        val moreLeft = if (ltr) scroll.canScrollBackward else scroll.canScrollForward
        val moreRight = if (ltr) scroll.canScrollForward else scroll.canScrollBackward
        if (moreLeft) {
            drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), endX = fade), blendMode = BlendMode.DstOut)
        }
        if (moreRight) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = size.width - fade, endX = size.width),
                blendMode = BlendMode.DstOut,
            )
        }
    }
