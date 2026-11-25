package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.media.MediaItem
import io.github.solcott.activitytracker.app.media.MediaMosaic
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.moderation.BlurReason
import kotlinx.collections.immutable.ImmutableList

/**
 * An activity's route and photos as one mosaic (feed/activity-page-layout): the large tile (the [map], or the first photo without a route)
 * two thirds wide, and the photos [mosaic] picks stacked beside it, the last saying how many more there are. Tapping a photo calls [onOpen]
 * with its index; the map opens itself. Draws nothing for a null [mosaic].
 */
@Composable
@Suppress("LongParameterList") // The tiles, and how a label and a takedown show on the photos.
fun MediaMosaicUi(
    mosaic: MediaMosaic?,
    media: ImmutableList<MediaItem>,
    onOpen: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = MOSAIC_HEIGHT,
    /** Draws the route at the size its modifier and height give. */
    map: (@Composable (Modifier, Dp) -> Unit)? = null,
    blur: BlurReason? = null,
    revealed: Boolean = false,
    onReveal: () -> Unit = {},
    takenDown: Boolean = false,
) {
    if (mosaic == null) return
    // The count shows over the last photo once it can be seen: a blurred one keeps its reveal.
    val more = mosaic.moreText.takeIf { blur == null || revealed }
    // Each photo the mosaic shows, by its index among the activity's media; the last carries the count.
    val tiles = (0 until mosaic.photos).mapNotNull { i -> media.getOrNull(i)?.let { i to it } }
    val last = tiles.lastOrNull()?.first
    Box(modifier.fillMaxWidth().height(height)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(MOSAIC_GAP)) {
            val large = if (mosaic.beside > 0) Modifier.weight(LARGE_SHARE) else Modifier.weight(1f)
            val stacked = if (mosaic.map) tiles else tiles.drop(1)
            if (mosaic.map) {
                map?.invoke(large.fillMaxSize().clip(MOSAIC_TILE_SHAPE).background(MaterialTheme.colorScheme.surfaceVariant), height)
            } else {
                tiles.firstOrNull()?.let { (i, item) ->
                    MosaicPhoto(item, { onOpen(i) }, more.takeIf { i == last }, large.fillMaxSize(), blur, revealed, onReveal, takenDown)
                }
            }
            if (stacked.isNotEmpty()) {
                Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MOSAIC_GAP)) {
                    stacked.forEach { (i, item) ->
                        val tile = Modifier.weight(1f).fillMaxWidth()
                        MosaicPhoto(item, { onOpen(i) }, more.takeIf { i == last }, tile, blur, revealed, onReveal, takenDown)
                    }
                }
            }
        }
    }
}

/** One photo tile of the mosaic, cropped to its shape, with [more] ("+2") over it on a neutral scrim. */
@Composable
@Suppress("LongParameterList") // The photo, its count, and how a label and a takedown show.
private fun MosaicPhoto(
    item: MediaItem,
    onClick: () -> Unit,
    more: AppText?,
    modifier: Modifier = Modifier,
    blur: BlurReason? = null,
    revealed: Boolean = false,
    onReveal: () -> Unit = {},
    takenDown: Boolean = false,
) {
    Box(modifier.clip(MOSAIC_TILE_SHAPE)) {
        MediaPhoto(item, onClick, Modifier.fillMaxSize(), blur, revealed, onReveal, takenDown)
        if (more != null) {
            Box(Modifier.matchParentSize().background(MORE_SCRIM), contentAlignment = Alignment.Center) {
                Text(more.string(), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** A phone's mosaic height; a wide window's side pane uses the same. */
val MOSAIC_HEIGHT = 300.dp
private val MOSAIC_GAP = 8.dp
private val MOSAIC_TILE_SHAPE = RoundedCornerShape(16.dp)
private const val LARGE_SHARE = 2f
private val MORE_SCRIM = Color.Black.copy(alpha = 0.45f)
