package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.error_load_activity
import io.github.solcott.activitytracker.composeApp.resources.error_load_feed
import io.github.solcott.activitytracker.composeApp.resources.error_load_followers
import io.github.solcott.activitytracker.composeApp.resources.error_load_following
import io.github.solcott.activitytracker.composeApp.resources.error_load_kudos
import io.github.solcott.activitytracker.composeApp.resources.error_load_profile
import io.github.solcott.activitytracker.composeApp.resources.error_save_activity
import io.github.solcott.activitytracker.composeApp.resources.error_save_settings
import io.github.solcott.activitytracker.composeApp.resources.handle_format
import io.github.solcott.activitytracker.composeApp.resources.sport_hike
import io.github.solcott.activitytracker.composeApp.resources.sport_other
import io.github.solcott.activitytracker.composeApp.resources.sport_ride
import io.github.solcott.activitytracker.composeApp.resources.sport_run
import io.github.solcott.activitytracker.composeApp.resources.sport_ski
import io.github.solcott.activitytracker.composeApp.resources.sport_swim
import io.github.solcott.activitytracker.composeApp.resources.sport_walk
import io.github.solcott.activitytracker.lexicon.app.Sport
import org.jetbrains.compose.resources.stringResource

/** Display name for a sport token. The lexicon's sport is an open enum, so unknown tokens show capitalized as-is. */
@Composable
fun sportLabel(sport: String): String =
    when (sport) {
        Sport.RUN -> stringResource(Res.string.sport_run)
        Sport.RIDE -> stringResource(Res.string.sport_ride)
        Sport.WALK -> stringResource(Res.string.sport_walk)
        Sport.HIKE -> stringResource(Res.string.sport_hike)
        Sport.SWIM -> stringResource(Res.string.sport_swim)
        Sport.SKI -> stringResource(Res.string.sport_ski)
        Sport.OTHER -> stringResource(Res.string.sport_other)
        else -> sport.replaceFirstChar { it.uppercase() }
    }

/** An account handle with its `@` prefix. */
@Composable fun handleLabel(handle: String): String = stringResource(Res.string.handle_format, handle)

/** The failure's own message when it has one, otherwise localized text for what failed. */
@Composable
fun errorText(error: UiError): String =
    error.detail
        ?: stringResource(
            when (error.kind) {
                UiError.Kind.LoadFeed -> Res.string.error_load_feed
                UiError.Kind.LoadActivity -> Res.string.error_load_activity
                UiError.Kind.LoadKudos -> Res.string.error_load_kudos
                UiError.Kind.LoadProfile -> Res.string.error_load_profile
                UiError.Kind.LoadFollowers -> Res.string.error_load_followers
                UiError.Kind.LoadFollowing -> Res.string.error_load_following
                UiError.Kind.SaveActivity -> Res.string.error_save_activity
                UiError.Kind.SaveSettings -> Res.string.error_save_settings
            }
        )
