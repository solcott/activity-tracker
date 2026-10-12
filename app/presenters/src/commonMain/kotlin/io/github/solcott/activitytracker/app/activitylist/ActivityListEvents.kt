package io.github.solcott.activitytracker.app.activitylist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.slack.circuit.runtime.Navigator
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.media.MediaGalleryScreen
import io.github.solcott.activitytracker.app.profile.ProfileScreen
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository

/** Handles [ActivityListEvent]s for a host presenter: opens navigate, and kudos toggles once per activity at a time. */
@Composable
fun rememberActivityListEventSink(navigator: Navigator, activities: ActivityRepository): (ActivityListEvent) -> Unit {
    val flights = rememberSingleFlight()
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

                is ActivityListEvent.ToggleKudos -> {
                    flights.launch(event.activity.uri) { runSuspendCatching { activities.toggleKudos(event.activity) } }
                }
            }
        }
    }
}
