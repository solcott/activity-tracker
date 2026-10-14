package io.github.solcott.activitytracker.app.activitylist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.media.MediaGalleryScreen
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository

/**
 * Handles [ActivityListEvent]s for a host presenter: opens navigate, and like toggles once per activity at a time. A like the session can't
 * give goes to [onError] ([likeErrorOf]).
 */
@Composable
fun rememberActivityListEventSink(
    navigator: Navigator,
    activities: ActivityRepository,
    onError: (UiError) -> Unit,
): (ActivityListEvent) -> Unit {
    val flights = rememberSingleFlight()
    val currentOnError by rememberUpdatedState(onError)
    return remember(navigator, activities, flights) {
        { event ->
            when (event) {
                is ActivityListEvent.Open -> {
                    navigator.goTo(ActivityDetailScreen(event.activity.uri.value))
                }

                is ActivityListEvent.OpenAuthor -> {
                    navigator.goTo(ProfileScreen(event.activity.author.did.value))
                }

                is ActivityListEvent.OpenMedia -> {
                    navigator.goTo(MediaGalleryScreen(event.activity.uri.value, event.index))
                }

                is ActivityListEvent.ToggleLike -> {
                    flights.launch(event.activity.uri) {
                        runSuspendCatching { activities.toggleLike(event.activity) }.onFailure { likeErrorOf(it)?.let(currentOnError) }
                    }
                }
            }
        }
    }
}

/**
 * What to show for a like that failed: a session whose scope doesn't cover likes in spaces is told to sign in again. Anything else is left
 * to the outbox, which retries.
 */
fun likeErrorOf(cause: Throwable): UiError? = uiErrorOf(UiError.Kind.SaveActivity, cause).takeIf { it.kind == UiError.Kind.SignInAgain }
