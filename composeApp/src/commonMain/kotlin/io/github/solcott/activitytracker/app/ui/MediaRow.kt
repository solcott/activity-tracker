package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.solcott.activitytracker.app.media.MediaItem
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.details_photo
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * An activity's route and photos side by side, as Strava shows them: the [map] (when it has a route) first, then each of [media], scrolled
 * sideways. With no photos the map takes the whole width; with neither, nothing shows. Tapping a photo calls [onOpen] with its index.
 */
@Composable
fun MediaRow(
    media: ImmutableList<MediaItem>,
    onOpen: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    /** Draws the route at the size its modifier and height give. */
    map: (@Composable (Modifier, Dp) -> Unit)? = null,
) {
    if (media.isEmpty()) {
        map?.invoke(modifier.fillMaxWidth().clip(TILE_SHAPE), height)
        return
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Peeks the next tile, so it reads as something to scroll.
        val mapWidth = maxWidth * MAP_SHARE
        LazyRow(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
            if (map != null) item(key = "map") { map(Modifier.width(mapWidth).clip(TILE_SHAPE), height) }
            itemsIndexed(media, key = { index, _ -> index }) { index, item -> MediaTile(item, height, onClick = { onOpen(index) }) }
        }
    }
}

@Composable
private fun MediaTile(item: MediaItem, height: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ratio = (item.aspectRatio ?: DEFAULT_RATIO).coerceIn(MIN_RATIO, MAX_RATIO)
    Box(
        modifier
            .width(height * ratio)
            .fillMaxHeight()
            .clip(TILE_SHAPE)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Image, onClick = onClick)
            .handCursor()
    ) {
        when (item) {
            is MediaItem.Photo ->
                AsyncImage(
                    item.url,
                    contentDescription = item.alt ?: stringResource(Res.string.details_photo),
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    contentScale = ContentScale.Crop,
                )
        }
    }
}

private val TILE_SHAPE = RoundedCornerShape(12.dp)
private val TILE_GAP = 8.dp
private const val MAP_SHARE = 0.8f
private const val DEFAULT_RATIO = 4f / 3
private const val MIN_RATIO = 0.6f
private const val MAX_RATIO = 1.8f
