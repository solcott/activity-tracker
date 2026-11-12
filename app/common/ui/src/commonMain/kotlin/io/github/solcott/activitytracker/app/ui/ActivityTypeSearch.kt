package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.sport.SportTypeSearch
import io.github.solcott.activitytracker.data.sport.SportCatalog
import io.github.solcott.activitytracker.lexicon.app.ActivityType

/** Whether [type] matches a type picker's search, by the shared rule ([SportTypeSearch.matches]) over the Compose [labels]. */
fun matchesTypeQuery(type: ActivityType, query: String, labels: Map<ActivityType, String>): Boolean =
    SportTypeSearch.matches(type, query, labels[type], labels[ActivityType(type.sport)])

/** [matchesTypeQuery] over every catalog type's label. Labels are resources, so the type pickers search here rather than in a presenter. */
@Composable
fun activityTypeMatcher(query: String): (ActivityType) -> Boolean {
    val labels = SportCatalog.categories.flatMap { it.types }.distinct().associateWith { activityTypeLabel(it) }
    return { type -> matchesTypeQuery(type, query, labels) }
}
