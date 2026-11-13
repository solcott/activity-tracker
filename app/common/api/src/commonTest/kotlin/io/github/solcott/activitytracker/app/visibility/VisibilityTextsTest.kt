package io.github.solcott.activitytracker.app.visibility

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the visibility choices say, chosen once for both UIs (dev/shared-display-text). */
class VisibilityTextsTest {
    @Test
    fun aKnownCategoryIsNamedByItsToken() {
        assertEquals(AppText.named("detail_category_heartRate"), LabelText.detailCategory(DetailCategory.HEART_RATE))
        assertEquals(DetailCategory.all.map { AppText.named("detail_category_$it") }, DetailCategory.all.map(LabelText::detailCategory))
    }

    @Test
    fun anUnknownCategoryShowsAsItIs() {
        assertEquals(AppText.written("lock_description"), LabelText.detailCategory("lock_description"))
    }

    @Test
    fun cadenceAloneWithoutRunningForm() {
        assertEquals(
            AppText.named("detail_category_cadence_only"),
            VisibilityTexts.categoryName(VisibilityFields(runningForm = false), DetailCategory.CADENCE),
        )
        assertEquals(
            AppText.named("detail_category_cadence"),
            VisibilityTexts.categoryName(VisibilityFields(runningForm = true), DetailCategory.CADENCE),
        )
        assertEquals(
            AppText.named("detail_category_power"),
            VisibilityTexts.categoryName(VisibilityFields(runningForm = false), DetailCategory.POWER),
        )
    }

    @Test
    fun fourCategoriesHaveHelp() {
        val helped = DetailCategory.all.filter { VisibilityTexts.categoryHelp(it) != null }
        assertEquals(listOf(DetailCategory.START_TIME, DetailCategory.PHYSIOLOGY, DetailCategory.DIVE, DetailCategory.GEAR), helped)
        assertEquals(AppText.named("detail_category_gear_help"), VisibilityTexts.categoryHelp(DetailCategory.GEAR))
        assertNull(VisibilityTexts.categoryHelp("unknown"))
    }

    @Test
    fun followersWithoutApprovalHasItsNote() {
        assertEquals(AppText.named("visibility_followers_open_note"), VisibilityTexts.followersNote(Visibility.FOLLOWERS, false))
        assertNull(VisibilityTexts.followersNote(Visibility.FOLLOWERS, true))
        assertNull(VisibilityTexts.followersNote(Visibility.MUTUALS, false))
        assertNull(VisibilityTexts.followersNote(null, false))
    }

    @Test
    fun theActivityNoteIsTheHelpUntilTheTierIsLocked() {
        assertEquals(AppText.named("activity_visibility_help"), VisibilityTexts.activityNote(VisibilityFields(canChoose = true)))
        assertEquals(AppText.named("activity_visibility_sign_in_again"), VisibilityTexts.activityNote(VisibilityFields(canChoose = false)))
        assertEquals(
            AppText.named("activity_visibility_unsupported"),
            VisibilityTexts.activityNote(VisibilityFields(canChoose = false, spacesUnsupported = true)),
        )
    }

    @Test
    fun theLockedNoteSaysWhy() {
        assertEquals(AppText.named("activity_visibility_sign_in_again"), VisibilityTexts.lockedNote(VisibilityFields(canChoose = false)))
        assertEquals(
            AppText.named("activity_visibility_unsupported"),
            VisibilityTexts.lockedNote(VisibilityFields(canChoose = false, spacesUnsupported = true)),
        )
    }

    @Test
    fun theActivityIsOfferedEveryVisibilityAndACategoryOnlyNarrower() {
        val fields = VisibilityFields(activity = Visibility.FOLLOWERS)
        assertEquals(Visibility.all, fields.activityOptions)
        assertEquals(listOf(Visibility.FOLLOWERS, Visibility.MUTUALS, Visibility.SELF), fields.categoryOptions)
    }
}
