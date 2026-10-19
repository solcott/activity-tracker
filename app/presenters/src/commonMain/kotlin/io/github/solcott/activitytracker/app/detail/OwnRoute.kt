package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.app.SingleFlight
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.CombinedRoute
import io.github.solcott.activitytracker.data.activity.SegmentedPolyline
import io.github.solcott.activitytracker.data.activity.TooManyZonesException
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.location.LocationSample

/**
 * The owner's view of their own route: [line] is the full track, untrimmed, which only they read; null on someone else's activity, or when
 * the track can't be read (the trimmed polyline shows then). [hide] adds a privacy zone at its start or end.
 */
internal class OwnRoute(
    /** The full track's points, two or more; null like [line]. */
    val track: List<LocationSample>?,
    /** Broken where a combined activity's members were recorded one after another ([SegmentedPolyline]). */
    val line: SegmentedPolyline?,
    val canHide: Boolean,
    val zoneAdded: Boolean,
    val error: UiError?,
    val hide: (start: Boolean) -> Unit,
)

@Composable
internal fun rememberOwnRoute(
    activity: AtUri?,
    activities: ActivityRepository,
    flights: SingleFlight,
    combined: Boolean = false,
): OwnRoute {
    var route by remember(activity) { mutableStateOf<List<LocationSample>?>(null) }
    var zoneAdded by remember(activity) { mutableStateOf(false) }
    var error by remember(activity) { mutableStateOf<UiError?>(null) }
    // Apart from the activity's load: the map shows the trimmed polyline until the track arrives.
    LaunchedEffect(activity) { route = activity?.let { activities.ownRoute(it) }?.takeIf { it.size >= 2 } }
    val line =
        remember(route, combined) {
            route?.let {
                if (combined) SegmentedPolyline.of(CombinedRoute.segments(it))
                else ActivityDraft.polylineOf(it)?.let { p -> SegmentedPolyline(p, null) }
            }
        }
    val current = route
    return OwnRoute(
        current,
        line,
        canHide = current != null && !flights.isRunning(HIDE),
        zoneAdded,
        error,
    ) { start ->
        if (current != null) {
            error = null
            flights.launch(HIDE) {
                runSuspendCatching { activities.hideRouteEnd(current, start) }
                    .onSuccess { zoneAdded = true }
                    .onFailure { e ->
                        error =
                            if (e is TooManyZonesException) UiError(UiError.Kind.TooManyZones, null)
                            else uiErrorOf(UiError.Kind.SavePrivacyZones, e)
                    }
            }
        }
    }
}

private const val HIDE = "hideRouteEnd"
