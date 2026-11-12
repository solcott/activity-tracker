package io.github.solcott.activitytracker.app.sport

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.text.TextSource
import io.github.solcott.activitytracker.data.sport.SportCatalog
import io.github.solcott.activitytracker.data.sport.SportCategory
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Whether this type shows under its sport in a picker listing [types]: a sub-sport whose own sport is listed beside it. A sub-sport filed
 * away from its sport (Indoor Row under Gym) shows as a row of its own. The rule every type picker lays its rows out by, in both UIs.
 */
fun ActivityType.nestsIn(types: Collection<ActivityType>): Boolean = subSport != null && ActivityType(sport) in types

/** A row of a type picker: [type], and the sub-sports that show under it ([ActivityType.nestsIn]), in the catalog's order. */
@Immutable data class SportTypeRow(val type: ActivityType, val nested: ImmutableList<ActivityType>)

/** The types a picker lists under one [category], in the catalog's order. */
@Immutable
data class SportTypeGroup(val category: SportCategory, val types: ImmutableList<ActivityType>) {
    /** The category's heading. */
    val label: AppText
        get() = LabelText.sportCategory(category)

    /** The group's rows: each type that doesn't nest, with the types that nest under it. */
    val rows: ImmutableList<SportTypeRow>
        get() =
            types
                .filterNot { it.nestsIn(types) }
                .map { top ->
                    SportTypeRow(top, types.filter { it.nestsIn(types) && top.subSport == null && it.sport == top.sport }.toImmutableList())
                }
                .toImmutableList()
}

/**
 * The groups a type picker offers, by category in the catalog's order (dev/shared-display-text): chosen here for the shared and the native
 * UI.
 */
object SportTypeGroups {
    /** Every activity type by category, sub-sports included: a gear default can be for a sport or for one of its sub-sports. */
    val allTypes: ImmutableList<SportTypeGroup> =
        SportCatalog.categories.map { SportTypeGroup(it.category, it.types.distinct().toImmutableList()) }.toImmutableList()

    /** Each category's bare sports, leaving out categories with none: gear is for a sport, not a sub-sport. */
    val gearSports: ImmutableList<SportTypeGroup> =
        SportCatalog.categories
            .map { group -> SportTypeGroup(group.category, group.types.filter { it.subSport == null }.distinct().toImmutableList()) }
            .filter { it.types.isNotEmpty() }
            .toImmutableList()

    /**
     * The groups a single-choice type picker lists: every catalog type [matches] keeps, by category, leaving out categories with none, and
     * types that can't use GPS when [outdoorOnly] (recording).
     */
    fun matching(outdoorOnly: Boolean, matches: (ActivityType) -> Boolean): ImmutableList<SportTypeGroup> =
        SportCatalog.categories
            .map { group ->
                SportTypeGroup(group.category, group.types.filter { offered(it, outdoorOnly) && matches(it) }.distinct().toImmutableList())
            }
            .filter { it.types.isNotEmpty() }
            .toImmutableList()

    /** [matching] for [query] ([SportTypeSearch.matches], the labels read by [source]): every type while nothing is typed. */
    fun matching(query: String, source: TextSource, outdoorOnly: Boolean): ImmutableList<SportTypeGroup> =
        matching(outdoorOnly) { SportTypeSearch.matches(it, query, source) }

    /**
     * The choices offered under a picker's [selected] type: its sport's generic form, then the sport's sub-sports (without the ones that
     * can't use GPS when [outdoorOnly]); none when the sport has no sub-sports left. Each is named by [LabelText.activityType].
     */
    fun subSportChoices(selected: ActivityType, outdoorOnly: Boolean): ImmutableList<ActivityType> {
        val subs =
            SportCatalog.subSports(selected.sport).map { ActivityType(selected.sport, it.subSport) }.filter { offered(it, outdoorOnly) }
        return if (subs.isEmpty()) persistentListOf() else (listOf(ActivityType(selected.sport)) + subs).toImmutableList()
    }

    private fun offered(type: ActivityType, outdoorOnly: Boolean): Boolean = !(outdoorOnly && SportCatalog.isIndoor(type))
}
