package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import io.github.solcott.activitytracker.app.edit.PickedPhoto
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString

/**
 * Picking photos on the device: [choose] opens the library, and [take] the camera where the platform has one (null on desktop and web).
 * Each picked photo is compressed ([compressPhoto]), which drops its metadata, before it's handed on; [preparing] while that runs.
 */
@Stable
class PhotoPicks internal constructor(private val busy: State<Boolean>, private val onChoose: () -> Unit, val take: (() -> Unit)?) {
    val preparing: Boolean
        get() = busy.value

    fun choose() = onChoose()
}

/**
 * [PhotoPicks] that offer at most [maxItems] from the library at once and send what was picked, compressed, to [onPick]; nothing when every
 * pick failed to decode or the athlete cancelled.
 */
@Composable
fun rememberPhotoPicks(maxItems: Int, onPick: (ImmutableList<PickedPhoto>) -> Unit): PhotoPicks {
    val scope = rememberCoroutineScope()
    val sink by rememberUpdatedState(onPick)
    val busy = remember { mutableStateOf(false) }
    fun prepare(files: List<PlatformFile>) {
        if (files.isEmpty()) return
        scope.launch {
            busy.value = true
            val photos = files.mapNotNull { file ->
                runSuspendCatching { compressPhoto(file.readBytes()) }
                    .getOrNull()
                    ?.let { PickedPhoto(ByteString(it.bytes), it.mimeType, it.width, it.height) }
            }
            busy.value = false
            if (photos.isNotEmpty()) sink(photos.toImmutableList())
        }
    }
    val library =
        rememberFilePickerLauncher(type = FileKitType.Image, mode = FileKitMode.Multiple(maxItems = maxItems.coerceIn(1, MAX_PICK))) {
            prepare(it.orEmpty())
        }
    val camera = rememberTakePhoto { file -> prepare(listOfNotNull(file)) }
    return remember(library, camera) { PhotoPicks(busy, onChoose = { library.launch() }, take = camera) }
}

/** Opens the camera and hands its photo to [onResult] (null when cancelled); null where the platform has no camera to open. */
@Composable internal expect fun rememberTakePhoto(onResult: (PlatformFile?) -> Unit): (() -> Unit)?

/** FileKit's limit for one pick. */
private const val MAX_PICK = 50
