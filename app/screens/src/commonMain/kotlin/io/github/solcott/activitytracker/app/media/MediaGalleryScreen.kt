package io.github.solcott.activitytracker.app.media

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.ImmutableList

/** The activity at [uri]'s media, full screen, starting at [index]. */
@CircuitSerializable(SessionScope::class) data class MediaGalleryScreen(val uri: String, val index: Int) : Screen

@Immutable
data class MediaGalleryState(
    /** Empty while loading. */
    val items: ImmutableList<MediaItem>,
    /** The item showing. */
    val page: Int,
    /** The athlete tapped the photo to zoom and pan it; swiping between items waits until they tap again. */
    val zoomed: Boolean,
    val error: UiError?,
    val eventSink: (MediaGalleryEvent) -> Unit,
) : CircuitUiState

sealed interface MediaGalleryEvent {
    data class PageChanged(val page: Int) : MediaGalleryEvent

    data object ToggleZoom : MediaGalleryEvent

    /** Leaves zoom mode, else the gallery. */
    data object Back : MediaGalleryEvent
}
