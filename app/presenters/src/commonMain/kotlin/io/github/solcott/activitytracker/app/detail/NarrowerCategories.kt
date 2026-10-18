package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.app.visibility.CategoryVisibility
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Each detail category of the owner's [view] (as served: its record and details apart) with data stored narrower than the activity, read
 * from where it's served ([streams] and the view's details), with its tier: its stats and charts show a lock. Empty for null, someone
 * else's activity.
 */
internal fun narrowerCategories(view: ActivityView?, streams: GetActivityStreams.Output?): ImmutableList<CategoryVisibility> {
    val activity = view?.visibilityOrPublic ?: return persistentListOf()
    return DetailCategory.currentTiers(activity, view, streams)
        .filter { (_, tier) -> Visibility.rank(tier) > Visibility.rank(activity) }
        .entries
        .sortedBy { DetailCategory.all.indexOf(it.key) }
        .map { CategoryVisibility(it.key, it.value) }
        .toImmutableList()
}
