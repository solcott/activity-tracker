package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.PrivacySettings
import io.github.solcott.activitytracker.data.activity.PrivacyZones
import io.github.solcott.activitytracker.data.activity.SegmentedPolyline
import io.github.solcott.activitytracker.data.activity.zonesOutcome
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.segments.SegmentPublisher
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.data.segments.SegmentRoute
import io.github.solcott.activitytracker.data.segments.SegmentSelection
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.SegmentSport
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.effectiveRecord
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.dataresult.combineOutcomes
import io.github.solcott.uistate.circuit.produceRetainedContentStates
import io.github.solcott.uistate.contentStatesOf
import io.github.solcott.uistate.errorOrNull
import io.github.solcott.uistate.hasLoaded
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@CircuitInject(CreateSegmentScreen::class, SessionScope::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod") // Loading the activity, its route and zones, then the selection and publishing.
fun CreateSegmentPresenter(
    screen: CreateSegmentScreen,
    navigator: Navigator,
    feed: FeedRepository,
    activities: ActivityRepository,
    privacy: PrivacyZones,
    segments: SegmentRepository,
    publisher: SegmentPublisher,
): CreateSegmentState {
    val uri = AtUri(screen.activityUri)
    var loadKey by remember { mutableIntStateOf(0) }
    // Retained, so coming back from a duplicate segment shows the route at once; a retry restarts all three reads under it. The zones are
    // read fresh, since they cut a line that will be public.
    val loads =
        produceRetainedContentStates(contentStatesOf<ActivityView?, List<LocationSample>?, PrivacySettings?>(null, null, null), loadKey) {
            combineOutcomes(feed.activityOutcome(uri), activities.ownRouteOutcome(uri), privacy.zonesOutcome())
        }
    // A null track is an answer (none), so it counts only once it has loaded.
    val loaded = loads.toContentState { activity, track, zones ->
        if (activity != null && zones != null && loads.second.hasLoaded) CreateSegmentLoad(activity, track, zones) else null
    }
    // As first loaded: later loads change neither the route nor the stretch chosen along it.
    val seed = rememberFormSeed(loaded) {}
    var start by rememberRetained { mutableFloatStateOf(0f) }
    var end by rememberRetained { mutableFloatStateOf(-1f) }
    var duplicates by rememberRetained { mutableStateOf<ImmutableList<SegmentView>>(persistentListOf()) }
    var limitReached by rememberRetained { mutableStateOf(false) }
    var actionError by rememberRetained { mutableStateOf<UiError?>(null) }
    val name = rememberTextFieldState()
    val flights = rememberSingleFlight()

    val activity = seed?.activity
    val track = seed?.track
    val record = remember(activity) { activity?.effectiveRecord() }
    val group = record?.let { SegmentSport.of(it.sport, it.subSport)?.group }
    val route =
        remember(seed, record) {
            val samples = track?.takeIf { it.size >= 2 } ?: return@remember null
            SegmentRoute(seed.zones.pieces(samples, combined = record?.combined != null))
        }
    val drawn = remember(route) { route?.lines?.let { lines -> drawnRoute(lines) } }
    // Until the athlete moves it, the stretch is the whole first piece.
    val chosenEnd = if (end < 0f) (route?.gaps?.firstOrNull() ?: route?.lengthMeters ?: 0.0).toFloat() else end
    val selection =
        remember(route, start, chosenEnd, group) {
            if (route == null || group == null) null else route.select(start.toDouble(), chosenEnd.toDouble(), group)
        }
    val ok = selection as? SegmentSelection.Ok
    val highlight = remember(ok) { ok?.line?.let(SegmentGeometry::toPrecision5) }
    val namesActivity = remember(activity) { activity?.let(::routeIsPublic) == true }
    // Read here so typing a name gives a new state.
    val title = name.text.toString().trim()

    val unavailable =
        when {
            seed == null -> null
            track.orEmpty().size < 2 -> CreateSegmentUnavailable.NoRoute
            group == null -> CreateSegmentUnavailable.NoSportGroup
            route == null || route.isEmpty -> CreateSegmentUnavailable.AllHidden
            else -> null
        }

    return CreateSegmentState(
        content = loaded.formContent(seeded = seed != null),
        loadErrorKind =
            when {
                loads.first.errorOrNull != null -> UiError.Kind.LoadActivity
                loads.second.errorOrNull != null -> UiError.Kind.LoadRoute
                else -> UiError.Kind.LoadPrivacyZones
            },
        unavailable = unavailable,
        route = drawn?.polyline,
        routeGaps = drawn?.gaps?.toImmutableList(),
        sport = group,
        lengthMeters = route?.lengthMeters?.toFloat() ?: 0f,
        start = start,
        end = chosenEnd,
        selection = selection.toCheck(),
        highlight = highlight,
        name = name,
        named = title.isNotEmpty(),
        namesActivity = namesActivity,
        duplicates = duplicates,
        limitReached = limitReached,
        publishing = flights.isRunning(CreateSegmentEvent.Publish),
        actionError = actionError,
    ) { event ->
        when (event) {
            CreateSegmentEvent.Back -> navigator.pop()
            CreateSegmentEvent.Retry -> loadKey++
            is CreateSegmentEvent.SelectionChanged -> {
                // They were found for the old stretch; cleared first, so no state shows them with the new one.
                duplicates = persistentListOf()
                start = event.start
                end = event.end
            }
            is CreateSegmentEvent.OpenSegment -> navigator.goTo(SegmentScreen(event.uri.value))
            CreateSegmentEvent.Publish -> {
                val line = ok?.encoded ?: return@CreateSegmentState
                val sport = group ?: return@CreateSegmentState
                val shown = activity ?: return@CreateSegmentState
                if (title.isEmpty()) return@CreateSegmentState
                flights.launch(CreateSegmentEvent.Publish) {
                    actionError = null
                    val checked =
                        runSuspendCatching { segments.check(sport, line) }
                            .onFailure { actionError = UiError.of(UiError.Kind.CheckSegment, it) }
                            .getOrNull() ?: return@launch
                    duplicates = checked.duplicates.toImmutableList()
                    limitReached = checked.remaining <= 0
                    if (duplicates.isNotEmpty() || limitReached) return@launch
                    val source = StrongRef(shown.uri, shown.cid).takeIf { namesActivity }
                    runSuspendCatching { publisher.create(uri, title, sport, line, source) }
                        .onSuccess { navigator.pop() }
                        .onFailure { actionError = UiError.of(UiError.Kind.PublishSegment, it) }
                }
            }
        }
    }
}

/** What a segment is cut from, once all of it has loaded: the activity, its full track (null for none) and the zones that cut it. */
private data class CreateSegmentLoad(val activity: ActivityView, val track: List<LocationSample>?, val zones: PrivacySettings)

/** [lines] as one precision-5 polyline broken between them, as maps draw routes. */
private fun drawnRoute(lines: List<List<GeoPoint>>): SegmentedPolyline? {
    if (lines.isEmpty()) return null
    val gaps = lines.runningFold(0) { at, line -> at + line.size }.drop(1).dropLast(1)
    return SegmentedPolyline(SegmentGeometry.toPrecision5(lines.flatten()), gaps.ifEmpty { null })
}

/** Whether [view]'s route is public: the activity is, and so is its Map category, so a segment may name it as its source. */
private fun routeIsPublic(view: ActivityView): Boolean {
    val activity = view.visibilityOrPublic
    if (activity != Visibility.PUBLIC) return false
    return (DetailCategory.currentTiers(activity, view, null)[DetailCategory.MAP] ?: activity) == Visibility.PUBLIC
}

private fun SegmentSelection?.toCheck(): SelectionCheck =
    when (this) {
        is SegmentSelection.Ok -> SelectionCheck.Ok(meters)
        is SegmentSelection.TooShort -> SelectionCheck.TooShort(minMeters)
        is SegmentSelection.TooLong -> SelectionCheck.TooLong(maxMeters)
        SegmentSelection.CrossesGap,
        null -> SelectionCheck.CrossesGap
    }
