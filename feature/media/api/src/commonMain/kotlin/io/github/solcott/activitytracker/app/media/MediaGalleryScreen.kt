package io.github.solcott.activitytracker.app.media

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.moderation.Revealed
import io.github.solcott.activitytracker.app.report.Reporting
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.moderation.BlurReason
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** The activity at [uri]'s media, full screen, starting at [index]. */
@CircuitSerializable(SessionScope::class) data class MediaGalleryScreen(val uri: String, val index: Int) : Screen

@Immutable
data class MediaGalleryState(
    /** The activity's media, loaded (ui/content-loading); empty until it first loads. */
    val content: ContentState<ImmutableList<MediaItem>> = ContentState(persistentListOf()),
    /** The item showing. */
    val page: Int = 0,
    /** The athlete tapped the photo to zoom and pan it; swiping between items waits until they tap again. */
    val zoomed: Boolean = false,
    /** Why blocking the photo's author failed; loading failures are [content]'s. */
    val error: UiError? = null,
    /** Report on someone else's photos: [MediaGalleryEvent.ReportPhoto] is offered while [Reporting.available] and this is true. */
    val canReport: Boolean = false,
    val reporting: Reporting = Reporting(),
    /** The activity's media label: each photo is blurred until [revealed] holds the activity's URI. */
    val blur: BlurReason? = null,
    /** The athlete's own taken-down activity: placeholders, since its photos are no longer served. */
    val takenDown: Boolean = false,
    val revealed: Revealed = Revealed(),
    val eventSink: (MediaGalleryEvent) -> Unit = {},
) : CircuitUiState {
    val items: ImmutableList<MediaItem>
        get() = content.data
}

sealed interface MediaGalleryEvent {
    data class PageChanged(val page: Int) : MediaGalleryEvent

    data object ToggleZoom : MediaGalleryEvent

    /** Leaves zoom mode, else the gallery. */
    data object Back : MediaGalleryEvent

    /** Reports the photo showing: the report names its activity and says which photo. */
    data object ReportPhoto : MediaGalleryEvent

    /** Loads the activity again after it failed to. */
    data object Retry : MediaGalleryEvent
}
