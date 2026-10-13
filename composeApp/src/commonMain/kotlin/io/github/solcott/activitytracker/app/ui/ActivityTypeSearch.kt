package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.data.sport.SportCatalog
import io.github.solcott.activitytracker.lexicon.app.ActivityType

/**
 * Whether [type] matches a type picker's search: [query], trimmed, is part of the type's label, its sport's label, or either token,
 * ignoring case. A blank query matches everything. Tokens match too, so "sup" finds Stand Up Paddle; "mtb" finds nothing.
 */
fun matchesTypeQuery(type: ActivityType, query: String, labels: Map<ActivityType, String>): Boolean {
    val needle = query.trim()
    return needle.isEmpty() ||
        listOfNotNull(labels[type], labels[ActivityType(type.sport)], type.sport, type.subSport).any {
            it.contains(needle, ignoreCase = true)
        }
}

/** [matchesTypeQuery] over every catalog type's label. Labels are resources, so the type pickers search here rather than in a presenter. */
@Composable
fun activityTypeMatcher(query: String): (ActivityType) -> Boolean {
    val labels = SportCatalog.categories.flatMap { it.types }.distinct().associateWith { activityTypeLabel(it) }
    return { type -> matchesTypeQuery(type, query, labels) }
}
