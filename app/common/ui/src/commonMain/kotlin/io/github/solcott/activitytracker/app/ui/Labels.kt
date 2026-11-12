package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.data.profile.DetailGroup
import io.github.solcott.activitytracker.data.sport.SportCategory
import io.github.solcott.activitytracker.lexicon.app.ActivityType

// What each label says is `:app-common-api`'s LabelText, shared with the native iOS UI (dev/shared-display-text); these read the string.

/** Display name for a sport token. The lexicon's sport is an open enum, so unknown tokens show capitalized as-is. */
@Composable fun sportLabel(sport: String): String = LabelText.sport(sport).string()

/** The sub-sport's own name ("Mountain Bike Ride"), or the sport's when the type is generic or its sub-sport is unknown. */
@Composable fun activityTypeLabel(type: ActivityType): String = LabelText.activityType(type).string()

/** A dive computer's alert ("No-deco limit reached"), by its `DiveAlertKind` token; one the app doesn't name reads as its token. */
@Composable fun diveAlertLabel(kind: String): String = LabelText.diveAlert(kind).string()

/** What a sensor is ("Power meter"), by its lexicon kind; an open enum, so an unknown kind is just a sensor. */
@Composable fun sensorTypeLabel(kind: String): String = LabelText.sensorType(kind).string()

/** "Power meter · SRAM", "Heart rate monitor · Garmin HRM 200". */
@Composable
fun sensorLabel(kind: String, manufacturer: String?, model: String?): String = LabelText.sensor(kind, manufacturer, model).string()

/** The name of a sport picker group. */
@Composable fun sportCategoryLabel(category: SportCategory): String = LabelText.sportCategory(category).string()

/** An account handle with its `@` prefix. */
@Composable fun handleLabel(handle: String): String = LabelText.handle(handle).string()

/** A known `Gender` value's label, or the athlete's own description as written. */
@Composable fun genderLabel(gender: String): String = LabelText.gender(gender).string()

/** The name of a profile detail group. */
@Composable fun detailGroupLabel(group: DetailGroup): String = LabelText.detailGroup(group).string()

/** What a profile detail group's visibility covers, as the heading of its picker: every field in the group. */
@Composable fun visibilityScopeLabel(group: DetailGroup): String = LabelText.visibilityScope(group).string()
