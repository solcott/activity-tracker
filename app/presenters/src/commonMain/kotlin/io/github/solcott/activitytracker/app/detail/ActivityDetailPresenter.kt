package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

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

            ActivityDetailEvent.SendComment -> {
                val text = draft.text.toString()
                // Checked before clearing the draft, so a dropped repeat can't lose what was typed.
                if (current != null && text.isNotBlank() && !flights.isRunning(ActivityDetailEvent.SendComment)) {
                    draft.clearText()
                    flights.launch(ActivityDetailEvent.SendComment) { runSuspendCatching { activities.comment(current, text) } }
                }
            }
        }
    }

    val shown = activity?.withKudosOverrides(kudosOverrides)
    val kudosList = rememberKudosList(showKudos, AtUri(screen.uri), viewerGave = shown?.viewer?.kudos != null, feed, profiles)
    return detailState(shown, error, sink) {
        val isOwn = it.author.did == profiles.did
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
        )
    }
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
