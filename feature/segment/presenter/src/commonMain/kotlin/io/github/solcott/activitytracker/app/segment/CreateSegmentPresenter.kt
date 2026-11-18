package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.retained.rememberRetained
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.PublishCheck
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.checkThenPublish
import io.github.solcott.activitytracker.app.formContent
import io.github.solcott.activitytracker.app.rememberActivityRouteLoad
import io.github.solcott.activitytracker.app.rememberFormSeed
import io.github.solcott.activitytracker.app.rememberPublishChecks
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.mapIsPublic
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.PrivacyZones
import io.github.solcott.activitytracker.data.activity.SegmentedPolyline
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.segments.SegmentPublisher
import io.github.solcott.activitytracker.data.segments.SegmentRepository
import io.github.solcott.activitytracker.data.segments.SegmentRoute
import io.github.solcott.activitytracker.data.segments.SegmentSelection
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.SegmentSport
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.effectiveRecord
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.textfield.rememberTextFieldState
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
    // Retained, so coming back from a duplicate segment shows the route at once. The zones are read fresh, since they cut a line that will
    // be public.
    val load = rememberActivityRouteLoad(uri, feed, activities, privacy)
    // As first loaded: later loads change neither the route nor the stretch chosen along it.
    val seed = rememberFormSeed(load.content) {}
    var start by rememberRetained { mutableFloatStateOf(0f) }
    var end by rememberRetained { mutableFloatStateOf(-1f) }
    val checks = rememberPublishChecks<SegmentView>()
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
    val namesActivity = remember(activity) { activity?.let(::mapIsPublic) == true }
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
        content = load.content.formContent(seeded = seed != null),
        loadErrorKind = load.loadErrorKind(UiError.Kind.LoadRoute),
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
        duplicates = checks.duplicates,
        limitReached = checks.limitReached,
        publishing = flights.isRunning(CreateSegmentEvent.Publish),
        actionError = checks.error,
    ) { event ->
        when (event) {
            CreateSegmentEvent.Back -> navigator.pop()
            CreateSegmentEvent.Retry -> load.retry()
            is CreateSegmentEvent.SelectionChanged -> {
                // They were found for the old stretch; cleared first, so no state shows them with the new one.
                checks.clearDuplicates()
                start = event.start
                end = event.end
            }
            is CreateSegmentEvent.OpenSegment -> navigator.goTo(SegmentScreen(event.uri.value))
            CreateSegmentEvent.Publish -> {
                val line = ok?.encoded ?: return@CreateSegmentState
                val sport = group ?: return@CreateSegmentState
                val shown = activity ?: return@CreateSegmentState
                if (title.isEmpty()) return@CreateSegmentState
                val source = StrongRef(shown.uri, shown.cid).takeIf { namesActivity }
                flights.checkThenPublish(
                    key = CreateSegmentEvent.Publish,
                    checks = checks,
                    check = { segments.check(sport, line).let { PublishCheck(it.duplicates, it.remaining) } },
                    checkError = UiError.Kind.CheckSegment,
                    publish = { publisher.create(uri, title, sport, line, source) },
                    publishError = UiError.Kind.PublishSegment,
                    onPublished = { navigator.pop() },
                )
            }
        }
    }
}

/** [lines] as one precision-5 polyline broken between them, as maps draw routes. */
private fun drawnRoute(lines: List<List<GeoPoint>>): SegmentedPolyline? {
    if (lines.isEmpty()) return null
    val gaps = lines.runningFold(0) { at, line -> at + line.size }.drop(1).dropLast(1)
    return SegmentedPolyline(SegmentGeometry.toPrecision5(lines.flatten()), gaps.ifEmpty { null })
}

private fun SegmentSelection?.toCheck(): SelectionCheck =
    when (this) {
        is SegmentSelection.Ok -> SelectionCheck.Ok(meters)
        is SegmentSelection.TooShort -> SelectionCheck.TooShort(minMeters)
        is SegmentSelection.TooLong -> SelectionCheck.TooLong(maxMeters)
        SegmentSelection.CrossesGap,
        null -> SelectionCheck.CrossesGap
    }
