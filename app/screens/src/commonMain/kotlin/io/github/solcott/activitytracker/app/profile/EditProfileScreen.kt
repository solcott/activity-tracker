package io.github.solcott.activitytracker.app.profile

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.DetailGroup
import io.github.solcott.activitytracker.data.profile.DetailsVisibility
import io.github.solcott.activitytracker.data.units.HeightConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.WeightConversions
import kotlinx.collections.immutable.ImmutableList
import kotlinx.io.bytestring.ByteString

/** Edit the signed-in athlete's profile, and who may see each detail. */
@CircuitSerializable(SessionScope::class) data object EditProfileScreen : Screen

@Immutable
data class EditProfileState(
    val displayName: TextFieldState,
    val description: TextFieldState,
    /** The picture to show: a newly picked one, the saved one, or none. */
    val avatar: AvatarImage?,
    /** City or town. */
    val locality: TextFieldState,
    /** State, province or similar. */
    val region: TextFieldState,
    /** ISO 3166-1 alpha-2 code, or empty. */
    val country: String,
    /** `YYYY-MM-DD`, or empty. */
    val birthDate: String,
    /** A `Gender` value, the athlete's own description, or empty. A known value is picked with [EditProfileEvent.GenderChanged]. */
    val gender: TextFieldState,
    /** As typed, in kg or lb per [units]. */
    val weight: TextFieldState,
    /** As typed: cm, or whole feet for imperial (with [heightInches]). */
    val height: TextFieldState,
    /** As typed, the inches past [height]'s feet; only used for imperial. */
    val heightInches: TextFieldState,
    val units: UnitSystem,
    /** Who may see each detail; set for every group. */
    val visibility: DetailsVisibility,
    /** Details that were public when the screen opened and are now set to be hidden. Anyone may already have copied them. */
    val nowHidden: ImmutableList<DetailGroup>,
    /** False until the current profile has been read, so a save can't blank fields the athlete never saw. */
    val loaded: Boolean,
    val saving: Boolean,
    val error: UiError?,
    val eventSink: (EditProfileEvent) -> Unit,
) : CircuitUiState {
    val valid: Boolean
        get() =
            displayName.text.length <= DISPLAY_NAME_MAX_LENGTH &&
                description.text.length <= DESCRIPTION_MAX_LENGTH &&
                listOf(locality, region, gender).all { it.text.length <= DETAIL_MAX_LENGTH } &&
                WeightConversions.isValidInputText(weight.text.toString(), units) &&
                heightValid

    /** Whether [height] (and, for imperial, [heightInches]) is blank or a height the lexicon allows. */
    val heightValid: Boolean
        get() =
            when (units) {
                UnitSystem.Metric -> HeightConversions.isValidCentimetersText(height.text.toString())
                UnitSystem.Imperial -> HeightConversions.isValidFeetInchesText(height.text.toString(), heightInches.text.toString())
            }

    companion object {
        /**
         * The lexicon's `maxGraphemes`. Counting UTF-16 chars instead is stricter (an emoji can be two chars) but needs no grapheme
         * segmentation, and keeps well within the byte `maxLength`.
         */
        const val DISPLAY_NAME_MAX_LENGTH = 64
        const val DESCRIPTION_MAX_LENGTH = 256

        /** Locality, region and a self-described gender. */
        const val DETAIL_MAX_LENGTH = 64
    }
}

/** A profile picture for the edit screen to show. */
@Immutable
sealed interface AvatarImage {
    /** The saved picture, by URL. */
    data class Saved(val url: String) : AvatarImage

    /** A picture picked on this screen and not yet saved. */
    data class Picked(val jpeg: ByteString) : AvatarImage
}

sealed interface EditProfileEvent {
    /** [jpeg] is already downscaled and compressed to fit the lexicon's 1 MB limit. */
    data class AvatarPicked(val jpeg: ByteString) : EditProfileEvent

    data object RemoveAvatar : EditProfileEvent

    /** An ISO 3166-1 alpha-2 code, or empty for none. */
    data class CountryChanged(val value: String) : EditProfileEvent

    /** `YYYY-MM-DD`, or empty for none. */
    data class BirthDateChanged(val value: String) : EditProfileEvent

    /** Picked from the menu: a `Gender` value, or empty for none. A self-description is typed into [EditProfileState.gender]. */
    data class GenderChanged(val value: String) : EditProfileEvent

    /** [visibility] is a `Visibility` value. */
    data class VisibilityChanged(val group: DetailGroup, val visibility: String) : EditProfileEvent

    data object Save : EditProfileEvent

    /** Signs out, so the athlete can sign in again with access to their spaces ([UiError.Kind.SignInAgain]). */
    data object SignInAgain : EditProfileEvent

    data object Back : EditProfileEvent
}
