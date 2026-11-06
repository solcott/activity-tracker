package io.github.solcott.activitytracker.app.visibility

import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.Visibility

/**
 * Whether [view]'s map is public: the activity is, and so is its Map category. Only then may a public record (a segment, a dive site) name
 * it as its source, since that names the activity to everyone.
 */
fun mapIsPublic(view: ActivityView): Boolean {
    val activity = view.visibilityOrPublic
    if (activity != Visibility.PUBLIC) return false
    return (DetailCategory.currentTiers(activity, view, null)[DetailCategory.MAP] ?: activity) == Visibility.PUBLIC
}
