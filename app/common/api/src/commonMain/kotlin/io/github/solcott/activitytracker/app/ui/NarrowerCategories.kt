package io.github.solcott.activitytracker.app.ui

import io.github.solcott.activitytracker.app.visibility.CategoryVisibility
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The detail categories of the owner's own [activity] whose data in its view is stored narrower than the activity, from its record and its
 * details (a feed has no streams to read channels from).
 */
fun narrowerCategories(activity: ActivityView): ImmutableList<CategoryVisibility> {
    val tier = activity.visibilityOrPublic
    return DetailCategory.currentTiers(tier, activity, null)
        .filter { (_, t) -> Visibility.rank(t) > Visibility.rank(tier) }
        .entries
        .sortedBy { DetailCategory.all.indexOf(it.key) }
        .map { CategoryVisibility(it.key, it.value) }
        .toImmutableList()
}
