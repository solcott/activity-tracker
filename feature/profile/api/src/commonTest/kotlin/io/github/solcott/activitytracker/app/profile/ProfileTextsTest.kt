package io.github.solcott.activitytracker.app.profile

import io.github.solcott.activitytracker.app.moderation.Revealed
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.TextSource
import io.github.solcott.activitytracker.app.text.resolve
import io.github.solcott.activitytracker.data.moderation.BlurReason
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.LabelView
import io.github.solcott.activitytracker.lexicon.app.Location
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewerState
import io.github.solcott.dataresult.Origin
import io.github.solcott.uistate.ContentState
import io.github.solcott.uistate.LoadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** What the profile says and offers, for both UIs (dev/shared-display-text, graph/follow-requests, graph/blocks, graph/mutes). */
class ProfileTextsTest {
    /** Strings as their names with the arguments after them, so a test reads what was chosen and in what order. */
    private val source =
        object : TextSource {
            override fun string(name: String, args: List<String>): String? =
                when (name) {
                    "profile_facts_pair" -> "${args[0]} · ${args[1]}"
                    "unit_height_ft_in" -> "${args[0]}′ ${args[1]}″"
                    "unit_height_cm" -> "${args[0]} cm"
                    "profile_follow_requests" -> "Follow requests (${args[0]})"
                    else -> (listOf(name) + args).joinToString(" ")
                }

            override fun plural(name: String, quantity: Int, args: List<String>): String =
                (listOf("$name/$quantity") + args).joinToString(" ")

            override fun time(epochSeconds: Long, utcOffsetMinutes: Int?): String = "$epochSeconds"
        }

    private fun AppText.read(): String = resolve(source)

    private val bob = ProfileView(Did("did:plc:bob"), Handle("bob.test"), displayName = "Bob")
    private val follow = AtUri("at://did:plc:alice/app.activitytracker.graph.follow/1")

    private fun state(profile: ProfileView? = bob, isSelf: Boolean = false, blocking: Boolean = false, onBluesky: Boolean = false) =
        ProfileState(
            content = ContentState(profile),
            isSelf = isSelf,
            blocking = blocking,
            blockedOnBluesky = onBluesky,
        )

    @Test
    fun theTitleIsTheShownNameOnceLoaded() {
        assertEquals("Bob", ProfileTexts.title(bob).read())
        assertEquals("bob.test", ProfileTexts.title(bob.copy(displayName = null)).read())
        assertEquals("profile_title", ProfileTexts.title(null).read())
    }

    @Test
    fun countsReadAMissingCountAsNone() {
        assertEquals("profile_activities_count/12 12", ProfileTexts.activities(12).read())
        assertEquals("profile_followers_count/0 0", ProfileTexts.followers(null).read())
        assertEquals("profile_following_count 1,200", ProfileTexts.following(1_200).read().replace(' ', ','))
        assertEquals("profile_following_count 0", ProfileTexts.following(null).read())
    }

    @Test
    fun theLocationJoinsWhatWasGiven() {
        val details = ProfileDetails(location = Location(locality = "Boulder", region = "CO", country = "US"))
        assertEquals("Boulder, CO, United States", ProfileTexts.location(details, "United States")?.read())
        assertEquals("Boulder", ProfileTexts.location(ProfileDetails(location = Location(locality = "Boulder")), "ignored")?.read())
        assertNull(ProfileTexts.location(ProfileDetails(location = Location()), null))
        assertNull(ProfileTexts.location(ProfileDetails(), null))
    }

    @Test
    fun theFactsAreAgeGenderHeightAndWeight() {
        val details = ProfileDetails(birthDate = "1990-04-12", gender = "female", height = 1803, weight = 61_500)
        val now = Instant.parse("2026-04-12T12:00:00Z")
        assertEquals(
            "profile_age/36 36 · gender_female · 180 cm · unit_weight_kg 61.5",
            ProfileTexts.facts(details, now, UnitSystem.Metric)?.read(),
        )
        assertEquals("5′ 11″", ProfileTexts.height(1803, UnitSystem.Imperial).read())
        assertEquals("Agender", ProfileTexts.facts(ProfileDetails(gender = "Agender"), now, UnitSystem.Metric)?.read())
        assertNull(ProfileTexts.facts(ProfileDetails(birthDate = "2030-01-01"), now, UnitSystem.Metric))
    }

    @Test
    fun theFollowButtonFollowsTheViewersFollow() {
        assertEquals(FollowLabel.Follow, state().followLabel)
        assertTrue(state().followPrimary)
        val following = state(bob.copy(viewer = ProfileViewerState(following = follow)))
        assertEquals(FollowLabel.Following, following.followLabel)
        assertFalse(following.followPrimary)
        val requested = state(bob.copy(viewer = ProfileViewerState(following = follow, followRequested = true)))
        assertEquals(FollowLabel.Requested, requested.followLabel)
        assertFalse(requested.followPrimary)
        assertEquals("profile_requested", ProfileTexts.follow(FollowLabel.Requested).read())
    }

    @Test
    fun oneRowOfActionsShows() {
        assertEquals(ProfileActions.Own, state(isSelf = true).actions)
        assertEquals(ProfileActions.BlockedOnBluesky, state(blocking = true, onBluesky = true).actions)
        assertEquals(ProfileActions.Unblock, state(blocking = true).actions)
        assertEquals(ProfileActions.Follow, state().actions)
        assertFalse(state(blocking = true, onBluesky = true).offersBlock)
        assertTrue(state(blocking = true).offersBlock)
        assertFalse(state(blocking = true).showsActivities)
    }

    @Test
    fun theOwnEntriesCountRequestsAndInvites() {
        val own = state(isSelf = true)
        assertEquals(
            listOf(ProfileEvent.EditProfile, ProfileEvent.OpenStarredSegments),
            ProfileTexts.ownEntries(own).map { it.event },
        )
        val busy = own.copy(activityInviteCount = 2, followRequestCount = 500, moreFollowRequests = true)
        assertEquals(
            listOf("profile_edit", "starred_segments_title", "activity_invites_entry/2 2", "Follow requests (500+)"),
            ProfileTexts.ownEntries(busy).map { it.label.read() },
        )
        assertEquals("Follow requests (2)", ProfileTexts.ownEntries(own.copy(followRequestCount = 2)).last().label.read())
        assertEquals(emptyList(), ProfileTexts.ownEntries(state()))
    }

    @Test
    fun theMenuSaysWhatATapDoes() {
        assertEquals("action_mute", ProfileTexts.mute(false).read())
        assertEquals("action_unmute", ProfileTexts.mute(true).read())
        assertEquals("action_block", ProfileTexts.block(false).read())
        assertEquals("action_unblock", ProfileTexts.block(true).read())
        assertEquals("block_confirm_title Bob", ProfileTexts.blockConfirmTitle(AppText.written("Bob")).read())
    }

    @Test
    fun aHiddenAthletesActivitiesWaitUntilRevealed() {
        val hidden = state(bob.copy(labels = listOf(LabelView(Did("did:plc:labeler"), "!hide"))))
        assertTrue(hidden.activitiesHidden)
        assertFalse(hidden.copy(revealed = Revealed(persistentSetOf(hidden.hiddenKey))).activitiesHidden)
        assertFalse(hidden.copy(isSelf = true).activitiesHidden)
        assertFalse(state().activitiesHidden)
    }

    @Test
    fun aLabelledAvatarIsBlurredUntilRevealed() {
        val labelled = state(bob.copy(labels = listOf(LabelView(Did("did:plc:labeler"), BlurReason.entries.first().value))))
        assertEquals(BlurReason.entries.first(), labelled.avatarBlur)
        assertNull(labelled.copy(revealed = Revealed(persistentSetOf("did:plc:bob"))).avatarBlur)
        assertNull(state().avatarBlur)
    }

    @Test
    fun theActivitiesAreEmptyOnlyOnceLoaded() {
        assertFalse(ProfileState(activities = ContentState(persistentListOf())).activitiesEmpty)
        assertTrue(ProfileState(activities = ContentState(persistentListOf(), Origin.Network, LoadStatus.Idle)).activitiesEmpty)
        assertEquals("Hi", state(bob.copy(description = "Hi")).bio)
        assertNull(ProfileState().bio)
    }
}
