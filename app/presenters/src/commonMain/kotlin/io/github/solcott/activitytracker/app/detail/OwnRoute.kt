package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.app.RouteEnds
import io.github.solcott.activitytracker.app.ShownRoute
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
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.location.LocationSample
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flowOf

/**
 * The owner's view of their own route: [map] is the full track, untrimmed, which only they read; null on someone else's activity, or when
 * the track can't be read (the trimmed polyline shows then). [hide] adds a privacy zone at its start or end.
 */
@Suppress("LongParameterList") // What the owner's map shows, and what can be done with the route.
internal class OwnRoute(
    /** The full track's points, two or more; null like [map]. */
    val track: List<LocationSample>?,
    val map: OwnMap?,
    val canHide: Boolean,
    val zoneAdded: Boolean,
    val error: UiError?,
    /** The owner's privacy settings were read, so the track can be cut by them, as a new segment's line is. */
    val zonesRead: Boolean,
    val hide: (start: Boolean) -> Unit,
)

/** The owner's map of their route. */
internal class OwnMap(
    /**
     * The full track, broken where a combined activity's members were recorded one after another, or a single one's positions far apart
     * ([SegmentedPolyline]).
     */
    val line: SegmentedPolyline,
    /** Where [line]'s start and end are marked: the ends of the route as trimmed by the owner's privacy settings. */
    val ends: RouteEnds,
)

/**
 * What [record]'s map draws: [own], the owner's full track marked at the route others see, once it's read; else the polyline the viewer was
 * served, marked at its own ends.
 */
internal fun shownRoute(record: ActivityRecord, own: OwnMap?): ShownRoute? =
    if (own != null) ShownRoute(own.line.polyline, own.line.gaps?.toImmutableList(), own.ends)
    else record.polyline?.let { ShownRoute(it, record.polylineGaps?.toImmutableList(), ends = null) }

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
            route?.let { if (combined) SegmentedPolyline.of(CombinedRoute.segments(it)) else ActivityDraft.polylineOf(it) }
        }
    // Read only on the owner's own activity.
    val settings by remember(activity) { if (activity == null) flowOf(null) else activities.privacySettings() }.collectAsState(null)
    val map =
        remember(route, line, settings, combined) {
            val track = route ?: return@remember null
            val ends =
                settings?.shownEnds(track, combined)?.let { (start, end) ->
                    RouteEnds(start.latitude to start.longitude, end.latitude to end.longitude)
                } ?: RouteEnds.HIDDEN
            line?.let { OwnMap(it, ends) }
        }
    val current = route
    return OwnRoute(
        current,
        map,
        canHide = current != null && !flights.isRunning(HIDE),
        zoneAdded,
        error,
        zonesRead = settings != null,
    ) { start ->
        if (current != null) {
            error = null
            flights.launch(HIDE) {
                runSuspendCatching { activities.hideRouteEnd(current, start) }
                    .onSuccess { zoneAdded = true }
                    .onFailure { e ->
                        error =
                            if (e is TooManyZonesException) UiError(UiError.Kind.TooManyZones)
                            else uiErrorOf(UiError.Kind.SavePrivacyZones, e)
                    }
            }
        }
    }
}

private const val HIDE = "hideRouteEnd"
