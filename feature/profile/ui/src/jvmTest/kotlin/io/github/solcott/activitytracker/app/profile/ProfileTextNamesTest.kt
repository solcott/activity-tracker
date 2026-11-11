package io.github.solcott.activitytracker.app.profile

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allPluralStringResources
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Every string and plural the profile's texts choose exists (dev/shared-display-text): `SharedTextNamesTest` can't see a feature's
 * contracts.
 */
class ProfileTextNamesTest {
    @Test
    fun everyNameTheProfileTextsChooseHasItsString() {
        val details = ProfileDetails(birthDate = "1990-04-12", gender = "female", height = 1803, weight = 61_500)
        val own = ProfileState(isSelf = true, activityInviteCount = 2, followRequestCount = 3)
        val texts =
            listOf(
                ProfileTexts.title(null),
                ProfileTexts.title(ProfileView(Did("did:plc:bob"), Handle("bob.test"))),
                ProfileTexts.activities(1),
                ProfileTexts.followers(1),
                ProfileTexts.following(1),
                ProfileTexts.mute(false),
                ProfileTexts.mute(true),
                ProfileTexts.block(false),
                ProfileTexts.block(true),
                ProfileTexts.blockConfirmTitle(AppText.written("Bob")),
                ProfileTexts.blockConfirmMessage,
                ProfileTexts.blockedOnBluesky,
                ProfileTexts.activitiesEmpty,
                ProfileTexts.more,
                ProfileTexts.settings,
            ) +
                UnitSystem.entries.mapNotNull { ProfileTexts.facts(details, Instant.parse("2026-01-01T00:00:00Z"), it) } +
                FollowLabel.entries.map(ProfileTexts::follow) +
                ProfileTexts.ownEntries(own).map { it.label }
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources || it in Res.allPluralStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Named -> listOf(text.name) + text.args.flatMap(::names)
            is AppText.Plural -> listOf(text.name) + text.args.flatMap(::names)
            else -> emptyList()
        }
}
