package io.github.solcott.activitytracker.app.profile

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.formContent
import io.github.solcott.activitytracker.app.rememberFormSeed
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.auth.AuthRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.AvatarEdit
import io.github.solcott.activitytracker.data.profile.DetailGroup
import io.github.solcott.activitytracker.data.profile.DetailsVisibility
import io.github.solcott.activitytracker.data.profile.OwnProfile
import io.github.solcott.activitytracker.data.profile.ProfileEdit
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.units.HeightConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.UnitsRepository
import io.github.solcott.activitytracker.data.units.WeightConversions
import io.github.solcott.activitytracker.lexicon.app.FollowPolicy
import io.github.solcott.activitytracker.lexicon.app.Location
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlinx.collections.immutable.toImmutableList

@CircuitInject(EditProfileScreen::class, SessionScope::class)
@Composable
fun EditProfilePresenter(
    navigator: Navigator,
    profiles: ProfileRepository,
    unitsRepository: UnitsRepository,
    auth: AuthRepository,
): EditProfileState {
    val form = rememberForm(unitsRepository.units.value)
    var loadKey by remember { mutableIntStateOf(0) }
    val loaded = produceRetainedContentState<OwnProfile?>(null, loadKey) { profiles.ownProfileOutcome() }
    var picked by remember { mutableStateOf<AvatarImage.Picked?>(null) }
    var avatarRemoved by remember { mutableStateOf(false) }
    // Why a save failed; never a loading failure, which is [loaded]'s.
    var error by remember { mutableStateOf<UiError?>(null) }
    // Null until loaded; saved, like the form, so a configuration change keeps the athlete's choice.
    var approveFollowers by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val flights = rememberSingleFlight()
    val saving = flights.isRunning(EditProfileEvent.Save)

    // As the form was filled from: which details were public, for the warning when one is hidden.
    val own =
        rememberFormSeed(loaded) { profile ->
            form.fillOnce(profile)
            approveFollowers = approveFollowers ?: (profile.followPolicy == FollowPolicy.APPROVE)
        }

    val details = form.details()
    val visibility = form.visibility()
    val nowHidden = own?.let { nowHidden(it, details, visibility) }.orEmpty().toImmutableList()

    return EditProfileState(
        displayName = form.displayName,
        description = form.description,
        avatar = picked ?: own?.avatarUrl?.takeUnless { avatarRemoved }?.let(AvatarImage::Saved),
        locality = form.locality,
        region = form.region,
        country = form.country.value,
        birthDate = form.birthDate.value,
        gender = form.gender,
        weight = form.weight,
        height = form.height,
        heightInches = form.heightInches,
        units = form.units,
        visibility = visibility,
        approveFollowers = approveFollowers == true,
        nowHidden = nowHidden,
        content = loaded.formContent(seeded = own != null),
        needsSignIn = own?.needsSignIn == true,
        saving = saving,
        error = error,
    ) { event ->
        when (event) {
            EditProfileEvent.Back -> {
                navigator.pop()
            }

            is EditProfileEvent.AvatarPicked -> {
                picked = AvatarImage.Picked(event.jpeg)
                avatarRemoved = false
            }

            EditProfileEvent.RemoveAvatar -> {
                picked = null
                avatarRemoved = true
            }

            EditProfileEvent.Save -> {
                if (own != null && !saving) {
                    error = null
                    val edit =
                        ProfileEdit(
                            form.displayName.text.toString(),
                            form.description.text.toString(),
                            form.details(),
                            form.visibility(),
                            avatarEdit(picked, avatarRemoved),
                            followPolicy = followPolicy(approveFollowers == true),
                        )
                    flights.launch(EditProfileEvent.Save) {
                        runSuspendCatching { profiles.save(edit) }.onSuccess { navigator.pop() }.onFailure { error = saveError(it) }
                    }
                }
            }

            EditProfileEvent.Retry -> {
                loadKey++
            }

            EditProfileEvent.SignInAgain -> {
                flights.launch(EditProfileEvent.SignInAgain) { auth.signOut() }
            }

            is EditProfileEvent.ApproveFollowersChanged -> {
                approveFollowers = event.approve
            }

            else -> {
                form.update(event)
            }
        }
    }
}

/** The edit form's fields, saved across configuration changes. Weight and height are text in [units], as typed. */
@Suppress("LongParameterList") // One field per form input.
private class Form(
    val displayName: TextFieldState,
    val description: TextFieldState,
    val locality: TextFieldState,
    val region: TextFieldState,
    val country: MutableState<String>,
    val birthDate: MutableState<String>,
    val gender: TextFieldState,
    val weight: TextFieldState,
    /** cm, or feet for imperial. */
    val height: TextFieldState,
    /** Inches past [height]'s feet, for imperial. */
    val heightInches: TextFieldState,
    val visibilities: Map<DetailGroup, MutableState<String>>,
    /** True once loaded, so a configuration change keeps what the athlete typed instead of reloading it. */
    val filled: MutableState<Boolean>,
    val units: UnitSystem,
) {
    fun fillOnce(profile: OwnProfile) {
        if (filled.value) return
        val loaded = profile.details
        displayName.setTextAndPlaceCursorAtEnd(profile.displayName.orEmpty())
        description.setTextAndPlaceCursorAtEnd(profile.description.orEmpty())
        locality.setTextAndPlaceCursorAtEnd(loaded.location?.locality.orEmpty())
        region.setTextAndPlaceCursorAtEnd(loaded.location?.region.orEmpty())
        country.value = loaded.location?.country.orEmpty()
        birthDate.value = loaded.birthDate.orEmpty()
        gender.setTextAndPlaceCursorAtEnd(loaded.gender.orEmpty())
        weight.setTextAndPlaceCursorAtEnd(loaded.weight?.let { WeightConversions.toInputText(it, units) }.orEmpty())
        fillHeight(loaded.height)
        visibilities.forEach { (group, state) -> state.value = group.visibility(profile.visibility) ?: Visibility.SELF }
        filled.value = true
    }

    private fun fillHeight(mm: Long?) {
        when (units) {
            UnitSystem.Metric -> height.setTextAndPlaceCursorAtEnd(mm?.let(HeightConversions::toCentimetersText).orEmpty())
            UnitSystem.Imperial -> {
                val feetInches = mm?.let(HeightConversions::toFeetInches)
                height.setTextAndPlaceCursorAtEnd(feetInches?.feet?.toString().orEmpty())
                heightInches.setTextAndPlaceCursorAtEnd(feetInches?.inches?.toString().orEmpty())
            }
        }
    }

    private fun heightMm(): Long? =
        when (units) {
            UnitSystem.Metric -> HeightConversions.fromCentimetersText(height.text.toString())
            UnitSystem.Imperial -> HeightConversions.fromFeetInchesText(height.text.toString(), heightInches.text.toString())
        }

    /** Applies a picked value. Typed fields edit their [TextFieldState]s; saving, leaving and the avatar are the presenter's. */
    fun update(event: EditProfileEvent) {
        when (event) {
            is EditProfileEvent.CountryChanged -> country.value = event.value
            is EditProfileEvent.BirthDateChanged -> birthDate.value = event.value
            is EditProfileEvent.GenderChanged -> gender.setTextAndPlaceCursorAtEnd(event.value)
            is EditProfileEvent.VisibilityChanged -> visibilities.getValue(event.group).value = event.visibility
            EditProfileEvent.Save,
            EditProfileEvent.SignInAgain,
            EditProfileEvent.Retry,
            is EditProfileEvent.ApproveFollowersChanged,
            EditProfileEvent.Back,
            EditProfileEvent.RemoveAvatar,
            is EditProfileEvent.AvatarPicked -> Unit
        }
    }

    fun details(): ProfileDetails =
        ProfileDetails(
            location =
                Location(
                        locality.text.trim().ifEmpty { null }?.toString(),
                        region.text.trim().ifEmpty { null }?.toString(),
                        country.value.ifEmpty { null },
                    )
                    .takeUnless { it == Location() },
            birthDate = birthDate.value.ifEmpty { null },
            gender = gender.text.trim().ifEmpty { null }?.toString(),
            weight = WeightConversions.fromInputText(weight.text.toString(), units),
            height = heightMm(),
        )

    fun visibility(): DetailsVisibility =
        DetailGroup.profile.fold(DetailsVisibility()) { acc, group -> group.withVisibility(acc, visibilities.getValue(group).value) }
}

@Composable
private fun rememberForm(units: UnitSystem): Form {
    val savedUnits by rememberSaveable { mutableStateOf(units) }
    return Form(
        displayName = rememberTextFieldState(),
        description = rememberTextFieldState(),
        locality = rememberTextFieldState(),
        region = rememberTextFieldState(),
        country = rememberSaveable { mutableStateOf("") },
        birthDate = rememberSaveable { mutableStateOf("") },
        gender = rememberTextFieldState(),
        weight = rememberTextFieldState(),
        height = rememberTextFieldState(),
        heightInches = rememberTextFieldState(),
        visibilities = DetailGroup.profile.associateWith { group -> rememberSaveable(group.name) { mutableStateOf(Visibility.SELF) } },
        filled = rememberSaveable { mutableStateOf(false) },
        units = savedUnits,
    )
}

/** Details that were public as [loaded] and are still set, but no longer public: anyone may already have copied them. */
private fun nowHidden(loaded: OwnProfile, details: ProfileDetails, visibility: DetailsVisibility): List<DetailGroup> =
    DetailGroup.profile.filter { group ->
        group.visibility(loaded.visibility) == Visibility.PUBLIC &&
            group.isSet(loaded.details) &&
            group.isSet(details) &&
            group.visibility(visibility) != Visibility.PUBLIC
    }

private fun followPolicy(approve: Boolean): String = if (approve) FollowPolicy.APPROVE else FollowPolicy.OPEN

/** A session without access to the athlete's spaces can't save non-public details until they sign in again. */
private fun saveError(cause: Throwable): UiError = uiErrorOf(UiError.Kind.SaveProfile, cause)

private fun avatarEdit(picked: AvatarImage.Picked?, removed: Boolean): AvatarEdit =
    when {
        picked != null -> AvatarEdit.Replace(picked.jpeg.toByteArray(), AVATAR_MIME_TYPE)
        removed -> AvatarEdit.Remove
        else -> AvatarEdit.Keep
    }

/** The edit screen sends avatars already compressed to JPEG. */
private const val AVATAR_MIME_TYPE = "image/jpeg"
