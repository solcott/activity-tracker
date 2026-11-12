package io.github.solcott.activitytracker.app.invites

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.InviteStatus
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every string the invites texts choose exists (dev/shared-display-text): `SharedTextNamesTest` can't see a feature's contracts. */
class InvitesTextNamesTest {
    @Test
    fun everyNameTheInvitesTextsChooseHasItsString() {
        val invite = fakeInvite()
        val texts =
            ActivityInvitesTab.entries.flatMap { listOf(ActivityInvitesTexts.tab(it), ActivityInvitesTexts.empty(it)) } +
                listOf(ActivityInvitesTexts.from(invite), ActivityInvitesTexts.summary(invite, UnitSystem.Metric)) +
                listOf(
                    AcceptInviteTexts.title,
                    AcceptInviteTexts.intro(invite),
                    AcceptInviteTexts.overlap,
                    AcceptInviteTexts.gone,
                    AcceptInviteTexts.accept(false),
                    AcceptInviteTexts.accept(true),
                    AcceptInviteTexts.decline,
                ) +
                listOf(InviteStatus.PENDING, InviteStatus.JOINED).flatMap {
                    val invitee = fakeInvitee(status = it)
                    listOf(AddPeopleTexts.status(invitee), AddPeopleTexts.remove(invitee))
                }
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Named -> listOf(text.name) + text.args.flatMap(::names)
            else -> emptyList()
        }
}
