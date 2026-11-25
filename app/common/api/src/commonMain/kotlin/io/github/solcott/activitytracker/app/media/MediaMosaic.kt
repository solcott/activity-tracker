package io.github.solcott.activitytracker.app.media

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.text.AppText

/**
 * Which tiles an activity's media mosaic shows (feed/activity-page-layout): the route's map as the large tile with up to [MAX_BESIDE_MAP]
 * photos stacked beside it, or without a route the first photo as the large tile and up to [MAX_BESIDE_MAP] more beside it. The last tile
 * names how many photos it leaves out ([more]). Both UIs lay the tiles out; this decides what they hold.
 *
 * @property map the large tile is the route's map; otherwise it's the first photo.
 * @property photos how many photos show, counting the large one when it's a photo.
 * @property more how many photos don't fit, said on the last tile; 0 when they all show.
 */
@Immutable
data class MediaMosaic(val map: Boolean, val photos: Int, val more: Int) {
    /** How many photos stack beside the large tile. */
    val beside: Int
        get() = if (map) photos else photos - 1

    /** What the last tile says over its photo, "+2"; null when every photo shows. */
    val moreText: AppText?
        get() = if (more > 0) AppText.count("media_more", more) else null

    companion object {
        const val MAX_BESIDE_MAP = 2

        /** The mosaic of a route ([hasRoute]) and [photoCount] photos; null with neither, when the page shows no mosaic. */
        fun of(hasRoute: Boolean, photoCount: Int): MediaMosaic? {
            if (!hasRoute && photoCount == 0) return null
            val shown = photoCount.coerceAtMost(if (hasRoute) MAX_BESIDE_MAP else MAX_BESIDE_MAP + 1)
            return MediaMosaic(map = hasRoute, photos = shown, more = photoCount - shown)
        }
    }
}
