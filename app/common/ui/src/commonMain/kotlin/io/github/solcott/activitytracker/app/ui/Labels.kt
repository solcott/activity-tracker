package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.composeApp.resources.detail_birth_date
import io.github.solcott.activitytracker.composeApp.resources.detail_body
import io.github.solcott.activitytracker.composeApp.resources.detail_gender
import io.github.solcott.activitytracker.composeApp.resources.detail_location
import io.github.solcott.activitytracker.composeApp.resources.detail_training
import io.github.solcott.activitytracker.composeApp.resources.gender_female
import io.github.solcott.activitytracker.composeApp.resources.gender_male
import io.github.solcott.activitytracker.composeApp.resources.gender_non_binary
import io.github.solcott.activitytracker.composeApp.resources.handle_format
import io.github.solcott.activitytracker.composeApp.resources.visibility_scope_birth_date
import io.github.solcott.activitytracker.composeApp.resources.visibility_scope_body
import io.github.solcott.activitytracker.composeApp.resources.visibility_scope_gender
import io.github.solcott.activitytracker.composeApp.resources.visibility_scope_location
import io.github.solcott.activitytracker.composeApp.resources.zones_visibility_heading
import io.github.solcott.activitytracker.data.profile.DetailGroup
import io.github.solcott.activitytracker.data.sport.SportCategory
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Gender
import org.jetbrains.compose.resources.stringResource

/** Display name for a sport token. The lexicon's sport is an open enum, so unknown tokens show capitalized as-is. */
@Composable
fun sportLabel(sport: String): String =
    Res.allStringResources["sport_$sport"]?.let { stringResource(it) } ?: sport.replaceFirstChar { it.uppercase() }

/** The sub-sport's own name ("Mountain Bike Ride"), or the sport's when the type is generic or its sub-sport is unknown. */
@Composable
fun activityTypeLabel(type: ActivityType): String =
    type.subSport?.let { Res.allStringResources["sub_sport_${type.sport}_$it"] }?.let { stringResource(it) } ?: sportLabel(type.sport)

/** A dive computer's alert ("No-deco limit reached"), by its `DiveAlertKind` token; one the app doesn't name reads as its token. */
@Composable fun diveAlertLabel(kind: String): String = Res.allStringResources["dive_alert_$kind"]?.let { stringResource(it) } ?: kind

/** What a sensor is ("Power meter"), by its lexicon kind; an open enum, so an unknown kind is just a sensor. */
@Composable
fun sensorTypeLabel(kind: String): String =
    stringResource(Res.allStringResources["sensor_$kind"] ?: Res.allStringResources.getValue("sensor_other"))

/** "Power meter · SRAM", "Heart rate monitor · Garmin HRM 200". */
@Composable
fun sensorLabel(kind: String, manufacturer: String?, model: String?): String =
    listOfNotNull(sensorTypeLabel(kind), listOfNotNull(manufacturer, model).joinToString(" ").ifEmpty { null }).joinToString(" · ")

/** The name of a sport picker group. */
@Composable
fun sportCategoryLabel(category: SportCategory): String = stringResource(Res.allStringResources.getValue("sport_category_${category.name}"))

/** An account handle with its `@` prefix. */
@Composable fun handleLabel(handle: String): String = stringResource(Res.string.handle_format, handle)

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
            DetailGroup.Body -> Res.string.detail_body
            DetailGroup.Training -> Res.string.detail_training
        }
    )

/** What a profile detail group's visibility covers, as the heading of its picker: every field in the group. */
@Composable
fun visibilityScopeLabel(group: DetailGroup): String =
    stringResource(
        when (group) {
            DetailGroup.Location -> Res.string.visibility_scope_location
            DetailGroup.BirthDate -> Res.string.visibility_scope_birth_date
            DetailGroup.Gender -> Res.string.visibility_scope_gender
            DetailGroup.Body -> Res.string.visibility_scope_body
            DetailGroup.Training -> Res.string.zones_visibility_heading
        }
    )
