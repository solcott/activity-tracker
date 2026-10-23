package io.github.solcott.activitytracker.app.gear

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.edit.PickedPhoto
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.uistate.ContentState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate

/** Adds a gear item ([rkey] null) or edits the athlete's item at [rkey]. */
@CircuitSerializable(SessionScope::class) data class EditGearScreen(val rkey: String? = null) : Screen

/** One of the athlete's collections as an item's form shows it, and whether the item is a [member]. */
@Immutable data class GearCollectionChoice(val rkey: String, val name: String, val member: Boolean)

/** The item's photo as the form shows it. */
@Immutable
sealed interface GearPhoto {
    data class Saved(val url: String) : GearPhoto

    data class Picked(val photo: PickedPhoto) : GearPhoto
}

@Immutable
data class EditGearState(
    /** The item as first read; loaded at once for a new item. */
    val content: ContentState<Unit>,
    val isNew: Boolean,
    val name: TextFieldState,
    val brand: TextFieldState,
    val model: TextFieldState,
    /** Grams, as digits. */
    val weight: TextFieldState,
    /** How far it went before the athlete tracked it (gear/gear-usage), in kilometers or miles per [units]; empty for none. */
    val startingDistance: TextFieldState,
    /** How long it was used before the athlete tracked it, in hours; empty for none. */
    val startingTime: TextFieldState,
    /** The athlete's units, which [startingDistance] is in. */
    val units: UnitSystem,
    val notes: TextFieldState,
    /** The type as stored; one this client doesn't know stays until the athlete picks another, and shows as `other`. */
    val type: String,
    /** The `GearType` values to choose from. */
    val types: ImmutableList<String>,
    /** The kind, when it's one of [kinds]. */
    val kind: String?,
    /** The kinds [type] offers; empty for none. */
    val kinds: ImmutableList<String>,
    /** `Sport` values. */
    val sports: ImmutableList<String>,
    /** The activity types a new activity starts with this item for ("Use by default for"). */
    val defaultFor: ImmutableList<ActivityType> = persistentListOf(),
    /** The athlete's collections by name, each marked when the item is in it. */
    val collections: ImmutableList<GearCollectionChoice> = persistentListOf(),
    /** A `Visibility` value; a new item starts at Only me. */
    val visibility: String,
    val acquiredOn: LocalDate?,
    val retiredOn: LocalDate?,
    val photo: GearPhoto?,
    /** The athlete tried to save without a name. */
    val nameMissing: Boolean,
    val saving: Boolean,
    /** Asking whether to delete, retire instead, or cancel. */
    val confirmingDelete: Boolean,
    /** Whether the athlete's follow policy approves followers, for the Followers note. */
    val approvesFollowers: Boolean,
    val error: UiError?,
    val eventSink: (EditGearEvent) -> Unit,
) : CircuitUiState

sealed interface EditGearEvent {
    data object Back : EditGearEvent

    data object Retry : EditGearEvent

    data class TypeChanged(val type: String) : EditGearEvent

    /** Null clears the kind. */
    data class KindChanged(val kind: String?) : EditGearEvent

    data class ToggleSport(val sport: String) : EditGearEvent

    /** Makes the item a default for [type], or no longer one. */
    data class ToggleDefault(val type: ActivityType) : EditGearEvent

    /** Adds the item to the collection at [rkey], or removes it from it. */
    data class ToggleCollection(val rkey: String) : EditGearEvent

    data class VisibilityChanged(val visibility: String) : EditGearEvent

    data class AcquiredChanged(val date: LocalDate?) : EditGearEvent

    data class PhotoPicked(val photo: PickedPhoto) : EditGearEvent

    data object RemovePhoto : EditGearEvent

    data object Save : EditGearEvent

    /** Saves the item retired today. */
    data object Retire : EditGearEvent

    /** Saves the item back in use. */
    data object Unretire : EditGearEvent

    /** Asks to delete ([EditGearState.confirmingDelete]). */
    data object Delete : EditGearEvent

    data object ConfirmDelete : EditGearEvent

    data object DismissDelete : EditGearEvent
}
