package io.github.solcott.activitytracker.app.visibility

import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** feed/detail-visibility: a form's "Match the activity" and "Use my defaults", and when each is offered. */
class VisibilityResetsTest {
    private val offered = listOf(DetailCategory.HEART_RATE, DetailCategory.MAP)

    private val defaults =
        VisibilityChoice(
            Visibility.FOLLOWERS,
            mapOf(DetailCategory.HEART_RATE to Visibility.SELF, DetailCategory.MAP to Visibility.FOLLOWERS),
        )

    private fun fields(choice: VisibilityChoice, withDefaults: VisibilityChoice? = defaults) =
        fieldsOf(choice, canChoose = true, eventSink = {}, offered = offered).copy(defaults = withDefaults.defaultsFor(offered))

    @Test
    fun matchingTheActivitySetsEveryOfferedCategoryToItsTier() {
        val choice = VisibilityChoice(Visibility.PUBLIC).withCategory(DetailCategory.HEART_RATE, Visibility.SELF)
        assertTrue(fields(choice).offersMatchActivity)

        val matched = choice.after(VisibilityEvent.MatchActivity, offered, defaults)
        assertEquals(Visibility.PUBLIC, matched.tiers[DetailCategory.HEART_RATE])
        assertFalse(fields(matched).offersMatchActivity)
    }

    @Test
    fun defaultsApplyToTheCategoriesCappedAtTheActivityWhichStays() {
        val choice = VisibilityChoice(Visibility.MUTUALS)
        assertTrue(fields(choice).offersDefaults)

        val applied = choice.after(VisibilityEvent.UseDefaults, offered, defaults)
        assertEquals(Visibility.MUTUALS, applied.activity)
        assertEquals(Visibility.SELF, applied.tiers[DetailCategory.HEART_RATE])
        // Followers by default, but never wider than the activity.
        assertEquals(Visibility.MUTUALS, applied.tiers[DetailCategory.MAP])
        assertFalse(fields(applied).offersDefaults)
    }

    @Test
    fun neitherIsOfferedWithoutSomethingToChangeOrWhenTheSessionCantChoose() {
        assertFalse(fields(VisibilityChoice(Visibility.PUBLIC), withDefaults = null).offersDefaults)
        assertEquals(
            VisibilityChoice(Visibility.PUBLIC),
            VisibilityChoice(Visibility.PUBLIC).after(VisibilityEvent.UseDefaults, offered, null),
        )

        val narrowed = VisibilityChoice(Visibility.PUBLIC).withCategory(DetailCategory.MAP, Visibility.SELF)
        val locked = fieldsOf(narrowed, canChoose = false, eventSink = {}, offered = offered).copy(defaults = defaults.defaultsFor(offered))
        assertFalse(locked.offersMatchActivity)
        assertFalse(locked.offersDefaults)
    }
}
