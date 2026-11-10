package io.github.solcott.activitytracker.app.visibility

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.Visibility

/**
 * What the visibility choices say that follows a rule (`VisibilityFields`), chosen here for the shared and the native UI
 * (dev/shared-display-text).
 */
object VisibilityTexts {
    /** A detail category's name as its picker shows it: cadence alone when the fields hold no running form, else its usual name. */
    fun categoryName(fields: VisibilityFields, category: String): AppText =
        if (category == DetailCategory.CADENCE && !fields.runningForm) AppText.named("detail_category_cadence_only")
        else LabelText.detailCategory(category)

    /** What a detail category covers, for the four whose name doesn't say it; null for the others. */
    fun categoryHelp(category: String): AppText? =
        when (category) {
            DetailCategory.START_TIME -> AppText.named("detail_category_startTime_help")
            DetailCategory.PHYSIOLOGY -> AppText.named("detail_category_physiology_help")
            DetailCategory.DIVE -> AppText.named("detail_category_dive_help")
            DetailCategory.GEAR -> AppText.named("detail_category_gear_help")
            else -> null
        }

    /**
     * Under a choice of [selected]: that anyone can follow the athlete, so Followers means anyone who chooses to, unless they approve
     * followers ([approvesFollowers]). Null for any other choice.
     */
    fun followersNote(selected: String?, approvesFollowers: Boolean): AppText? =
        if (selected == Visibility.FOLLOWERS && !approvesFollowers) AppText.named("visibility_followers_open_note") else null

    /** Why [fields] can't be chosen: a session to sign in again, or a PDS without spaces, where signing in again wouldn't help. */
    fun lockedNote(fields: VisibilityFields): AppText =
        AppText.named(if (fields.spacesUnsupported) "activity_visibility_unsupported" else "activity_visibility_sign_in_again")
}
