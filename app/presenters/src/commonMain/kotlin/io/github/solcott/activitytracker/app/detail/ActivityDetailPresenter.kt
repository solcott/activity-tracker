package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
): ActivityDetailState {
    var activity by remember { mutableStateOf<ActivityView?>(null) }
    var comments by remember { mutableStateOf(emptyList<CommentView>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var loadKey by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

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
                if (current != null) {
                    val liked = current.viewer?.kudos != null
                    activity = current.copy(kudosCount = current.kudosCount + if (liked) -1 else 1)
                    scope.launch {
                        runSuspendCatching { if (liked) activities.removeKudos(current) else activities.giveKudos(current) }
                        loadKey++
                    }
                }
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

    val loaded = activity
    return when {
        loaded != null -> ActivityDetailState.Loaded(loaded, comments, draft, sink)
        error != null -> ActivityDetailState.Error(error!!, sink)
        else -> ActivityDetailState.Loading(sink)
    }
}
