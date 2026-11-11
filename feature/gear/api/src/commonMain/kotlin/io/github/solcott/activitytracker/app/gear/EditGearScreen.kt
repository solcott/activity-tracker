package io.github.solcott.activitytracker.app.gear

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.edit.PickedPhoto
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.visibility.VisibilityTexts
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.textfield.TextFieldState
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.hasLoaded
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate

/**
 * Adds a gear item ([rkey] null) or edits the athlete's item at [rkey]. A new item can be mounted on the item at [mountOn] from today once
 * it's saved, in place of the component at [replacing] when that's set (gear/gear-components).
 */
@CircuitSerializable(SessionScope::class)
data class EditGearScreen(val rkey: String? = null, val mountOn: String? = null, val replacing: String? = null) : Screen

/** The item the edited one is mounted on now, at [uri], since [since]. */
@Immutable data class GearMountedOn(val uri: String, val name: String, val since: LocalDate)

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
    /** What the item is mounted on now; null when it isn't, or the parent isn't one of the athlete's items any more. */
    val mountedOn: GearMountedOn? = null,
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
) : CircuitUiState {
    // What both UIs show and decide, from the fields above (dev/shared-display-text).

    /** "Add gear" or "Edit gear". */
    val title: AppText
        get() = EditGearTexts.title(isNew)

    /** Whether the form's fields, choices and buttons can be used: not while it saves. */
    val enabled: Boolean
        get() = !saving

    /** Whether Save is offered: once what the form edits has loaded, as the shared form shows its Save only with the form. */
    val showsSave: Boolean
        get() = content.hasLoaded

    /** Whether the type has kinds to choose from. */
    val showsKind: Boolean
        get() = kinds.isNotEmpty()

    /** The Kind menu's choices: None, then each of the type's kinds. */
    val kindOptions: ImmutableList<GearKindOption>
        get() =
            (listOf(GearKindOption(null, EditGearTexts.kind(type, null))) + kinds.map { GearKindOption(it, EditGearTexts.kind(type, it)) })
                .toImmutableList()

    /** The kind shown, or None. */
    val kindLabel: AppText
        get() = EditGearTexts.kind(type, kind)

    /** The sports it is for, joined, or "Any sport". */
    val sportsLabel: AppText
        get() = EditGearTexts.sports(sports)

    /** The types it is the default for, joined, or "No activity types". */
    val defaultForLabel: AppText
        get() = EditGearTexts.defaultFor(defaultFor)

    /** The starting distance's label in the athlete's [units]. */
    val startingDistanceLabel: AppText
        get() = EditGearTexts.startingDistance(units)

    /** "Add photo" or "Change photo". */
    val photoAction: AppText
        get() = EditGearTexts.photoAction(photo)

    /** Who may see it: all four visibilities. */
    val visibilityOptions: ImmutableList<String>
        get() = Visibility.all.toImmutableList()

    /** What choosing Followers means when anyone can follow the athlete; null otherwise. */
    val followersNote: AppText?
        get() = VisibilityTexts.followersNote(visibility, approvesFollowers)

    /** Whether the form is a saved item's, with what it is mounted on, retiring and deleting. */
    val showsManagement: Boolean
        get() = !isNew

    val isRetired: Boolean
        get() = retiredOn != null

    /** "Mounted on Gravel bike since 2026-07-01"; null when it isn't mounted. */
    val mountedOnLabel: AppText?
        get() = mountedOn?.let(EditGearTexts::mountedOn)

    /** "Retired 2026-08-01"; null while in use. */
    val retiredOnLabel: AppText?
        get() = retiredOn?.let(EditGearTexts::retiredOn)

    /** Whether the item is for [sport], as the sports sheet ticks it. */
    fun isForSport(sport: String): Boolean = sport in sports

    /** Whether new activities of [type] start with the item, as the default-for sheet ticks it. */
    fun isDefaultFor(type: ActivityType): Boolean = type in defaultFor
}

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

    /** Opens the gear page of the item it's mounted on. */
    data object OpenMountedOn : EditGearEvent
}
