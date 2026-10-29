package io.github.solcott.activitytracker.app.ui

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.Dimension
import coil3.size.Size
import kotlinx.collections.immutable.ImmutableList
import okio.Buffer
import okio.IOException

/** Bumped whenever the drawing changes ([RouteLayers], the framing), so images drawn before it are drawn again. */
internal const val ROUTE_IMAGE_VERSION = 1

/**
 * A feed card's map image of [polyline], broken at [gaps], in the [dark] or light map style with the route in [routeColor] (ARGB), for a
 * screen of [density]. Coil loads it through [RouteSnapshotFetcher] at the size the card lays it out.
 */
internal data class RouteSnapshot(
    val polyline: String,
    val gaps: ImmutableList<Int>?,
    val dark: Boolean,
    val routeColor: Int,
    val density: Float,
) {
    /** The memory and disk cache key at [size]: everything that changes the image. */
    fun cacheKey(size: Size): String {
        val style = if (dark) MapStyles.DARK else MapStyles.LIGHT
        return "route-map:v$ROUTE_IMAGE_VERSION:$style:$routeColor:$density:${size.width}x${size.height}:${gaps.orEmpty()}:$polyline"
    }
}

internal class RouteSnapshotKeyer : Keyer<RouteSnapshot> {
    override fun key(data: RouteSnapshot, options: Options): String = data.cacheKey(options.size)
}

/**
 * Draws a [RouteSnapshot] with [RouteSnapshots] and keeps it as a PNG in the image loader's disk cache, which Coil leaves to fetchers. A
 * failure fails the request, and the card keeps its line preview.
 */
internal class RouteSnapshotFetcher(
    private val data: RouteSnapshot,
    private val options: Options,
    private val imageLoader: ImageLoader,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val width = (options.size.width as? Dimension.Pixels)?.px ?: error("A map image needs a fixed width")
        val height = (options.size.height as? Dimension.Pixels)?.px ?: error("A map image needs a fixed height")
        val key = data.cacheKey(options.size)
        val disk = imageLoader.diskCache
        disk?.openSnapshot(key)?.let { snapshot ->
            return SourceFetchResult(ImageSource(snapshot.data, disk.fileSystem, key, snapshot), PNG, DataSource.DISK)
        }
        val png = RouteSnapshots.render(data, width, height).encodePng()
        disk?.openEditor(key)?.let { editor ->
            try {
                disk.fileSystem.write(editor.data) { write(png) }
                editor.commit()
            } catch (e: IOException) {
                editor.abort()
                throw e
            }
        }
        return SourceFetchResult(ImageSource(Buffer().write(png), options.fileSystem), PNG, DataSource.MEMORY)
    }

    class Factory : Fetcher.Factory<RouteSnapshot> {
        override fun create(data: RouteSnapshot, options: Options, imageLoader: ImageLoader): Fetcher =
            RouteSnapshotFetcher(data, options, imageLoader)
    }

    private companion object {
        const val PNG = "image/png"
    }
}
