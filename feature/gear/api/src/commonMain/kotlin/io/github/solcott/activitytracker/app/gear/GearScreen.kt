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
import io.github.solcott.activitytracker.lexicon.app.GearComponent
import io.github.solcott.activitytracker.lexicon.app.GearMountView
import io.github.solcott.activitytracker.lexicon.app.GearUsage
import io.github.solcott.activitytracker.lexicon.app.GearView
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.PagedState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate

/**
 * The gear item at [uri], its canonical AT-URI (gear/gear-usage): its photo and details, its usage as the viewer may see it, and the
 * activities counted, newest first.
 */
@CircuitSerializable(SessionScope::class) data class GearScreen(val uri: String) : Screen

/**
 * The item as the page shows it: [usage] counts only what the viewer may see. [visibility] is a `Visibility` value, the owner's only. The
 * items mounted on it now ([components]) and before ([previous]), and the items it was mounted on ([mounts], oldest first), are only those
 * the viewer may see (gear/gear-components).
 */
@Immutable
data class GearPage(
    val gear: GearView,
    val usage: GearUsage,
    val retired: Boolean,
    val isOwner: Boolean,
    val visibility: String? = null,
    val components: ImmutableList<GearComponent> = persistentListOf(),
    val previous: ImmutableList<GearComponent> = persistentListOf(),
    val mounts: ImmutableList<GearMountView> = persistentListOf(),
)

/**
 * A device linked to the item (gear/gear-sensors), by its maker, model and the last characters of its serial ([serialTail]). A [weak] one
 * is known only by its model, so it may match other units of it. [id] names it to [GearEvent.UnlinkSensor].
 */
@Immutable
data class LinkedSensor(
    val id: String,
    val manufacturer: String?,
    val model: String?,
    val serialTail: String? = null,
    val weak: Boolean = false,
)

/** One of the athlete's items the page offers to mount: in use, not mounted elsewhere now, and not one this item is mounted on. */
@Immutable data class GearMountCandidate(val rkey: String, val name: String, val type: String, val kind: String? = null)

/** Choosing an item to mount on the page's item from today, in place of [replacing] when it's set. */
@Immutable data class GearMountPicker(val candidates: ImmutableList<GearMountCandidate>, val replacing: GearComponent? = null)

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
    /** Choosing an item to mount or to replace a component with; only on the owner's page. */
    val picker: GearMountPicker? = null,
    /** The component the owner is choosing an unmount date for. */
    val unmounting: GearComponent? = null,
    /** A mount, unmount or replace is saving. */
    val mountSaving: Boolean = false,
    /** The devices linked to the item; only on the owner's page, since links are private. */
    val linkedSensors: ImmutableList<LinkedSensor> = persistentListOf(),
    /** The activity profiles (`"MOUNTAIN"`) linked to the item; only on the owner's page. */
    val linkedProfiles: ImmutableList<String> = persistentListOf(),
    /** Why the last action failed: on an activity (a like), a collection change, a mount, unmount or replace, or an unlink. */
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

    /** Opens the gear page at [uri]: a component, or an item this one was mounted on. */
    data class OpenGear(val uri: String) : GearEvent

    /** Opens the picker to mount one of the owner's items on this one. */
    data object Mount : GearEvent

    /** Opens the picker to replace the component at [uri] from today. */
    data class Replace(val uri: String) : GearEvent

    /** Mounts the item at [rkey] from today, or replaces the picker's component with it. */
    data class ChooseMount(val rkey: String) : GearEvent

    /** Adds a new item to mount, or to replace the picker's component with, once it's saved. */
    data object MountNew : GearEvent

    data object DismissMount : GearEvent

    /** Asks for the date to unmount the component at [uri] on. */
    data class Unmount(val uri: String) : GearEvent

    /** Ends [GearState.unmounting]'s mount at the start of [date]. */
    data class ConfirmUnmount(val date: LocalDate) : GearEvent

    data object DismissUnmount : GearEvent

    /** Unlinks the [LinkedSensor] with [id]: later imports naming it offer it again, and past activities keep their gear. */
    data class UnlinkSensor(val id: String) : GearEvent

    /** Unlinks the activity profile [name]. */
    data class UnlinkProfile(val name: String) : GearEvent

    data class Activity(val event: ActivityListEvent) : GearEvent
}
