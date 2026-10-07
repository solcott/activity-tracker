package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.DetailGroup
import io.github.solcott.activitytracker.data.profile.OwnProfile
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.profile.updateTraining
import io.github.solcott.activitytracker.data.training.SportZones
import io.github.solcott.activitytracker.data.training.ZoneModels
import io.github.solcott.activitytracker.data.training.Zones
import io.github.solcott.activitytracker.data.training.forSport
import io.github.solcott.activitytracker.data.training.override
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.UnitsRepository
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

@CircuitInject(TrainingZonesScreen::class, SessionScope::class)
@Composable
fun TrainingZonesPresenter(navigator: Navigator, profiles: ProfileRepository, units: UnitsRepository): TrainingZonesState {
    val unitSystem = units.units.collectAsState().value
    // Reloaded each time the screen shows, so it reflects what an editor just saved.
    var profile by remember { mutableStateOf<OwnProfile?>(null) }
    var error by remember { mutableStateOf<UiError?>(null) }
    // Picked and not yet saved, shown in place of the saved visibility until the save lands.
    var pickedVisibility by remember { mutableStateOf<String?>(null) }
    val flights = rememberSingleFlight()
    val savingVisibility = flights.isRunning(VISIBILITY)

    LaunchedEffect(Unit) {
        runSuspendCatching { profiles.ownProfile() }
            .onSuccess { profile = it }
            .onFailure { error = UiError.of(UiError.Kind.LoadProfile, it) }
    }

    val training = profile?.details?.training ?: TrainingDetails()
    val savedVisibility = profile?.let { DetailGroup.Training.visibility(it.visibility) } ?: Visibility.SELF
    return TrainingZonesState(
        defaults = profile?.let { summary(training, null, unitSystem) },
        sports =
            if (profile == null) persistentListOf() else training.sports.map { summary(training, it.sport, unitSystem) }.toImmutableList(),
        addableSports =
            if (profile == null) persistentListOf() else SportZones.overridable.filter { training.override(it) == null }.toImmutableList(),
        visibility = pickedVisibility ?: savedVisibility,
        hasZones = training != TrainingDetails(),
        savingVisibility = savingVisibility,
        error = error,
    ) { event ->
        when (event) {
            TrainingZonesEvent.Back -> navigator.pop()
            TrainingZonesEvent.EditDefaults -> navigator.goTo(ZonesScreen)
            is TrainingZonesEvent.VisibilityChanged ->
                if (profile != null && !savingVisibility && event.visibility != savedVisibility) {
                    error = null
                    pickedVisibility = event.visibility
                    flights.launch(VISIBILITY) {
                        runSuspendCatching { profiles.updateTraining(event.visibility) }
                            .onSuccess {
                                profile = profile?.let {
                                    it.copy(visibility = DetailGroup.Training.withVisibility(it.visibility, event.visibility))
                                }
                            }
                            .onFailure { error = UiError.of(UiError.Kind.SaveZones, it) }
                        pickedVisibility = null
                    }
                }
            is TrainingZonesEvent.EditSport -> navigator.goTo(SportZonesScreen(event.sport))
        }
    }
}

private const val VISIBILITY = "visibility"

/** [sport]'s zones as they apply, or the defaults' when [sport] is null. */
private fun summary(training: TrainingDetails, sport: String?, units: UnitSystem): ZoneSummary {
    val effective = training.forSport(sport)
    val heartRate = ZoneModels.heartRate(effective.heartRateZoneModel)
    val power = ZoneModels.power(effective.powerZoneModel)
    val speed = ZoneModels.speed(effective.speedZoneModel, sport)
    return ZoneSummary(
        sport = sport,
        kinds = SportZones.kinds(sport).toImmutableSet(),
        training = effective,
        override = training.override(sport),
        paceUnit = SportZones.paceUnit(sport, units),
        heartRateModel = heartRate,
        heartRateZones = Zones.compute(heartRate, effective)?.toImmutableList(),
        powerModel = power,
        powerZones = Zones.compute(power, effective)?.toImmutableList(),
        speedModel = speed,
        speedZones = Zones.compute(speed, effective)?.toImmutableList(),
    )
}
