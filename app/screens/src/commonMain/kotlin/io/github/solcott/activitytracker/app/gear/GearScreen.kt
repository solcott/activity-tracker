package io.github.solcott.activitytracker.app.gear

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.activitylist.ActivityListEvent
import io.github.solcott.activitytracker.app.moderation.Revealed
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GearUsage
import io.github.solcott.activitytracker.lexicon.app.GearView
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.PagedState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The gear item at [uri], its canonical AT-URI (gear/gear-usage): its photo and details, its usage as the viewer may see it, and the
 * activities counted, newest first.
 */
@CircuitSerializable(SessionScope::class) data class GearScreen(val uri: String) : Screen

/** The item as the page shows it: [usage] counts only what the viewer may see. [visibility] is a `Visibility` value, the owner's only. */
@Immutable
data class GearPage(val gear: GearView, val usage: GearUsage, val retired: Boolean, val isOwner: Boolean, val visibility: String? = null)

@Immutable
data class GearState(
    /** The item and its usage, with whether it's loading or failed to; null until it first loads. */
    val content: ContentState<GearPage?> = ContentState(null),
    /** The item doesn't exist or the viewer may not see it: the page says so instead of offering a retry. */
    val notFound: Boolean = false,
    /** The activities counted in its usage, newest first (ui/content-loading). */
    val activities: PagedState<ActivityView, Unit>,
    val viewerDid: String = "",
    /** Labeled photos the viewer revealed on this screen. */
    val revealed: Revealed = Revealed(),
    /** The athlete's collections by name, each marked when the item is in it; only on the owner's page, since collections are private. */
    val collections: ImmutableList<GearCollectionChoice> = persistentListOf(),
    /** Why the last action failed: on an activity (a like), or adding the item to a collection or taking it out. */
    val error: UiError? = null,
    val eventSink: (GearEvent) -> Unit = {},
) : CircuitUiState {
    /** [content]'s item: the last loaded, or null before the first. */
    val page: GearPage?
        get() = content.data
}

sealed interface GearEvent {
    data object Back : GearEvent

    /** Loads the item and its activities again after either failed. */
    data object Retry : GearEvent

    /** Loads the next page of activities. */
    data object LoadMore : GearEvent

    /** Opens the item's edit screen; only on the owner's page. */
    data object Edit : GearEvent

    /** Adds the item to the collection at [rkey], or takes it out, saving at once; only on the owner's page. */
    data class ToggleCollection(val rkey: String) : GearEvent

    data class Activity(val event: ActivityListEvent) : GearEvent
}
