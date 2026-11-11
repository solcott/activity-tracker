package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.SignInAgainNotice
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.visibility.VisibilityTexts
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.CalendarDate
import io.github.solcott.activitytracker.data.profile.DetailGroup
import io.github.solcott.activitytracker.data.profile.DetailsVisibility
import io.github.solcott.activitytracker.data.units.HeightConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.WeightConversions
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.textfield.TextFieldState
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.hasLoaded
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate
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
    /**
     * Follows count only once the athlete approves them. Turning it on keeps everyone who follows them now; while it's off, Followers means
     * anyone who follows them.
     */
    val approveFollowers: Boolean = false,
    /** Details that were public when the screen opened and are now set to be hidden. Anyone may already have copied them. */
    val nowHidden: ImmutableList<DetailGroup>,
    /** The profile the form is filled from: loading or failed until it is, then loaded. */
    val content: ContentState<Unit>,
    /** A session signed in before its grant covered the athlete's spaces: hidden details can't be saved until they sign in again. */
    val needsSignIn: Boolean = false,
    val saving: Boolean,
    /** Why a save failed. */
    val error: UiError?,
    val eventSink: (EditProfileEvent) -> Unit,
) : CircuitUiState {
    /**
     * [description] under a name Swift export carries: it leaves out a member named `description`, which every Swift object already has
     * (dev/ios-swift-interop).
     */
    val bio: TextFieldState
        get() = description

    /** False until the form is filled from the current profile, so a save can't blank fields the athlete never saw. */
    val loaded: Boolean
        get() = content.hasLoaded

    val valid: Boolean
        get() =
            displayNameValid &&
                descriptionValid &&
                listOf(locality, region, gender).all { detailValid(it.text.toString()) } &&
                weightValid &&
                heightValid

    /** Whether Save can be used: the form is filled, every field holds what the lexicon allows, and it isn't saving already. */
    val canSave: Boolean
        get() = loaded && valid && !saving

    val displayNameValid: Boolean
        get() = displayName.text.length <= DISPLAY_NAME_MAX_LENGTH

    val descriptionValid: Boolean
        get() = description.text.length <= DESCRIPTION_MAX_LENGTH

    /** "5/64" under the display name. */
    val displayNameCounter: AppText
        get() = EditProfileTexts.counter(displayName.text.toString(), DISPLAY_NAME_MAX_LENGTH)

    /** "31/256" under the bio. */
    val descriptionCounter: AppText
        get() = EditProfileTexts.counter(description.text.toString(), DESCRIPTION_MAX_LENGTH)

    /** Whether [weight] is blank or a weight the lexicon allows, in [units]. */
    val weightValid: Boolean
        get() = WeightConversions.isValidInputText(weight.text.toString(), units)

    val weightLabel: AppText
        get() = EditProfileTexts.weightLabel(units)

    /** The height's label: in cm, or the feet of feet and inches. */
    val heightLabel: AppText
        get() = EditProfileTexts.heightLabel(units)

    /** Whether the height is entered as feet and inches, two fields. */
    val heightInFeetAndInches: Boolean
        get() = units == UnitSystem.Imperial

    /** The four visibilities a detail group can have. */
    val visibilityOptions: ImmutableList<String>
        get() = Visibility.all.toImmutableList()

    /** The birthdate as a calendar day, for a date control; null when none is set. */
    val birthDay: LocalDate?
        get() = CalendarDate.toLocalDate(birthDate)

    /** The notice at the top of the form: why the last save failed, or that the session must sign in again; null for neither. */
    val signInAgainNotice: SignInAgainNotice?
        get() = SignInAgainNotice.of(error, needsSignIn)

    /** Whether [height] (and, for imperial, [heightInches]) is blank or a height the lexicon allows. */
    val heightValid: Boolean
        get() =
            when (units) {
                UnitSystem.Metric -> HeightConversions.isValidCentimetersText(height.text.toString())
                UnitSystem.Imperial -> HeightConversions.isValidFeetInchesText(height.text.toString(), heightInches.text.toString())
            }

    /** Whether a locality, region or self-described gender of [text] fits its limit. */
    fun detailValid(text: String): Boolean = text.length <= DETAIL_MAX_LENGTH

    /** Who may see [group]'s fields; a group with none chosen, which the presenter never leaves, reads as Only me. */
    fun visibilityOf(group: DetailGroup): String = group.visibility(visibility) ?: Visibility.SELF

    /** What choosing Followers for [group] means when anyone can follow the athlete; null otherwise. */
    fun followersNote(group: DetailGroup): AppText? = VisibilityTexts.followersNote(visibilityOf(group), approveFollowers)

    /**
     * Whether [group] was public when the form opened and is now set to be hidden ([nowHidden]), asked per group since Swift can't read the
     * list.
     */
    fun isNowHidden(group: DetailGroup): Boolean = group in nowHidden

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
    data class BirthDateChanged(val value: String) : EditProfileEvent {
        /** [date] as its `YYYY-MM-DD`, or none; for a UI whose date control gives a calendar day. */
        constructor(date: LocalDate?) : this(CalendarDate.fromLocalDate(date))
    }

    /** Picked from the menu: a `Gender` value, or empty for none. A self-description is typed into [EditProfileState.gender]. */
    data class GenderChanged(val value: String) : EditProfileEvent

    /** [visibility] is a `Visibility` value. */
    data class VisibilityChanged(val group: DetailGroup, val visibility: String) : EditProfileEvent

    data class ApproveFollowersChanged(val approve: Boolean) : EditProfileEvent

    data object Save : EditProfileEvent

    /** Loads the profile again after a failed first load. */
    data object Retry : EditProfileEvent

    /** Signs out, so the athlete can sign in again with access to their spaces ([UiError.Kind.SignInAgain]). */
    data object SignInAgain : EditProfileEvent

    data object Back : EditProfileEvent
}
