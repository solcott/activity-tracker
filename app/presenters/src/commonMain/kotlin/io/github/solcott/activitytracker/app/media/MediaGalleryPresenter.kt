package io.github.solcott.activitytracker.app.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import kotlinx.collections.immutable.persistentListOf

@CircuitInject(MediaGalleryScreen::class, SessionScope::class)
@Composable
fun MediaGalleryPresenter(screen: MediaGalleryScreen, navigator: Navigator, feed: FeedRepository): MediaGalleryState {
    val uri = AtUri(screen.uri)
    var activity by remember { mutableStateOf<ActivityView?>(null) }
    var error by remember { mutableStateOf<UiError?>(null) }
    var page by rememberSaveable { mutableStateOf(screen.index) }
    var zoomed by rememberSaveable { mutableStateOf(false) }
    // Photos the athlete just saved show here too, before the AppView has them.
    val rewrite by remember(uri) { feed.rewrites.observe(uri) }.collectAsState(null)

    LaunchedEffect(uri) {
        runSuspendCatching { feed.activity(uri).activity }
            .onSuccess { activity = it }
            .onFailure { error = UiError.of(UiError.Kind.LoadActivity, it) }
    }

    val shown = activity?.let { a -> rewrite?.let { feed.rewrites.rewritten(a, it) } ?: a }
    val items = remember(shown) { shown?.mediaItems() ?: persistentListOf() }
    return MediaGalleryState(items, page.coerceIn(0, (items.size - 1).coerceAtLeast(0)), zoomed, error) { event ->
        when (event) {
            is MediaGalleryEvent.PageChanged -> {
                page = event.page
                zoomed = false
            }

            MediaGalleryEvent.ToggleZoom -> zoomed = !zoomed

            MediaGalleryEvent.Back -> if (zoomed) zoomed = false else navigator.pop()
        }
    }
}
