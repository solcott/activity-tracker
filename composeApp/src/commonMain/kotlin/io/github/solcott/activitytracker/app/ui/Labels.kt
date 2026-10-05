package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.detail_birth_date
import io.github.solcott.activitytracker.composeApp.resources.detail_gender
import io.github.solcott.activitytracker.composeApp.resources.detail_location
import io.github.solcott.activitytracker.composeApp.resources.detail_weight
import io.github.solcott.activitytracker.composeApp.resources.error_load_activity
import io.github.solcott.activitytracker.composeApp.resources.error_load_feed
import io.github.solcott.activitytracker.composeApp.resources.error_load_followers
import io.github.solcott.activitytracker.composeApp.resources.error_load_following
import io.github.solcott.activitytracker.composeApp.resources.error_load_kudos
import io.github.solcott.activitytracker.composeApp.resources.error_load_profile
import io.github.solcott.activitytracker.composeApp.resources.error_read_activity_file
import io.github.solcott.activitytracker.composeApp.resources.error_save_activity
import io.github.solcott.activitytracker.composeApp.resources.error_save_profile
import io.github.solcott.activitytracker.composeApp.resources.error_save_settings
import io.github.solcott.activitytracker.composeApp.resources.error_unsupported_activity_file
import io.github.solcott.activitytracker.composeApp.resources.gender_female
import io.github.solcott.activitytracker.composeApp.resources.gender_male
import io.github.solcott.activitytracker.composeApp.resources.gender_non_binary
import io.github.solcott.activitytracker.composeApp.resources.handle_format
import io.github.solcott.activitytracker.composeApp.resources.sport_hike
import io.github.solcott.activitytracker.composeApp.resources.sport_other
import io.github.solcott.activitytracker.composeApp.resources.sport_ride
import io.github.solcott.activitytracker.composeApp.resources.sport_run
import io.github.solcott.activitytracker.composeApp.resources.sport_ski
import io.github.solcott.activitytracker.composeApp.resources.sport_swim
import io.github.solcott.activitytracker.composeApp.resources.sport_walk
import io.github.solcott.activitytracker.data.profile.DetailGroup
import io.github.solcott.activitytracker.lexicon.app.Gender
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
                UiError.Kind.ReadActivityFile -> Res.string.error_read_activity_file
                UiError.Kind.UnsupportedActivityFile -> Res.string.error_unsupported_activity_file
                UiError.Kind.SaveSettings -> Res.string.error_save_settings
                UiError.Kind.SaveProfile -> Res.string.error_save_profile
            }
        )

/** A known [Gender] value's label, or the athlete's own description as written. */
@Composable
fun genderLabel(gender: String): String =
    when (gender) {
        Gender.FEMALE -> stringResource(Res.string.gender_female)
        Gender.MALE -> stringResource(Res.string.gender_male)
        Gender.NON_BINARY -> stringResource(Res.string.gender_non_binary)
        else -> gender
    }

/** The name of a profile detail group. */
@Composable
fun detailGroupLabel(group: DetailGroup): String =
    stringResource(
        when (group) {
            DetailGroup.Location -> Res.string.detail_location
            DetailGroup.BirthDate -> Res.string.detail_birth_date
            DetailGroup.Gender -> Res.string.detail_gender
            DetailGroup.Weight -> Res.string.detail_weight
        }
    )
