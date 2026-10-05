package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.DetailGroup
import io.github.solcott.activitytracker.data.profile.OwnProfile
import io.github.solcott.activitytracker.data.profile.ProfileEdit
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.UnitsRepository
import io.github.solcott.activitytracker.data.units.WeightConversions
import io.github.solcott.activitytracker.lexicon.app.DetailsVisibility
import io.github.solcott.activitytracker.lexicon.app.Location
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@CircuitInject(EditProfileScreen::class, SessionScope::class)
@Composable
fun EditProfilePresenter(navigator: Navigator, profiles: ProfileRepository, unitsRepository: UnitsRepository): EditProfileState {
    val form = rememberForm(unitsRepository.units.value)
    // As loaded: which details were public, for the warning when one is hidden.
    var own by remember { mutableStateOf<OwnProfile?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runSuspendCatching { profiles.ownProfile() }
            .onSuccess { profile ->
                form.fillOnce(profile)
                own = profile
            }
            .onFailure { error = UiError.of(UiError.Kind.LoadProfile, it) }
    }

    val details = form.details()
    val visibility = form.visibility()
    val nowHidden =
        own?.let { loaded ->
                DetailGroup.entries.filter { group ->
                    group.visibility(loaded.visibility) == Visibility.PUBLIC &&
                        group.isSet(loaded.details) &&
                        group.isSet(details) &&
                        group.visibility(visibility) != Visibility.PUBLIC
                }
            }
            .orEmpty()
            .toImmutableList()

    return EditProfileState(
        displayName = form.displayName.value,
        description = form.description.value,
        locality = form.locality.value,
        region = form.region.value,
        country = form.country.value,
        birthDate = form.birthDate.value,
        gender = form.gender.value,
        weight = form.weight.value,
        units = form.units,
        visibility = visibility,
        nowHidden = nowHidden,
        loaded = own != null,
        saving = saving,
        error = error,
    ) { event ->
        when (event) {
            EditProfileEvent.Back -> {
                navigator.pop()
            }

            EditProfileEvent.Save -> {
                if (own != null && !saving) {
                    saving = true
                    error = null
                    val edit = ProfileEdit(form.displayName.value, form.description.value, details, visibility)
                    scope.launch {
                        runSuspendCatching { profiles.save(edit) }
                            .onSuccess { navigator.pop() }
                            .onFailure { error = UiError.of(UiError.Kind.SaveProfile, it) }
                        saving = false
                    }
                }
            }

            else -> {
                form.update(event)
            }
        }
    }
}

/** The edit form's fields, saved across configuration changes. Weight is text in [units], as typed. */
private class Form(
    val displayName: MutableState<String>,
    val description: MutableState<String>,
    val locality: MutableState<String>,
    val region: MutableState<String>,
    val country: MutableState<String>,
    val birthDate: MutableState<String>,
    val gender: MutableState<String>,
    val weight: MutableState<String>,
    val visibilities: Map<DetailGroup, MutableState<String>>,
    /** True once loaded, so a configuration change keeps what the athlete typed instead of reloading it. */
    val filled: MutableState<Boolean>,
    val units: UnitSystem,
) {
    fun fillOnce(profile: OwnProfile) {
        if (filled.value) return
        val loaded = profile.details
        displayName.value = profile.displayName.orEmpty()
        description.value = profile.description.orEmpty()
        locality.value = loaded.location?.locality.orEmpty()
        region.value = loaded.location?.region.orEmpty()
        country.value = loaded.location?.country.orEmpty()
        birthDate.value = loaded.birthDate.orEmpty()
        gender.value = loaded.gender.orEmpty()
        weight.value = loaded.weight?.let { WeightConversions.toInputText(it, units) }.orEmpty()
        visibilities.forEach { (group, state) -> state.value = group.visibility(profile.visibility) ?: Visibility.SELF }
        filled.value = true
    }

    /** Applies a field edit; [EditProfileEvent.Save] and [EditProfileEvent.Back] are the presenter's. */
    fun update(event: EditProfileEvent) {
        when (event) {
            is EditProfileEvent.DisplayNameChanged -> displayName.value = event.value
            is EditProfileEvent.DescriptionChanged -> description.value = event.value
            is EditProfileEvent.LocalityChanged -> locality.value = event.value
            is EditProfileEvent.RegionChanged -> region.value = event.value
            is EditProfileEvent.CountryChanged -> country.value = event.value
            is EditProfileEvent.BirthDateChanged -> birthDate.value = event.value
            is EditProfileEvent.GenderChanged -> gender.value = event.value
            is EditProfileEvent.WeightChanged -> weight.value = event.value
            is EditProfileEvent.VisibilityChanged -> visibilities.getValue(event.group).value = event.visibility
            EditProfileEvent.Save,
            EditProfileEvent.Back -> Unit
        }
    }

    fun details(): ProfileDetails =
        ProfileDetails(
            location =
                Location(locality.value.trim().ifEmpty { null }, region.value.trim().ifEmpty { null }, country.value.ifEmpty { null })
                    .takeUnless { it == Location() },
            birthDate = birthDate.value.ifEmpty { null },
            gender = gender.value.trim().ifEmpty { null },
            weight = WeightConversions.fromInputText(weight.value, units),
        )

    fun visibility(): DetailsVisibility =
        DetailGroup.entries.fold(DetailsVisibility()) { acc, group -> group.withVisibility(acc, visibilities.getValue(group).value) }
}

@Composable
private fun rememberForm(units: UnitSystem): Form {
    val savedUnits by rememberSaveable { mutableStateOf(units) }
    return Form(
        displayName = rememberSaveable { mutableStateOf("") },
        description = rememberSaveable { mutableStateOf("") },
        locality = rememberSaveable { mutableStateOf("") },
        region = rememberSaveable { mutableStateOf("") },
        country = rememberSaveable { mutableStateOf("") },
        birthDate = rememberSaveable { mutableStateOf("") },
        gender = rememberSaveable { mutableStateOf("") },
        weight = rememberSaveable { mutableStateOf("") },
        visibilities = DetailGroup.entries.associateWith { group -> rememberSaveable(group.name) { mutableStateOf(Visibility.SELF) } },
        filled = rememberSaveable { mutableStateOf(false) },
        units = savedUnits,
    )
}
