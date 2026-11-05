package io.github.solcott.activitytracker.app.divesite

import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.Visibility

/** Whether [view]'s map is public: the dive is, and so is its Map category, so a site may name it as its source. */
internal fun mapIsPublic(view: ActivityView): Boolean {
    val activity = view.visibilityOrPublic
    if (activity != Visibility.PUBLIC) return false
    return (DetailCategory.currentTiers(activity, view, null)[DetailCategory.MAP] ?: activity) == Visibility.PUBLIC
}
