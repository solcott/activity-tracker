package io.github.solcott.activitytracker.app.feedfilter

import io.github.solcott.activitytracker.app.text.TextSource
import io.github.solcott.activitytracker.data.sport.SportCategory
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** The type sheet's groups for a search, which both UIs list (feed/feed-filter, "Search shows every match in the type picker"). */
class TypePickerGroupsTest {
    private val ride = ActivityType(Sport.RIDE)
    private val gravel = ActivityType(Sport.RIDE, "gravel")
    private val run = ActivityType(Sport.RUN)
    private val trail = ActivityType(Sport.RUN, "trail")

    private val picker =
        TypePickerState(
            draft = persistentSetOf(gravel),
            quickTypes = persistentListOf(ride),
            categories =
                persistentListOf(
                    CategorySelection(SportCategory.Cycling, persistentListOf(ride, gravel), Selection.Partial),
                    CategorySelection(SportCategory.Running, persistentListOf(run, trail), Selection.None),
                ),
        )

    /** Names no string, so every label falls back to its token. */
    private val tokens =
        object : TextSource {
            override fun string(name: String, args: List<String>): String? = null

            override fun plural(name: String, quantity: Int, args: List<String>): String? = null

            override fun time(epochSeconds: Long, utcOffsetMinutes: Int?) = ""
        }

    @Test
    fun nothingTypedListsEveryCategory() {
        assertEquals(listOf(SportCategory.Cycling, SportCategory.Running), picker.groups("", tokens).map { it.category })
    }

    @Test
    fun aQueryKeepsOnlyCategoriesWithAMatch() {
        val groups = picker.groups("trail", tokens)
        assertEquals(listOf(SportCategory.Running), groups.map { it.category })
        assertEquals(listOf(trail), groups.single().types)
    }

    @Test
    fun aCategoryIsMarkedByHowMuchOfItIsPicked() {
        assertEquals(Selection.Partial, picker.selectionOf(SportCategory.Cycling))
        assertEquals(Selection.None, picker.selectionOf(SportCategory.Running))
    }
}
