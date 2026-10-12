package io.github.solcott.activitytracker.app.feedfilter

import io.github.solcott.activitytracker.data.sport.SportCatalog
import io.github.solcott.activitytracker.lexicon.app.ActivityType

/*
 * A type selection is a set of ActivityTypes where a bare sport (no sub-sport) stands for that sport and every sub-sport of it, as the
 * AppView matches it. These keep a selection canonical: a sport never sits beside its own sub-sports, and a sport whose sub-sports are all
 * selected is the bare sport.
 */

/** Whether [type] is selected, directly or through its bare sport. */
internal fun Set<ActivityType>.covers(type: ActivityType): Boolean =
    type in this || (type.subSport != null && ActivityType(type.sport) in this)

internal fun Set<ActivityType>.toggle(type: ActivityType): Set<ActivityType> = if (covers(type)) deselect(type) else select(type)

internal fun Set<ActivityType>.select(type: ActivityType): Set<ActivityType> = (this + type).normalized()

/** Deselecting a sub-sport its bare sport covers keeps the sport's other sub-sports, but no longer its generic form. */
internal fun Set<ActivityType>.deselect(type: ActivityType): Set<ActivityType> {
    val bare = ActivityType(type.sport)
    return when {
        type.subSport == null -> filterNot { it.sport == type.sport }.toSet()
        bare in this -> (this - bare + SportCatalog.subSports(type.sport).map { ActivityType(type.sport, it.subSport) } - type).normalized()
        else -> this - type
    }
}

internal fun Set<ActivityType>.normalized(): Set<ActivityType> {
    val bareSports = filter { it.subSport == null }.mapTo(mutableSetOf()) { it.sport }
    val withoutCovered = filterTo(mutableSetOf()) { it.subSport == null || it.sport !in bareSports }
    val complete =
        withoutCovered
            .filter { it.subSport != null }
            .groupBy { it.sport }
            .filter { (sport, picked) ->
                val all = SportCatalog.subSports(sport).map { it.subSport }
                all.isNotEmpty() && picked.mapTo(mutableSetOf()) { it.subSport }.containsAll(all)
            }
            .keys
    return withoutCovered.filterNot { it.sport in complete }.toSet() + complete.map { ActivityType(it) }
}
