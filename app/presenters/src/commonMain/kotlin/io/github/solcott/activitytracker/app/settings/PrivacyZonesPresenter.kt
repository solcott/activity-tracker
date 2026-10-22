package io.github.solcott.activitytracker.app.settings

import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.formContent
import io.github.solcott.activitytracker.app.rememberFormSeed
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.PrivacySettings
import io.github.solcott.activitytracker.data.activity.PrivacyZones
import io.github.solcott.activitytracker.data.activity.TooManyZonesException
import io.github.solcott.activitytracker.data.activity.ZonesUnreadableException
import io.github.solcott.activitytracker.data.activity.zonesOutcome
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.PrivacyZonesRecord
import io.github.solcott.activitytracker.location.LocationTracker
import io.github.solcott.uistate.circuit.produceRetainedContentState
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** A zone added at the current location starts at this radius; the athlete can change it. */
internal const val NEW_ZONE_RADIUS_METERS = 400

private val LOCATION_TIMEOUT = 30.seconds

/** Radii and hidden start/end distances, in the lexicon's steps. */
private val CHOICES: ImmutableList<Int> =
    (PrivacyZonesRecord.MIN_RADIUS_METERS..PrivacyZonesRecord.MAX_METERS step PrivacyZonesRecord.STEP_METERS).toList().toImmutableList()

/**
 * The athlete's privacy zones. Every change is a direct save, which re-trims their activities in the background
 * (`ActivityRepository.repairRoutesOnSave`, run for the session). Zones that can't be read can't be changed, so a save never starts from
 * nothing and drops them.
 */
@CircuitInject(PrivacyZonesScreen::class, SessionScope::class)
@Composable
fun PrivacyZonesPresenter(navigator: Navigator, privacy: PrivacyZones, tracker: LocationTracker): PrivacyZonesState {
    var loadKey by remember { mutableIntStateOf(0) }
    val loaded = produceRetainedContentState<PrivacySettings?>(null, loadKey) { privacy.zonesOutcome() }
    // As read when the screen first loaded them, then as each save here returned them. Later loads change nothing.
    var settings by rememberRetained { mutableStateOf<PrivacySettings?>(null) }
    // Why a change failed; never a loading failure, which is [loaded]'s.
    var error by remember { mutableStateOf<UiError?>(null) }
    var hasPermission by remember { mutableStateOf(tracker.hasPermission()) }
    val label = rememberTextFieldState()
    val flights = rememberSingleFlight()
    val saving = flights.isRunning(SAVE)
    val locating = flights.isRunning(PrivacyZonesEvent.AddAtCurrentLocation)

    val seed = rememberFormSeed(loaded) { settings = it }

    fun save(change: (PrivacySettings) -> PrivacySettings) {
        val current = settings ?: return
        error = null
        flights.launch(SAVE) {
            runSuspendCatching { privacy.save(change(current)) }.onSuccess { settings = it }.onFailure { error = privacyErrorOf(it) }
        }
    }

    fun addHere() {
        error = null
        flights.launch(PrivacyZonesEvent.AddAtCurrentLocation) {
            runSuspendCatching {
                val here = withTimeoutOrNull(LOCATION_TIMEOUT) { tracker.updates().first() }
                if (here == null) {
                    error = UiError(UiError.Kind.NoLocation)
                    return@runSuspendCatching
                }
                settings = privacy.add(here.latitude, here.longitude, NEW_ZONE_RADIUS_METERS, label.text.toString())
                label.clearText()
            }
                .onFailure { error = privacyErrorOf(it) }
        }
    }

    val current = settings
    return PrivacyZonesState(
        content = loaded.formContent(seeded = seed != null),
        zones = current?.zones?.toImmutableList(),
        hideEndsMeters = current?.hideEndsMeters ?: 0,
        choices = CHOICES,
        canEdit = current != null && privacy.canSave,
        saving = saving,
        canUseLocation = tracker.isSupported,
        hasLocationPermission = hasPermission,
        locating = locating,
        label = label,
        needsSignIn = current != null && !privacy.canSave,
        error = error,
    ) { event ->
        when (event) {
            PrivacyZonesEvent.Back -> navigator.pop()

            PrivacyZonesEvent.Retry -> loadKey++

            is PrivacyZonesEvent.RadiusChanged -> save { it.withRadius(event.index, event.meters) }

            is PrivacyZonesEvent.Remove -> save { it.without(event.index) }

            is PrivacyZonesEvent.HideEndsChanged -> save { s -> s.copy(hideEndsMeters = event.meters) }

            PrivacyZonesEvent.AddAtCurrentLocation -> if (hasPermission) addHere()

            is PrivacyZonesEvent.PermissionResult -> {
                hasPermission = event.granted
                if (event.granted) addHere()
            }
        }
    }
}

private const val SAVE = "save"

private fun privacyErrorOf(e: Throwable): UiError =
    when (e) {
        is TooManyZonesException -> UiError(UiError.Kind.TooManyZones)
        is ZonesUnreadableException -> UiError.of(UiError.Kind.LoadPrivacyZones, e)
        else -> uiErrorOf(UiError.Kind.SavePrivacyZones, e)
    }

private fun PrivacySettings.withRadius(index: Int, meters: Int) =
    copy(zones = zones.mapIndexed { i, zone -> if (i == index) zone.copy(radiusMeters = meters) else zone })

private fun PrivacySettings.without(index: Int) = copy(zones = zones.filterIndexed { i, _ -> i != index })
