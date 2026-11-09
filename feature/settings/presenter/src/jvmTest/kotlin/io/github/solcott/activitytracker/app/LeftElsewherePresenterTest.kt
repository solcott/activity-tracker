package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.app.settings.LeftElsewhereEvent
import io.github.solcott.activitytracker.app.settings.LeftElsewherePresenter
import io.github.solcott.activitytracker.app.settings.LeftElsewhereState
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.auth.EndedSession
import io.github.solcott.activitytracker.data.auth.SignOutCheck
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** After the athlete left on another device (auth/leaving): sign out at once with nothing pending, else discard or rejoin. */
class LeftElsewherePresenterTest {
    private val fixture = SessionTestFixture()

    private suspend fun leftElsewhere(
        before: suspend (SessionTestFixture.Repos) -> Unit = {},
        block: suspend app.cash.turbine.ReceiveTurbine<LeftElsewhereState>.(SessionTestFixture.Repos) -> Unit,
    ) {
        val repos = fixture.repos()
        before(repos)
        repos.clients.markLeft()
        presenterTestOf(
            {
                LeftElsewherePresenter(
                    repos.clients,
                    repos.leaveJob,
                    SignOutCheck(repos.uploads, repos.saves, repos.exports, repos.clients),
                    repos.recorder,
                )
            },
        ) {
            block(repos)
        }
    }

    /** Bob's tablet recorded a ride while it couldn't reach the PDS. */
    private suspend fun SessionTestFixture.Repos.queueARide() {
        fixture.offline = true
        activities.publish(ActivityDraft(ActivityType(Sport.RIDE), "Ride", null, Instant.parse("2026-10-14T06:12:00Z"), 30.minutes))
        fixture.offline = false
    }

    @Test
    fun withNothingPendingItSignsOutWithTheLeftNote() = runTest {
        leftElsewhere { repos ->
            repos.auth.session.first { it == null }
            assertEquals(EndedSession.Reason.Left, repos.auth.lastEnd.value?.reason)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aPendingRideIsCountedAndDiscardingSignsOut() = runTest {
        leftElsewhere(before = { it.queueARide() }) { repos ->
            val asking = awaitUntil { !it.checking }
            assertEquals(1, asking.changes)
            assertNotNull(repos.auth.session.value, "it asks first")

            asking.eventSink(LeftElsewhereEvent.Discard)
            asking.eventSink(LeftElsewhereEvent.Discard)
            repos.auth.session.first { it == null }
            assertEquals(EndedSession.Reason.Left, repos.auth.lastEnd.value?.reason)
            assertEquals(emptyList(), fixture.putRkeys, "the ride is never sent")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun rejoiningLetsTheAppSendAgain() = runTest {
        leftElsewhere(before = { it.queueARide() }) { repos ->
            awaitUntil { !it.checking }.eventSink(LeftElsewhereEvent.Rejoin)
            repos.clients.accountLeft.first { !it }
            assertFalse(repos.clients.accountLeft.value)
            assertNotNull(repos.auth.session.value)
            // The app shell then runs the outbox again, which uploads the ride.
            repos.pacer.runUntilIdleOrPaused()
            assertEquals(0, repos.uploads.summary.first().own)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
