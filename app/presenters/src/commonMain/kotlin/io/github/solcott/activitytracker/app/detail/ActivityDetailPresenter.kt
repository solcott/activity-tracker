package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.profile.ProfileScreen
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
import kotlinx.coroutines.launch

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
    var draft by remember { mutableStateOf("") }
    var loadKey by remember { mutableIntStateOf(0) }
    var showKudos by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
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

            is ActivityDetailEvent.DraftChanged -> {
                draft = event.text
            }

            ActivityDetailEvent.ToggleKudos -> {
                current?.let { scope.launch { runSuspendCatching { activities.toggleKudos(it) } } }
            }

            ActivityDetailEvent.SendComment -> {
                if (current != null && draft.isNotBlank()) {
                    val text = draft
                    draft = ""
                    scope.launch { runSuspendCatching { activities.comment(current, text) } }
                }
            }
        }
    }

    val shown = activity?.withKudosOverrides(kudosOverrides)
    val kudosList = rememberKudosList(showKudos, AtUri(screen.uri), viewerGave = shown?.viewer?.kudos != null, feed, profiles)
    return detailState(shown, error, sink) {
        ActivityDetailState.Loaded(it, comments, draft, it.author.did == profiles.did, kudosList, sink)
    }
}

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
