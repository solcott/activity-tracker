package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.PrivacySettings
import io.github.solcott.activitytracker.data.activity.PrivacyZones
import io.github.solcott.activitytracker.data.activity.zonesOutcome
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.dataresult.combineOutcomes
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.circuit.produceRetainedContentStates
import io.github.solcott.uistate.contentStatesOf
import io.github.solcott.uistate.errorOrNull
import io.github.solcott.uistate.hasLoaded

/**
 * What a place (a segment, a dive site) is made from an activity, once all of it has loaded: the activity, its owner's full track (null for
 * none) and the privacy zones that guard it.
 */
data class ActivityRoute(val activity: ActivityView, val track: List<LocationSample>?, val zones: PrivacySettings)

/** [rememberActivityRouteLoad]'s reads: [content] holds all three once they have loaded, and [retry] reads them all again. */
class ActivityRouteLoad
internal constructor(
    val content: ContentState<ActivityRoute?>,
    private val activityFailed: Boolean,
    private val trackFailed: Boolean,
    val retry: () -> Unit,
) {
    /** Which read failed: the activity's, the track's as [trackKind] (what the screen calls the track), else the zones'. */
    fun loadErrorKind(trackKind: UiError.Kind): UiError.Kind =
        when {
            activityFailed -> UiError.Kind.LoadActivity
            trackFailed -> trackKind
            else -> UiError.Kind.LoadPrivacyZones
        }
}

/**
 * Reads the activity at [uri], its owner's full track and their privacy zones, for a screen that makes a place from them. Retained, so
 * coming back from another screen shows them at once; a retry restarts all three reads. The zones are read fresh, since they decide what of
 * the track may become public.
 */
@Composable
fun rememberActivityRouteLoad(
    uri: AtUri,
    feed: FeedRepository,
    activities: ActivityRepository,
    privacy: PrivacyZones,
): ActivityRouteLoad {
    var loadKey by remember { mutableIntStateOf(0) }
    val loads =
        produceRetainedContentStates(contentStatesOf<ActivityView?, List<LocationSample>?, PrivacySettings?>(null, null, null), loadKey) {
            combineOutcomes(feed.activityOutcome(uri), activities.ownRouteOutcome(uri), privacy.zonesOutcome())
        }
    // A null track is an answer (none), so it counts only once it has loaded.
    val content = loads.toContentState { activity, track, zones ->
        if (activity != null && zones != null && loads.second.hasLoaded) ActivityRoute(activity, track, zones) else null
    }
    return ActivityRouteLoad(
        content = content,
        activityFailed = loads.first.errorOrNull != null,
        trackFailed = loads.second.errorOrNull != null,
        retry = { loadKey++ },
    )
}
