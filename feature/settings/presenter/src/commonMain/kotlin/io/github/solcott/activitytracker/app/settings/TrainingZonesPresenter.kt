package io.github.solcott.activitytracker.app.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.mapContent
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.profile.DetailGroup
import io.github.solcott.activitytracker.data.profile.OwnProfile
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.data.profile.updateTraining
import io.github.solcott.activitytracker.data.training.SportZones
import io.github.solcott.activitytracker.data.training.TrainingHistory
import io.github.solcott.activitytracker.data.training.ZoneModels
import io.github.solcott.activitytracker.data.training.Zones
import io.github.solcott.activitytracker.data.training.forSport
import io.github.solcott.activitytracker.data.training.override
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.UnitsRepository
import io.github.solcott.activitytracker.lexicon.app.FollowPolicy
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

@CircuitInject(TrainingZonesScreen::class, SessionScope::class)
@Composable
fun TrainingZonesPresenter(navigator: Navigator, profiles: ProfileRepository, units: UnitsRepository): TrainingZonesState {
    val unitSystem = units.units.collectAsState().value
    var loadKey by remember { mutableIntStateOf(0) }
    // Retained and reloaded each time the screen shows: coming back from an editor shows the zones at once, then what it saved.
    val loaded = produceRetainedContentState<OwnProfile?>(null, loadKey) { profiles.ownProfileOutcome() }
    // The training visibility saved here since the last load; the next load reads it back from the PDS.
    var savedHere by remember(loaded.data) { mutableStateOf<String?>(null) }
    val profile =
        loaded.data?.let { own -> savedHere?.let { own.copy(visibility = DetailGroup.Training.withVisibility(own.visibility, it)) } ?: own }
    // Why saving the visibility failed; never a loading failure, which is [loaded]'s.
    var error by remember { mutableStateOf<UiError?>(null) }
    // Picked and not yet saved, shown in place of the saved visibility until the save lands.
    var pickedVisibility by remember { mutableStateOf<String?>(null) }
    val flights = rememberSingleFlight()
    val savingVisibility = flights.isRunning(VISIBILITY)

    val savedVisibility = profile?.let { DetailGroup.Training.visibility(it.visibility) } ?: Visibility.SELF
    val zones = remember(profile, unitSystem) { profile?.let { zonesOf(it, unitSystem) } }
    return TrainingZonesState(
        content = loaded.mapContent { zones },
        visibility = pickedVisibility ?: savedVisibility,
        savingVisibility = savingVisibility,
        error = error,
    ) { event ->
        when (event) {
            TrainingZonesEvent.Back -> navigator.pop()
            TrainingZonesEvent.EditDefaults -> navigator.goTo(ZonesScreen)
            TrainingZonesEvent.History -> navigator.goTo(ThresholdHistoryScreen)
            is TrainingZonesEvent.VisibilityChanged ->
                if (profile != null && !savingVisibility && event.visibility != savedVisibility) {
                    error = null
                    pickedVisibility = event.visibility
                    flights.launch(VISIBILITY) {
                        runSuspendCatching { profiles.updateTraining(event.visibility) }
                            .onSuccess { savedHere = event.visibility }
                            .onFailure { error = uiErrorOf(UiError.Kind.SaveZones, it) }
                        pickedVisibility = null
                    }
                }
            is TrainingZonesEvent.EditSport -> navigator.goTo(SportZonesScreen(event.sport))
            TrainingZonesEvent.Retry -> loadKey++
        }
    }
}

private const val VISIBILITY = "visibility"

/** [profile]'s zones as they apply, in [units]. */
private fun zonesOf(profile: OwnProfile, units: UnitSystem): TrainingZones {
    val training = profile.details.training ?: TrainingDetails()
    return TrainingZones(
        defaults = summary(training, null, units),
        sports = training.sports.map { summary(training, it.sport, units) }.toImmutableList(),
        addableSports = SportZones.overridable.filter { training.override(it) == null }.toImmutableList(),
        hasZones = training != TrainingDetails(),
        hasHistory = TrainingHistory.backfilled(training).history.isNotEmpty(),
        approvesFollowers = profile.followPolicy == FollowPolicy.APPROVE,
    )
}

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
