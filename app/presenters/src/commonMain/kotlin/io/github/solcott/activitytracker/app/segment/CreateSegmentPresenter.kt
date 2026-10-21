package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.PrivacySettings
import io.github.solcott.activitytracker.data.activity.SegmentedPolyline
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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map

@CircuitInject(CreateSegmentScreen::class, SessionScope::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod") // Loading the activity, its route and zones, then the selection and publishing.
fun CreateSegmentPresenter(
    screen: CreateSegmentScreen,
    navigator: Navigator,
    feed: FeedRepository,
    activities: ActivityRepository,
    segments: SegmentRepository,
    publisher: SegmentPublisher,
): CreateSegmentState {
    val uri = AtUri(screen.activityUri)
    var activity by remember { mutableStateOf<ActivityView?>(null) }
    // Null until read; an empty list when there's no track.
    var track by remember { mutableStateOf<List<LocationSample>?>(null) }
    var error by remember { mutableStateOf<UiError?>(null) }
    // Null until the zones were read once (or failed to be): then the settings, which are null when they couldn't be read.
    val zones by remember { activities.privacySettings().map { ZonesRead(it) } }.collectAsState(null)
    var start by remember { mutableFloatStateOf(0f) }
    var end by remember { mutableFloatStateOf(-1f) }
    var duplicates by remember { mutableStateOf<ImmutableList<SegmentView>>(persistentListOf()) }
    var limitReached by remember { mutableStateOf(false) }
    val name = rememberTextFieldState()
    val flights = rememberSingleFlight()

    LaunchedEffect(uri) {
        runSuspendCatching { feed.activity(uri) }
            .onSuccess { activity = it }
            .onFailure { error = UiError.of(UiError.Kind.LoadActivity, it) }
    }
    LaunchedEffect(uri) { track = runSuspendCatching { activities.ownRoute(uri) }.getOrNull().orEmpty() }

    val record = remember(activity) { activity?.effectiveRecord() }
    val group = record?.let { SegmentSport.of(it.sport, it.subSport)?.group }
    val settings = zones?.settings
    val route =
        remember(track, settings, record) {
            val samples = track?.takeIf { it.size >= 2 } ?: return@remember null
            settings?.let { SegmentRoute(it.pieces(samples, combined = record?.combined != null)) }
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
            activity == null || track == null || zones == null -> null
            track.orEmpty().size < 2 -> CreateSegmentUnavailable.NoRoute
            group == null -> CreateSegmentUnavailable.NoSportGroup
            settings == null -> CreateSegmentUnavailable.ZonesUnreadable
            route == null || route.isEmpty -> CreateSegmentUnavailable.AllHidden
            else -> null
        }

    return CreateSegmentState(
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
        isLoading = error == null && unavailable == null && route == null,
        error = error,
    ) { event ->
        when (event) {
            CreateSegmentEvent.Back -> navigator.pop()
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
                    error = null
                    val checked =
                        runSuspendCatching { segments.check(sport, line) }
                            .onFailure { error = UiError.of(UiError.Kind.CheckSegment, it) }
                            .getOrNull() ?: return@launch
                    duplicates = checked.duplicates.toImmutableList()
                    limitReached = checked.remaining <= 0
                    if (duplicates.isNotEmpty() || limitReached) return@launch
                    val source = StrongRef(shown.uri, shown.cid).takeIf { namesActivity }
                    runSuspendCatching { publisher.create(uri, title, sport, line, source) }
                        .onSuccess { navigator.pop() }
                        .onFailure { error = UiError.of(UiError.Kind.PublishSegment, it) }
                }
            }
        }
    }
}

/** The zones as last read: [settings] null when they couldn't be read. */
private data class ZonesRead(val settings: PrivacySettings?)

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
