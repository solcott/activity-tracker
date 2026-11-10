package io.github.solcott.activitytracker.app.divesite

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
import io.github.solcott.activitytracker.app.visibility.mapIsPublic
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.PrivacySettings
import io.github.solcott.activitytracker.data.activity.PrivacyZones
import io.github.solcott.activitytracker.data.activity.zonesOutcome
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.divesites.DiveSitePublisher
import io.github.solcott.activitytracker.data.divesites.DiveSiteRepository
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRecord
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.effectiveRecord
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.PrivacyCircle
import io.github.solcott.activitytracker.location.siteOutline
import io.github.solcott.activitytracker.textfield.rememberTextFieldState
import io.github.solcott.activitytracker.textfield.setTextAndPlaceCursorAtEnd
import io.github.solcott.dataresult.combineOutcomes
import io.github.solcott.uistate.circuit.produceRetainedContentStates
import io.github.solcott.uistate.contentStatesOf
import io.github.solcott.uistate.errorOrNull
import io.github.solcott.uistate.hasLoaded
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@CircuitInject(CreateDiveSiteScreen::class, SessionScope::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod") // Loading the dive, its position and zones, then the form and publishing.
fun CreateDiveSitePresenter(
    screen: CreateDiveSiteScreen,
    navigator: Navigator,
    feed: FeedRepository,
    activities: ActivityRepository,
    privacy: PrivacyZones,
    sites: DiveSiteRepository,
    publisher: DiveSitePublisher,
): CreateDiveSiteState {
    val uri = AtUri(screen.diveUri)
    var loadKey by remember { mutableIntStateOf(0) }
    // The zones are read fresh, since they decide whether a position that will be public may be.
    val loads =
        produceRetainedContentStates(contentStatesOf<ActivityView?, List<LocationSample>?, PrivacySettings?>(null, null, null), loadKey) {
            combineOutcomes(feed.activityOutcome(uri), activities.ownRouteOutcome(uri), privacy.zonesOutcome())
        }
    // A null track is an answer (none), so it counts only once it has loaded.
    val loaded = loads.toContentState { activity, track, zones ->
        if (activity != null && zones != null && loads.second.hasLoaded) CreateDiveSiteLoad(activity, track, zones) else null
    }
    val name = rememberTextFieldState()
    // The logbook's own site name, when it has one, starts the name.
    val seed = rememberFormSeed(loaded) { load -> load.activity.effectiveRecord().dive?.site?.let(name::setTextAndPlaceCursorAtEnd) }
    var radius by rememberRetained { mutableIntStateOf(DiveSiteRecord.DEFAULT_RADIUS) }
    var duplicates by rememberRetained { mutableStateOf<ImmutableList<DiveSiteView>>(persistentListOf()) }
    var limitReached by rememberRetained { mutableStateOf(false) }
    var actionError by rememberRetained { mutableStateOf<UiError?>(null) }
    val flights = rememberSingleFlight()

    val activity = seed?.activity
    // The site's centre is the dive's entry: its track's first position.
    val entry = seed?.track?.firstOrNull()
    val unavailable = seed?.let { unavailableFor(it.activity, entry, it.zones) }
    val latE7 = entry?.let { PrivacyCircle.toE7(it.latitude) }
    val lngE7 = entry?.let { PrivacyCircle.toE7(it.longitude) }
    val outline =
        remember(unavailable, latE7, lngE7, radius) {
            if (unavailable == null && latE7 != null && lngE7 != null) siteOutline(latE7, lngE7, radius) else null
        }
    val namesDive = remember(activity) { activity?.let(::mapIsPublic) == true }
    // Read here so typing a name gives a new state.
    val title = name.text.toString().trim()

    return CreateDiveSiteState(
        content = loaded.formContent(seeded = seed != null),
        loadErrorKind =
            when {
                loads.first.errorOrNull != null -> UiError.Kind.LoadActivity
                loads.second.errorOrNull != null -> UiError.Kind.LoadDivePosition
                else -> UiError.Kind.LoadPrivacyZones
            },
        unavailable = unavailable,
        outline = outline,
        name = name,
        named = title.isNotEmpty() && title.length <= DiveSiteRecord.MAX_NAME_GRAPHEMES,
        radius = radius,
        namesDive = namesDive,
        duplicates = duplicates,
        limitReached = limitReached,
        publishing = flights.isRunning(CreateDiveSiteEvent.Publish),
        actionError = actionError,
    ) { event ->
        when (event) {
            CreateDiveSiteEvent.Back -> navigator.pop()
            CreateDiveSiteEvent.Retry -> loadKey++
            is CreateDiveSiteEvent.RadiusChanged -> radius = event.meters.coerceIn(DiveSiteRecord.MIN_RADIUS, DiveSiteRecord.MAX_RADIUS)
            is CreateDiveSiteEvent.OpenSite -> navigator.goTo(DiveSiteScreen(event.uri.value))
            CreateDiveSiteEvent.Publish -> {
                val dive = activity ?: return@CreateDiveSiteState
                if (unavailable != null || latE7 == null || lngE7 == null) return@CreateDiveSiteState
                if (title.isEmpty() || title.length > DiveSiteRecord.MAX_NAME_GRAPHEMES) return@CreateDiveSiteState
                flights.launch(CreateDiveSiteEvent.Publish) {
                    actionError = null
                    val checked =
                        runSuspendCatching { sites.check(latE7, lngE7) }
                            .onFailure { actionError = UiError.of(UiError.Kind.CheckDiveSite, it) }
                            .getOrNull() ?: return@launch
                    duplicates = checked.duplicates.toImmutableList()
                    limitReached = checked.remaining <= 0
                    if (duplicates.isNotEmpty() || limitReached) return@launch
                    val source = StrongRef(dive.uri, dive.cid).takeIf { namesDive }
                    runSuspendCatching { publisher.create(uri, title, latE7, lngE7, radius, source) }
                        .onSuccess { navigator.pop() }
                        .onFailure { actionError = UiError.of(UiError.Kind.PublishDiveSite, it) }
                }
            }
        }
    }
}

/** What a site is placed from, once all of it has loaded: the dive, its own track (null for none) and the zones that guard it. */
private data class CreateDiveSiteLoad(val activity: ActivityView, val track: List<LocationSample>?, val zones: PrivacySettings)

/**
 * Why [dive] can't give a site, if it can't: it already visits a live one, it has no [entry], or the entry is one the athlete's privacy
 * settings hide (inside a zone, or at the start of the track, which their hidden start distance always covers).
 */
private fun unavailableFor(dive: ActivityView, entry: LocationSample?, zones: PrivacySettings): CreateDiveSiteUnavailable? =
    when {
        dive.diveSite?.let { SegmentStatus.of(it.status) in LIVE } == true -> CreateDiveSiteUnavailable.AtSite
        entry == null -> CreateDiveSiteUnavailable.NoPosition
        zones.zones.any {
            PrivacyCircle.fromE7(it.latitudeE7, it.longitudeE7, it.radiusMeters).contains(entry.latitude, entry.longitude)
        } -> CreateDiveSiteUnavailable.InsideZone
        zones.hideEndsMeters > 0 -> CreateDiveSiteUnavailable.HiddenStart
        else -> null
    }

private val LIVE = setOf(SegmentStatus.PROPOSED, SegmentStatus.ACTIVE)
