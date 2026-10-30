package io.github.solcott.activitytracker.app.record

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.AtUri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * The devices an import's file names that the athlete hasn't linked to gear or marked as not gear (gear/gear-sensors), one [cards] entry
 * each across the file or logbook, recording device first, and the offer to link the activity profile ([profileOffer]). Answers go to
 * [eventSink], which the import's holder fills. Empty when the session can't save gear links.
 */
@Immutable
data class NewDevicesFields(
    val cards: ImmutableList<NewDeviceCard> = persistentListOf(),
    /** The athlete's items a new one can be mounted on. */
    val mountOptions: ImmutableList<GearOption> = persistentListOf(),
    /** The athlete's collections a new item can join. */
    val collections: ImmutableList<GearCollectionOption> = persistentListOf(),
    val profileOffer: ProfileLinkOffer? = null,
    val eventSink: (NewDeviceEvent) -> Unit = {},
)

/** What the athlete does with a new device. */
enum class NewDeviceDecision {
    /** Ask again at the next import that names it; what a card left alone saves. */
    Later,

    /** Add it as gear, linked to it. */
    Add,

    /** It isn't gear: never ask again. */
    NotGear,
}

/**
 * A device the file names, by [manufacturer] and [model] and the last characters of its serial ([serialTail]). A [weak] one is known only
 * by its model, so it may match other units. [Add][NewDeviceDecision.Add] saves an Only me item named [name], of [type] and [kind] (a
 * `GearType` value and one of its kinds), mounted on [mountOn] from the activity's start and in [collections] (rkeys).
 */
@Immutable
data class NewDeviceCard(
    val id: String,
    val manufacturer: String?,
    val model: String?,
    val recorder: Boolean,
    /** A `SensorType` value, for a sensor. */
    val sensorKind: String? = null,
    val serialTail: String? = null,
    val weak: Boolean = false,
    val decision: NewDeviceDecision = NewDeviceDecision.Later,
    val name: TextFieldState = TextFieldState(),
    val type: String,
    val kind: String? = null,
    val mountOn: AtUri? = null,
    val collections: ImmutableSet<String> = persistentSetOf(),
    val expanded: Boolean = false,
)

/**
 * Offers to link the activity profile [name] (`"MOUNTAIN"`) to one of the [options], the gear picked for this import; [chosen] once picked.
 */
@Immutable data class ProfileLinkOffer(val name: String, val options: ImmutableList<GearOption>, val chosen: AtUri? = null)

sealed interface NewDeviceEvent {
    data class Decide(val id: String, val decision: NewDeviceDecision) : NewDeviceEvent

    /** Shows or hides the card's form. */
    data class Expand(val id: String, val expanded: Boolean) : NewDeviceEvent

    /** A `GearType` value: the kind resets to the first it offers, or none. */
    data class TypeChanged(val id: String, val type: String) : NewDeviceEvent

    data class KindChanged(val id: String, val kind: String?) : NewDeviceEvent

    /** Mounts the new item on [uri], or on nothing. */
    data class MountOn(val id: String, val uri: AtUri?) : NewDeviceEvent

    /** Adds the new item to the collection at [rkey], or takes it out. */
    data class ToggleCollection(val id: String, val rkey: String) : NewDeviceEvent

    /** Links the profile to [uri], or leaves it unlinked. */
    data class LinkProfile(val uri: AtUri?) : NewDeviceEvent
}

/**
 * What the screen still says once an import is saved, before it leaves: that the gear links weren't saved ([linksNotSaved]), and the offer
 * to add newly linked gear to earlier imports ([pastImports]).
 */
@Immutable data class AfterImport(val linksNotSaved: Boolean = false, val pastImports: PastImportsOffer? = null)

/**
 * Adds [gear] (the names of the items) to [count] earlier imports that name a newly linked device and list nothing of their type. [done]
 * counts those finished while [applying]; [failed] once a pass stopped.
 */
@Immutable
data class PastImportsOffer(
    val gear: ImmutableList<String>,
    val count: Int,
    val applying: Boolean = false,
    val done: Int = 0,
    val failed: Boolean = false,
)
