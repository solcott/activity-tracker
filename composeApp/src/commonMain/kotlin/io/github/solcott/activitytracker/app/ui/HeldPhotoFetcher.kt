package io.github.solcott.activitytracker.app.ui

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import io.github.solcott.activitytracker.data.activity.HeldPhotos
import io.github.solcott.activitytracker.data.activity.photoCid
import kotlinx.coroutines.flow.MutableStateFlow
import okio.Buffer

/**
 * Loads a photo shown under [HeldPhotos.SCHEME] from the bytes the signed-in session uploaded ([Factory.session]), never from the network.
 * Without them (signed out, or uploaded before the app restarted) the load fails at once and the photo's tile stays an empty placeholder.
 */
class HeldPhotoFetcher(private val cid: String, private val held: HeldPhotos?, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val photo = held?.get(cid) ?: error("Photo $cid isn't held on this device")
        return SourceFetchResult(
            source = ImageSource(Buffer().write(photo.bytes), options.fileSystem),
            mimeType = photo.mimeType,
            dataSource = DataSource.MEMORY,
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == HeldPhotos.SCHEME) HeldPhotoFetcher(photoCid(data.toString()), session.value, options) else null

        companion object {
            /**
             * The signed-in session's [HeldPhotos], swapped as sessions come and go. Process-wide, like the singleton image loader, which
             * is built once and outlives both the sessions and the composition that first set it up.
             */
            val session = MutableStateFlow<HeldPhotos?>(null)
        }
    }
}
