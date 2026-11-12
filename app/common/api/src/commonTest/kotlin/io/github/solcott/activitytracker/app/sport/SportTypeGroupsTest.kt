package io.github.solcott.activitytracker.app.sport

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.data.sport.SportCatalog
import io.github.solcott.activitytracker.data.sport.SportCategory
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the type pickers offer and how their rows nest (dev/shared-display-text). */
class SportTypeGroupsTest {
    private val ride = ActivityType(Sport.RIDE)
    private val gravel = ActivityType(Sport.RIDE, "gravel")

    private fun cycling(groups: List<SportTypeGroup>) = groups.first { it.category == SportCategory.Cycling }

    @Test
    fun aSubSportNestsOnlyBesideItsSport() {
        assertTrue(gravel.nestsIn(listOf(ride, gravel)))
        assertFalse(gravel.nestsIn(listOf(gravel)))
        assertFalse(ride.nestsIn(listOf(ride, gravel)))
    }

    @Test
    fun everyTypeGroupsSubSportsUnderTheirSport() {
        val rows = cycling(SportTypeGroups.allTypes).rows
        val rideRow = rows.first { it.type == ride }
        assertTrue(gravel in rideRow.nested)
        assertTrue(rows.none { it.type == gravel })
    }

    @Test
    fun aSubSportFiledAwayFromItsSportIsARowOfItsOwn() {
        val away =
            SportCatalog.categories.flatMap { group ->
                group.types.filter { it.subSport != null && ActivityType(it.sport) !in group.types }.map { group.category to it }
            }
        val (category, type) = away.first()
        assertTrue(SportTypeGroups.allTypes.first { it.category == category }.rows.any { it.type == type && it.nested.isEmpty() })
    }

    @Test
    fun gearSportsAreBareSportsInCategoriesThatHaveThem() {
        val groups = SportTypeGroups.gearSports
        assertTrue(groups.all { group -> group.types.isNotEmpty() && group.types.all { it.subSport == null } })
        assertTrue(ride in cycling(groups).types)
        assertTrue(cycling(groups).rows.all { it.nested.isEmpty() })
    }

    @Test
    fun aGroupIsHeadedByItsCategory() =
        assertEquals(LabelText.sportCategory(SportCategory.Cycling), cycling(SportTypeGroups.allTypes).label)

    @Test
    fun aListJoinsEachNextNameOn() {
        val a = AppText.written("Ride")
        val b = AppText.written("Run")
        val c = AppText.written("Swim")
        assertNull(AppText.joined(emptyList()))
        assertEquals(a, AppText.joined(listOf(a)))
        assertEquals(AppText.named("text_list_pair", AppText.named("text_list_pair", a, b), c), AppText.joined(listOf(a, b, c)))
    }

    @Test
    fun aSingleChoicePickerListsWhatMatchesAndLeavesOutIndoorTypesWhenRecording() {
        val gravelOnly = SportTypeGroups.matching(outdoorOnly = false) { it == gravel }
        assertEquals(listOf(SportCategory.Cycling), gravelOnly.map { it.category })
        assertEquals(listOf(gravel), gravelOnly.single().types)
        val everything = SportTypeGroups.matching(outdoorOnly = false) { true }.flatMap { it.types }
        val outdoors = SportTypeGroups.matching(outdoorOnly = true) { true }.flatMap { it.types }
        assertTrue(everything.any(SportCatalog::isIndoor))
        assertFalse(outdoors.any(SportCatalog::isIndoor))
    }

    @Test
    fun theSubSportChoicesAreTheGenericSportThenItsSubSports() {
        val choices = SportTypeGroups.subSportChoices(gravel, outdoorOnly = false)
        assertEquals(ride, choices.first())
        assertTrue(gravel in choices)
        assertEquals(SportCatalog.subSports(Sport.RIDE).size + 1, choices.size)
        assertFalse(SportTypeGroups.subSportChoices(ride, outdoorOnly = true).any(SportCatalog::isIndoor))
        assertTrue(SportTypeGroups.subSportChoices(ActivityType(Sport.YOGA), outdoorOnly = false).isEmpty())
    }
}
