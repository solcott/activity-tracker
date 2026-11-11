package io.github.solcott.activitytracker.app.gear

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.GearCollectionRecord
import io.github.solcott.activitytracker.lexicon.app.GearItemRecord
import kotlinx.datetime.LocalDate

/**
 * What the gear and collection forms say, chosen here for the shared and the native UI (dev/shared-display-text). The states' getters
 * ([EditGearState.kindLabel] and the rest) ask these with their own values.
 */
object EditGearTexts {
    /** "Add gear" or "Edit gear". */
    fun title(isNew: Boolean): AppText = AppText.named(if (isNew) "gear_new_title" else "gear_edit_title")

    /** "New collection" or "Edit collection". */
    fun collectionTitle(isNew: Boolean): AppText = AppText.named(if (isNew) "gear_collection_new" else "gear_collection_edit_title")

    /** [kind]'s name within [type], or None when it has no kind. */
    fun kind(type: String, kind: String?): AppText = kind?.let { GearTexts.kind(type, it) } ?: AppText.named("gear_kind_none")

    /** The sports' names joined ("Ride, Run"), or "Any sport" for none. */
    fun sports(sports: List<String>): AppText = AppText.joined(sports.map(LabelText::sport)) ?: AppText.named("gear_sports_none")

    /** The activity types' names joined, or "No activity types" for none. */
    fun defaultFor(types: List<ActivityType>): AppText =
        AppText.joined(types.map(LabelText::activityType)) ?: AppText.named("gear_default_for_none")

    /** "Starting distance (km)" or "(mi)", in [units]. */
    fun startingDistance(units: UnitSystem): AppText =
        AppText.named(
            when (units) {
                UnitSystem.Metric -> "gear_starting_distance_km"
                UnitSystem.Imperial -> "gear_starting_distance_mi"
            },
        )

    /** "Add photo" with none, "Change photo" with one. */
    fun photoAction(photo: GearPhoto?): AppText = AppText.named(if (photo == null) "gear_photo_add" else "gear_photo_change")

    /** "Mounted on Gravel bike since 2026-07-01". */
    fun mountedOn(mounted: GearMountedOn): AppText =
        AppText.named("gear_mounted_on_since", AppText.written(mounted.name), date(mounted.since))

    /** "Retired 2026-08-01". */
    fun retiredOn(date: LocalDate): AppText = AppText.named("gear_retired_on", date(date))

    /** A day as the forms show it: ISO, "2026-07-01". */
    fun date(date: LocalDate): AppText = AppText.written(date.toString())
}

/** The longest each field of the gear and collection forms takes, in characters, as both UIs limit typing. */
object EditGearLimits {
    val name: Int = GearItemRecord.MAX_NAME

    /** The brand's and the model's. */
    val brand: Int = GearItemRecord.MAX_BRAND
    val notes: Int = GearItemRecord.MAX_NOTES

    /** Whole grams, as digits only. */
    val weightDigits: Int
        get() = WEIGHT_DIGITS

    /** Room for a used bike's 100,000 km, or a helmet's thousands of hours, to a decimal. */
    val starting: Int
        get() = STARTING_CHARS

    val collectionName: Int = GearCollectionRecord.MAX_NAME
    val collectionNotes: Int = GearCollectionRecord.MAX_NOTES
}

private const val WEIGHT_DIGITS = 7
private const val STARTING_CHARS = 9

/** One of the kinds the gear form's Kind menu offers: [kind], null for None, with its [label]. */
@Immutable data class GearKindOption(val kind: String?, val label: AppText)
