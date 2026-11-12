package io.github.solcott.activitytracker.app.record

import io.github.solcott.activitytracker.app.gear.GearTexts
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.data.units.formatWhole
import kotlinx.collections.immutable.persistentListOf

/** What the activity forms' gear picker says (gear/activity-gear), chosen here for the shared and the native picker. */
object GearFieldTexts {
    /** The chosen items' names in the athlete's order, as the field shows them; "No gear" when none is chosen. */
    fun summary(field: GearField): AppText {
        val names = field.selected.mapNotNull { uri -> field.options.firstOrNull { it.uri == uri }?.name }
        return if (names.isEmpty()) AppText.named("gear_field_none") else AppText.written(names.joinToString(", "))
    }

    /** What an item is and who may see it, "Road bike · Followers", and "· Retired" after a retired one. */
    fun line(option: GearOption): AppText {
        val what = option.kind?.let { GearTexts.kind(option.type, it) } ?: GearTexts.type(option.type)
        val detail = AppText.named("page_gear_item_value", what, LabelText.visibility(option.visibility))
        return if (option.retired) AppText.named("page_gear_item_value", detail, AppText.named("gear_field_retired")) else detail
    }

    /** A collection in the Add collection menu: its name and how many of its items exist, "Race day · 3 items". */
    fun collection(collection: GearCollectionOption): AppText =
        AppText.named(
            "page_gear_item_value",
            AppText.written(collection.name),
            AppText.Plural(
                "gear_collection_count",
                collection.count,
                persistentListOf(AppText.written(formatWhole(collection.count.toLong()))),
            ),
        )

    /** How many of the last added collection's items the activity's gear limit left out; null when none were. */
    fun notAdded(field: GearField): AppText? =
        field.notAdded
            .takeIf { it > 0 }
            ?.let { AppText.Plural("gear_field_not_added", it, persistentListOf(AppText.written(formatWhole(it.toLong())))) }
}
