package io.github.solcott.activitytracker.app.profile

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.profile.CalendarDate
import io.github.solcott.activitytracker.data.units.HeightConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import kotlin.math.roundToLong
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf

/** Which row of actions a loaded profile shows under its header ([ProfileState.actions]). */
enum class ProfileActions {
    /** The athlete's own profile: edit, starred segments, invites and follow requests ([ProfileTexts.ownEntries]). */
    Own,

    /** The viewer's only block of the athlete is on Bluesky: a note that only Bluesky can remove it, and nothing to tap. */
    BlockedOnBluesky,

    /** The viewer blocks the athlete: Unblock, the one thing to do. */
    Unblock,

    /** Follow, Following or Requested ([ProfileState.followLabel]). */
    Follow,
}

/** What the follow button says ([ProfileState.followLabel]). */
enum class FollowLabel {
    Follow,
    Following,

    /** The follow waits for the athlete's approval; a tap cancels it. */
    Requested,
}

/** One of the athlete's own entries under their header: what it says and the event a tap sends. */
@Immutable data class ProfileEntry(val label: AppText, val event: ProfileEvent)

/** What the profile says, chosen here for the shared and the native UI (dev/shared-display-text). */
object ProfileTexts {
    /** The page's title: the athlete's shown name, or "Profile" until it loads. */
    fun title(profile: ProfileView?): AppText = profile?.let { AppText.written(it.shownName) } ?: AppText.named("profile_title")

    /** The athlete as a sentence names them (the block confirmation): their display name, or their handle with its `@`. */
    fun name(profile: ProfileView): AppText = profile.displayName?.let(AppText::written) ?: LabelText.handle(profile.handle.value)

    /** "12 activities", a missing count as none. */
    fun activities(count: Long?): AppText = countText("profile_activities_count", count)

    /** "3 followers", a missing count as none. */
    fun followers(count: Long?): AppText = countText("profile_followers_count", count)

    /** "5 following", a missing count as none. */
    fun following(count: Long?): AppText = AppText.named("profile_following_count", AppText.written(formatWhole(count ?: 0L)))

    /**
     * The location line: city, region and country, as far as the athlete gave them, joined by commas; null when there's none. The country
     * is named by each platform in the device's language ([countryName], for the details' country code), as the country choice names it.
     */
    fun location(details: ProfileDetails, countryName: String?): AppText? =
        details.location
            ?.let { listOfNotNull(it.locality, it.region, if (it.country != null) countryName else null) }
            ?.joinToString(", ")
            ?.takeIf { it.isNotEmpty() }
            ?.let(AppText::written)

    /** The facts line: age on [now], gender, height and weight in [units], joined by " · "; null when there are none. */
    fun facts(details: ProfileDetails, now: Instant, units: UnitSystem): AppText? {
        val age = details.birthDate?.let { CalendarDate.age(it, now) }
        val facts =
            listOfNotNull(
                age?.let { AppText.Plural("profile_age", it, persistentListOf(AppText.written("$it"))) },
                details.gender?.let(LabelText::gender),
                details.height?.let { height(it, units) },
                details.weight?.let { StatText.weight(it, units) },
            )
        return facts.reduceOrNull { line, next -> AppText.named("profile_facts_pair", line, next) }
    }

    /** A height ([mm]) in whole centimeters, or in feet and inches like `5′ 11″`. */
    fun height(mm: Long, units: UnitSystem): AppText =
        when (units) {
            UnitSystem.Metric -> AppText.named("unit_height_cm", AppText.written("${HeightConversions.toCentimeters(mm).roundToLong()}"))
            UnitSystem.Imperial -> {
                val feetInches = HeightConversions.toFeetInches(mm)
                AppText.named("unit_height_ft_in", AppText.written("${feetInches.feet}"), AppText.written("${feetInches.inches}"))
            }
        }

    /** The follow button's words. */
    fun follow(label: FollowLabel): AppText =
        AppText.named(
            when (label) {
                FollowLabel.Follow -> "profile_follow"
                FollowLabel.Following -> "profile_following"
                FollowLabel.Requested -> "profile_requested"
            },
        )

    /**
     * The athlete's own entries, in order: Edit profile, Starred segments, their pending activity invites when they have some, and their
     * follow requests when they approve followers ("Follow requests (500+)" when there are more than were counted). None on anyone else's.
     */
    fun ownEntries(state: ProfileState): List<ProfileEntry> {
        if (!state.isSelf) return emptyList()
        val invites = state.activityInviteCount
        return listOfNotNull(
            ProfileEntry(AppText.named("profile_edit"), ProfileEvent.EditProfile),
            ProfileEntry(AppText.named("starred_segments_title"), ProfileEvent.OpenStarredSegments),
            ProfileEntry(
                    AppText.Plural("activity_invites_entry", invites, persistentListOf(AppText.written("$invites"))),
                    ProfileEvent.OpenActivityInvites,
                )
                .takeIf { invites > 0 },
            state.followRequestCount?.let { count ->
                val shown = if (state.moreFollowRequests) "$count+" else "$count"
                ProfileEntry(AppText.named("profile_follow_requests", AppText.written(shown)), ProfileEvent.OpenFollowRequests)
            },
        )
    }

    /** The menu's Mute, or Unmute while the viewer mutes the athlete. */
    fun mute(muting: Boolean): AppText = AppText.named(if (muting) "action_unmute" else "action_mute")

    /** The menu's Block, or Unblock while the viewer blocks the athlete. */
    fun block(blocking: Boolean): AppText = AppText.named(if (blocking) "action_unblock" else "action_block")

    /** The block confirmation's title, naming the athlete. */
    fun blockConfirmTitle(name: AppText): AppText = AppText.named("block_confirm_title", name)

    /** What the block confirmation says: they won't be told, and Public activities stay visible outside the app. */
    val blockConfirmMessage: AppText
        get() = AppText.named("block_confirm_message")

    /** The note where only Bluesky can remove the viewer's block. */
    val blockedOnBluesky: AppText
        get() = AppText.named("blocked_on_bluesky")

    /** What the activities say when the athlete has none the viewer may see. */
    val activitiesEmpty: AppText
        get() = AppText.named("profile_activities_none")

    /** The bar menu's spoken name on someone else's profile. */
    val more: AppText
        get() = AppText.named("profile_more")

    /** The bar's Settings on the athlete's own profile. */
    val settings: AppText
        get() = AppText.named("profile_settings")

    private fun countText(name: String, count: Long?): AppText {
        val n = count ?: 0L
        return AppText.Plural(name, n.toInt(), persistentListOf(AppText.written(formatWhole(n))))
    }
}
