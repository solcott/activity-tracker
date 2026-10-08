package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.SingleFlight
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.settings.TrainingZonesScreen
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalKudos
import io.github.solcott.activitytracker.data.feed.withKudosOverrides
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

@CircuitInject(ActivityDetailScreen::class, SessionScope::class)
@Composable
fun ActivityDetailPresenter(
    screen: ActivityDetailScreen,
    navigator: Navigator,
    feed: FeedRepository,
    activities: ActivityRepository,
    profiles: ProfileRepository,
    localKudos: LocalKudos,
): ActivityDetailState {
    var activity by remember { mutableStateOf<ActivityView?>(null) }
    var comments by remember { mutableStateOf<ImmutableList<CommentView>>(persistentListOf()) }
    var error by remember { mutableStateOf<UiError?>(null) }
    val draft = rememberTextFieldState()
    var loadKey by remember { mutableIntStateOf(0) }
    var showKudos by remember { mutableStateOf(false) }
    val flights = rememberSingleFlight()
    // The viewer's own kudos toggles, laid over the loaded activity: the AppView only reflects them once Jetstream catches up.
    val kudosOverrides by localKudos.overrides.collectAsState()

    LaunchedEffect(loadKey) {
        error = null
        runSuspendCatching {
            val output = feed.activity(AtUri(screen.uri))
            activity = output.activity
            comments = output.comments.toImmutableList()
        }
            .onFailure { error = UiError.of(UiError.Kind.LoadActivity, it) }
    }

    val charts = rememberCharts(AtUri(screen.uri), loadKey, feed, activities, flights) { error = it }
    val shown = activity?.withKudosOverrides(kudosOverrides)
    val isOwn = shown?.author?.did == profiles.did
    val sections = shown?.let { detailSections(it, charts.charts) } ?: persistentListOf()
    val expansion = rememberExpansion(sections)

    val sink: (ActivityDetailEvent) -> Unit = { event ->
        val current = activity
        when (event) {
            ActivityDetailEvent.Back -> {
                navigator.pop()
            }

            ActivityDetailEvent.Retry -> {
                loadKey++
            }

            ActivityDetailEvent.OpenAuthor -> {
                current?.let { navigator.goTo(ProfileScreen(it.author.did.value)) }
            }

            ActivityDetailEvent.ToggleKudosList -> {
                showKudos = !showKudos
            }

            is ActivityDetailEvent.OpenProfile -> {
                navigator.goTo(ProfileScreen(event.did.value))
            }

            ActivityDetailEvent.ToggleKudos -> {
                current?.let { flights.launch(it.uri) { runSuspendCatching { activities.toggleKudos(it) } } }
            }

            is ActivityDetailEvent.SetChartsVisibility -> charts.setVisibility(event.visibility)

            ActivityDetailEvent.OpenTrainingZones -> navigator.goTo(TrainingZonesScreen)

            is ActivityDetailEvent.ToggleSection,
            ActivityDetailEvent.ToggleAll -> expansion.toggle(event)

            ActivityDetailEvent.SendComment -> {
                current?.let { sendComment(it, draft, flights, activities) }
            }
        }
    }

    val kudosList = rememberKudosList(showKudos, AtUri(screen.uri), viewerGave = shown?.viewer?.kudos != null, feed, profiles)
    return detailState(shown, error, sink) {
        ActivityDetailState.Loaded(
            activity = it,
            comments = comments,
            draftComment = draft,
            isOwn = isOwn,
            kudosList = kudosList,
            eventSink = sink,
            charts = charts.charts,
            chartsMissingZones = charts.missingZones(isOwn),
            savingChartsVisibility = charts.saving,
            sections = sections,
            expanded = expansion.expanded,
        )
    }
}

/** Sends [draft] on [activity] and clears it, unless it's blank or a comment is already sending. */
private fun sendComment(activity: ActivityView, draft: TextFieldState, flights: SingleFlight, activities: ActivityRepository) {
    val text = draft.text.toString()
    // Checked before clearing the draft, so a dropped repeat can't lose what was typed.
    if (text.isNotBlank() && !flights.isRunning(ActivityDetailEvent.SendComment)) {
        draft.clearText()
        flights.launch(ActivityDetailEvent.SendComment) { runSuspendCatching { activities.comment(activity, text) } }
    }
}

/** Which of the screen's sections show in full, and how [toggle] changes that. */
private class Expansion(val expanded: ImmutableSet<DetailSection>, val toggle: (ActivityDetailEvent) -> Unit)

/** Every one of [sections] starts collapsed; one toggles alone, or all collapse when any is open and expand when none is. */
@Composable
private fun rememberExpansion(sections: ImmutableList<DetailSection>): Expansion {
    // Names, so they save in a Bundle.
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    return Expansion(sections.filter { it.name in expanded }.toImmutableSet()) { event ->
        val all = sections.map { it.name }
        expanded =
            when (event) {
                is ActivityDetailEvent.ToggleSection -> event.section.name.let { if (it in expanded) expanded - it else expanded + it }
                ActivityDetailEvent.ToggleAll -> if (expanded.any { it in all }) emptyList() else all
                else -> expanded
            }
    }
}

/** The sections [activity] has data for: one per kind of chart, and the device. */
internal fun detailSections(activity: ActivityView, charts: ActivityCharts?): ImmutableList<DetailSection> {
    val kinds = charts?.lines.orEmpty().map { it.kind }.toSet()
    val record = activity.record
    return DetailSection.entries
        .filter { section ->
            when (section) {
                DetailSection.HeartRate -> StreamKind.HeartRate in kinds
                DetailSection.Power -> StreamKind.Power in kinds
                DetailSection.Speed -> StreamKind.Speed in kinds
                DetailSection.Cadence -> StreamKind.Cadence in kinds
                DetailSection.Elevation -> StreamKind.Elevation in kinds || StreamKind.Grade in kinds
                DetailSection.Temperature -> StreamKind.Temperature in kinds
                DetailSection.Depth -> StreamKind.Depth in kinds
                DetailSection.Gears -> charts != null && charts.gears.size >= 2
                DetailSection.Device -> record.device != null || !record.sensors.isNullOrEmpty()
            }
        }
        .toImmutableList()
}

/** The activity's charts, and the owner's control over who sees them. */
private class Charts(val charts: ActivityCharts?, val saving: Boolean, val setVisibility: (String) -> Unit) {
    /** The [own] activity has heart rate or power but no zones to split it into. */
    fun missingZones(own: Boolean): Boolean = own && charts?.missingZones() == true
}

/**
 * [activity]'s charts, reloaded with [loadKey]. They're extra: without streams, or when they fail to load, the activity shows without them.
 * Changing who sees them reports a failure through [onError].
 */
@Composable
private fun rememberCharts(
    activity: AtUri,
    loadKey: Int,
    feed: FeedRepository,
    activities: ActivityRepository,
    flights: SingleFlight,
    onError: (UiError) -> Unit,
): Charts {
    var charts by remember { mutableStateOf<ActivityCharts?>(null) }
    LaunchedEffect(loadKey) {
        runSuspendCatching { feed.streams(activity) }.onSuccess { streams -> charts = streams?.let { ChartsBuilder.buildOffMain(it) } }
    }
    val key = ActivityDetailEvent.SetChartsVisibility::class
    return Charts(charts, flights.isRunning(key)) { visibility ->
        if (charts?.visibility.let { it != null && it != visibility }) {
            flights.launch(key) {
                runSuspendCatching { activities.setStreamsVisibility(activity, visibility) }
                    .fold({ charts = charts?.copy(visibility = visibility) }, { onError(UiError.of(UiError.Kind.SaveActivity, it)) })
            }
        }
    }
}

/** Heart rate or power without the zones to split it into. */
private fun ActivityCharts.missingZones(): Boolean =
    lines.any { it.kind == StreamKind.HeartRate } && heartRateZones == null ||
        lines.any { it.kind == StreamKind.Power } && powerZones == null

private fun detailState(
    activity: ActivityView?,
    error: UiError?,
    sink: (ActivityDetailEvent) -> Unit,
    loaded: (ActivityView) -> ActivityDetailState.Loaded,
): ActivityDetailState =
    when {
        activity != null -> loaded(activity)
        error != null -> ActivityDetailState.Error(error, sink)
        else -> ActivityDetailState.Loading(sink)
    }

/**
 * Who gave kudos on [uri] while [show] is set, reloaded each time it's shown. The AppView lags the viewer's own toggles, so the viewer is
 * added or dropped to match [viewerGave].
 */
@Composable
private fun rememberKudosList(
    show: Boolean,
    uri: AtUri,
    viewerGave: Boolean,
    feed: FeedRepository,
    profiles: ProfileRepository,
): KudosList? {
    var athletes by remember { mutableStateOf(emptyList<ProfileViewBasic>()) }
    // Starts (and goes back to) loading while hidden, so the first frame after showing doesn't flash an empty list.
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<UiError?>(null) }
    var me by remember { mutableStateOf<ProfileViewBasic?>(null) }

    LaunchedEffect(show) {
        loading = true
        error = null
        if (!show) return@LaunchedEffect
        runSuspendCatching { athletes = feed.kudos(uri) }.onFailure { error = UiError.of(UiError.Kind.LoadKudos, it) }
        loading = false
    }

    val viewer = profiles.did
    val viewerListed = athletes.any { it.did == viewer }
    val needMe = show && !loading && error == null && viewerGave && !viewerListed && me == null
    LaunchedEffect(needMe) {
        if (needMe) {
            runSuspendCatching { profiles.profile(viewer.value) }.onSuccess { me = ProfileViewBasic(it.did, it.handle, it.displayName) }
        }
    }

    if (!show) return null
    val merged =
        when {
            !viewerGave -> athletes.filter { it.did != viewer }
            viewerListed -> athletes
            else -> listOfNotNull(me) + athletes
        }
    return KudosList(merged.toImmutableList(), loading, error)
}
