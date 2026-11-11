package io.github.solcott.activitytracker.app.profile

import io.github.solcott.activitytracker.app.report.Reporting
import io.github.solcott.activitytracker.app.report.ReportingEvent
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.uistate.ContentState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Report on a profile (moderation/reports, "Report entry points"), as both UIs' menus ask it. */
class ProfileReportTest {
    private val bob = ProfileView(Did("did:plc:bob"), Handle("bob.test"), displayName = "Bob")
    private val offered = Reporting(available = true)

    private fun state(profile: ProfileView? = bob, isSelf: Boolean = false, blocking: Boolean = false, reporting: Reporting = offered) =
        ProfileState(content = ContentState(profile), isSelf = isSelf, blocking = blocking, reporting = reporting)

    @Test
    fun someoneElsesProfileReportsTheirAccountAndOffersBlock() {
        assertEquals(ReportingEvent.Open(ReportTarget.Account(bob.did), "Bob", offerBlock = true), state().reportEvent("Bob"))
    }

    @Test
    fun aBlockedProfileIsStillReportedWithNoBlockOffer() {
        assertEquals(
            ReportingEvent.Open(ReportTarget.Account(bob.did), "Bob", offerBlock = false),
            state(blocking = true).reportEvent("Bob"),
        )
    }

    @Test
    fun noReportOnTheirOwnProfileBeforeItLoadsOrWithoutALabeler() {
        assertNull(state(isSelf = true).reportEvent("Bob"))
        assertNull(state(profile = null).reportEvent("Bob"))
        assertNull(state(reporting = Reporting(available = false)).reportEvent("Bob"))
    }
}
