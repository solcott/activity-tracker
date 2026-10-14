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
import io.github.solcott.activitytracker.app.edit.EditActivityScreen
import io.github.solcott.activitytracker.app.media.MediaGalleryScreen
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.settings.TrainingZonesScreen
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityManager
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalKudos
import io.github.solcott.activitytracker.data.feed.withKudosOverrides
import io.github.solcott.activitytracker.data.profile.ProfileRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.FollowPolicy
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
    manager: ActivityManager,
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

    // An edit shows as soon as it's saved, and stays over the AppView's copy until Jetstream brings it there.
    val rewrite by remember(screen.uri) { feed.rewrites.observe(AtUri(screen.uri)) }.collectAsState(null)

    val charts = rememberCharts(AtUri(screen.uri), activity, loadKey, feed, activities, flights) { error = it }
    val shown = activity?.let { a -> rewrite?.let { feed.rewrites.rewritten(a, it) } ?: a }?.withKudosOverrides(kudosOverrides)
    val isOwn = shown?.author?.did == profiles.did
    var approvesFollowers by remember { mutableStateOf(false) }
    LaunchedEffect(isOwn) {
        if (isOwn)
            runSuspendCatching { profiles.ownProfileRecord() }.onSuccess { approvesFollowers = it?.followPolicy == FollowPolicy.APPROVE }
    }
    val sections = shown?.let { detailSections(it, charts.charts) } ?: persistentListOf()
    val expansion = rememberExpansion(sections)
    val manage = rememberManage(activity.takeIf { isOwn }, loadKey, feed, manager, navigator, flights) { loadKey++ }

    val sink: (ActivityDetailEvent) -> Unit = { event ->
        val current = activity
        when (event) {
            ActivityDetailEvent.Back -> {
                navigator.pop()
            }

            ActivityDetailEvent.Retry -> {
                loadKey++
            }

            ActivityDetailEvent.OpenAuthor,
            is ActivityDetailEvent.OpenProfile,
            is ActivityDetailEvent.OpenActivity,
            ActivityDetailEvent.OpenTrainingZones,
            ActivityDetailEvent.Edit,
            is ActivityDetailEvent.OpenMedia -> navigator.open(event, screen.uri, current)

            ActivityDetailEvent.ToggleKudosList -> {
                showKudos = !showKudos
            }

            ActivityDetailEvent.ToggleKudos -> {
                current?.let { flights.launch(it.uri) { runSuspendCatching { activities.toggleKudos(it) } } }
            }

            is ActivityDetailEvent.SetChartsVisibility -> charts.setVisibility(event.visibility)

            is ActivityDetailEvent.ToggleSection,
            ActivityDetailEvent.ToggleAll -> expansion.toggle(event)

            ActivityDetailEvent.SendComment -> {
                current?.let { sendComment(it, draft, flights, activities) }
            }

            is ActivityDetailEvent.Delete -> manage.delete(event.withRecordings)

            ActivityDetailEvent.Ungroup -> manage.ungroup()
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
            approvesFollowers = approvesFollowers,
            sections = sections,
            expanded = expansion.expanded,
            manage = manage.state,
        )
    }
}

/** The owner's delete and ungroup: [state] is null on someone else's activity. */
private class Manage(val state: ActivityManage?, val delete: (withRecordings: Boolean) -> Unit, val ungroup: () -> Unit)

/**
 * Deleting or ungrouping the signed-in athlete's own [activity] (null for someone else's). A recording loads the activity it records, to
 * tell whether the two are grouped alone. A delete leaves the screen; an ungroup stays and [reload]s it.
 */
@Composable
@Suppress("LongParameterList") // Injected collaborators.
private fun rememberManage(
    activity: ActivityView?,
    loadKey: Int,
    feed: FeedRepository,
    manager: ActivityManager,
    navigator: Navigator,
    flights: SingleFlight,
    reload: () -> Unit,
): Manage {
    var error by remember { mutableStateOf<UiError?>(null) }
    val recordingOf = activity?.record?.recordingOf
    // The main activity's recordings, this one among them; null until loaded, and when it's gone.
    var siblings by remember(recordingOf) { mutableStateOf<Int?>(null) }
    LaunchedEffect(recordingOf, loadKey) {
        if (recordingOf != null) {
            runSuspendCatching { feed.activity(recordingOf) }.onSuccess { siblings = it.activity.recordings.orEmpty().size }
        }
    }
    val delete = ActivityDetailEvent.Delete::class
    val ungroup = ActivityDetailEvent.Ungroup
    activity ?: return Manage(null, {}, {})
    val recordings = activity.recordings.orEmpty()
    val state =
        ActivityManage(
            otherRecordings = recordings.size,
            canUngroup = if (recordingOf == null) recordings.size == 1 else siblings == 1,
            busy = flights.isRunning(delete) || flights.isRunning(ungroup),
            error = error,
        )
    return Manage(
        state,
        delete = { withRecordings ->
            flights.launch(delete) {
                error = null
                runSuspendCatching { manager.delete(activity, withRecordings) }
                    .onSuccess { navigator.pop() }
                    .onFailure { error = UiError.of(UiError.Kind.DeleteActivity, it) }
            }
        },
        ungroup = {
            if (state.canUngroup) {
                flights.launch(ungroup) {
                    error = null
                    runSuspendCatching {
                        val visibility = activity.visibilityOrPublic
                        if (recordingOf == null) manager.ungroup(recordings.single().uri, from = activity.uri, visibility)
                        else manager.ungroup(activity.uri, from = recordingOf, visibility)
                    }
                        .onSuccess { reload() }
                        .onFailure { error = UiError.of(UiError.Kind.UngroupActivity, it) }
                }
            }
        },
    )
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
 * Changing who sees them (once [view] has loaded, since they're never shown wider than it) reports a failure through [onError].
 */
@Composable
@Suppress("LongParameterList") // The activity, and what loads and saves its charts.
private fun rememberCharts(
    activity: AtUri,
    view: ActivityView?,
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
        if (view != null && charts?.visibility.let { it != null && it != visibility }) {
            flights.launch(key) {
                runSuspendCatching { activities.setStreamsVisibility(view, visibility) }
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

/** Goes where [event] leads from the activity at [uri]; [current] is the activity, once loaded. */
private fun Navigator.open(event: ActivityDetailEvent, uri: String, current: ActivityView?) {
    when (event) {
        ActivityDetailEvent.OpenAuthor -> current?.let { goTo(ProfileScreen(it.author.did.value)) }
        is ActivityDetailEvent.OpenProfile -> goTo(ProfileScreen(event.did.value))
        is ActivityDetailEvent.OpenActivity -> goTo(ActivityDetailScreen(event.uri.value))
        ActivityDetailEvent.OpenTrainingZones -> goTo(TrainingZonesScreen)
        ActivityDetailEvent.Edit -> goTo(EditActivityScreen(uri))
        is ActivityDetailEvent.OpenMedia -> goTo(MediaGalleryScreen(uri, event.index))
        else -> Unit
    }
}
