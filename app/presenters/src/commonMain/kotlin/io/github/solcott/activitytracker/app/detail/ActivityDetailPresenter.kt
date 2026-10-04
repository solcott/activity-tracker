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
    var comments by remember { mutableStateOf(emptyList<CommentView>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var loadKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    // The viewer's own kudos toggles, laid over the loaded activity: the AppView only reflects them once Jetstream catches up.
    val kudosOverrides by localKudos.overrides.collectAsState()

    LaunchedEffect(loadKey) {
        error = null
        runSuspendCatching {
            val output = feed.activity(AtUri(screen.uri))
            activity = output.activity
            comments = output.comments
        }
            .onFailure { error = it.message ?: "Couldn't load activity" }
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

    return detailState(activity?.withKudosOverrides(kudosOverrides), comments, error, draft, activity?.author?.did == profiles.did, sink)
}

private fun detailState(
    activity: ActivityView?,
    comments: List<CommentView>,
    error: String?,
    draft: String,
    isOwn: Boolean,
    sink: (ActivityDetailEvent) -> Unit,
): ActivityDetailState =
    when {
        activity != null -> ActivityDetailState.Loaded(activity, comments, draft, isOwn, sink)
        error != null -> ActivityDetailState.Error(error, sink)
        else -> ActivityDetailState.Loading(sink)
    }
