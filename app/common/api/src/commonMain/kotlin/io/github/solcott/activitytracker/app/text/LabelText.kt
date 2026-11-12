package io.github.solcott.activitytracker.app.text

import io.github.solcott.activitytracker.data.profile.DetailGroup
import io.github.solcott.activitytracker.data.sport.SportCategory
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.Gender
import io.github.solcott.activitytracker.lexicon.app.Visibility

/**
 * The names of things the app shows by a token or a kind. Several come from open sets (a sport another client added, a sensor kind the app
 * doesn't know), so each says what to show when the app has no string for one, the same in every UI (dev/shared-display-text).
 */
@Suppress("TooManyFunctions") // One per kind of label.
object LabelText {
    /** A sport's name. The lexicon's sport is an open enum, so an unknown token shows capitalized as it is. */
    fun sport(sport: String): AppText = AppText.Named("sport_$sport", fallback = AppText.written(sport.replaceFirstChar { it.uppercase() }))

    /** The sub-sport's own name ("Mountain Bike Ride"), or the sport's when the type is generic or its sub-sport is unknown. */
    fun activityType(type: ActivityType): AppText =
        type.subSport?.let { AppText.Named("sub_sport_${type.sport}_$it", fallback = sport(type.sport)) } ?: sport(type.sport)

    /** A dive computer's alert ("No-deco limit reached"), by its `DiveAlertKind` token; one the app doesn't name reads as its token. */
    fun diveAlert(kind: String): AppText = AppText.Named("dive_alert_$kind", fallback = AppText.written(kind))

    /** What a sensor is ("Power meter"), by its lexicon kind; an open enum, so an unknown kind is just a sensor. */
    fun sensorType(kind: String): AppText = AppText.Named("sensor_$kind", fallback = AppText.named("sensor_other"))

    /** "Power meter · SRAM", "Heart rate monitor · Garmin HRM 200": what a sensor is, then its make and model where known. */
    fun sensor(kind: String, manufacturer: String?, model: String?): AppText {
        val name = listOfNotNull(manufacturer, model).joinToString(" ").ifEmpty { null }
        return name?.let { AppText.named("text_dot_pair", sensorType(kind), AppText.written(it)) } ?: sensorType(kind)
    }

    /** The name of a sport picker group. */
    fun sportCategory(category: SportCategory): AppText = AppText.named("sport_category_${category.name}")

    /** An account handle with its `@` prefix. */
    fun handle(handle: String): AppText = AppText.named("handle_format", AppText.written(handle))

    /** A known [Gender] value's label, or the athlete's own description as written. */
    fun gender(gender: String): AppText =
        when (gender) {
            Gender.FEMALE -> AppText.named("gender_female")
            Gender.MALE -> AppText.named("gender_male")
            Gender.NON_BINARY -> AppText.named("gender_non_binary")
            else -> AppText.written(gender)
        }

    /** A [Visibility] value's name, as pickers and summaries show it. A value the app doesn't know reads as Only me, the narrowest. */
    fun visibility(visibility: String): AppText =
        AppText.named(
            when (Visibility.known(visibility)) {
                Visibility.PUBLIC -> "visibility_public"
                Visibility.FOLLOWERS -> "visibility_followers"
                Visibility.MUTUALS -> "visibility_mutuals"
                else -> "visibility_self"
            },
        )

    /**
     * A detail category's name, by its `DetailCategory` token. The set is open, so a token the app doesn't know shows as it is, and only a
     * known token is looked up: a lookup with a fallback could pick up another string whose name happened to end in the token.
     */
    fun detailCategory(category: String): AppText =
        if (category in DetailCategory.all) AppText.named("detail_category_$category") else AppText.written(category)

    /** The name of a profile detail group. */
    fun detailGroup(group: DetailGroup): AppText =
        AppText.named(
            when (group) {
                DetailGroup.Location -> "detail_location"
                DetailGroup.BirthDate -> "detail_birth_date"
                DetailGroup.Gender -> "detail_gender"
                DetailGroup.Body -> "detail_body"
                DetailGroup.Training -> "detail_training"
            },
        )

    /** What a profile detail group's visibility covers, as the heading of its picker: every field in the group. */
    fun visibilityScope(group: DetailGroup): AppText =
        AppText.named(
            when (group) {
                DetailGroup.Location -> "visibility_scope_location"
                DetailGroup.BirthDate -> "visibility_scope_birth_date"
                DetailGroup.Gender -> "visibility_scope_gender"
                DetailGroup.Body -> "visibility_scope_body"
                DetailGroup.Training -> "zones_visibility_heading"
            },
        )
}
