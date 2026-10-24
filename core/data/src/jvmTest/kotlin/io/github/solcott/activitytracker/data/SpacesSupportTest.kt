package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.di.SpacesSupport
import io.github.solcott.activitytracker.data.di.SpacesUnsupportedException
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/**
 * Spaces are used only where the athlete's PDS reports Spaces support in `describeServer` (spaces/client-protocol): the session asks once
 * at its start, a PDS without the flag gets its own refusal, and a failed lookup leaves the scope to decide.
 */
class SpacesSupportTest {
    private val pds = FakeAthletePds()

    private fun run(visibility: String) =
        ActivityDraft(
            type = ActivityType(Sport.RUN),
            title = "Run",
            description = null,
            startedAt = Instant.parse("2026-10-14T06:12:00Z"),
            elapsed = 30.minutes,
            visibility = visibility,
        )

    @Test
    fun aSupportedPdsUsesSpaces() = runTest {
        val s = pds.session()
        s.clients.resolveSpacesSupport()

        assertEquals(SpacesSupport.Supported, s.clients.spacesSupport.value)
        assertTrue(s.clients.canUseSpaces)
        s.activities.publish(run(Visibility.FOLLOWERS))
        s.activities.sync()
        assertEquals(
            setOf(Visibility.FOLLOWERS),
            pds.stored.filterValues { r -> r.keys.any { it.startsWith(Nsid.FEED_ACTIVITY + "/") } }.keys,
        )
    }

    @Test
    fun aPdsWithoutTheFlagRefusesNonPublicSavesUpFront() = runTest {
        pds.spacesAlpha = null
        val s = pds.session()
        s.clients.resolveSpacesSupport()

        assertEquals(SpacesSupport.Unsupported, s.clients.spacesSupport.value)
        assertFalse(s.clients.canUseSpaces, "the scope is granted, but the PDS can't host spaces")
        assertFalse(s.clients.canWriteToSpaces(Nsid.FEED_ACTIVITY))
        assertFailsWith<SpacesUnsupportedException> { s.activities.publish(run(Visibility.FOLLOWERS)) }
        s.activities.sync()
        assertEquals(emptyList(), pds.writes, "nothing written")
        assertEquals(0, s.db.outbox().pending(pds.did.value).size, "nor queued")

        // Public features work as before.
        s.activities.publish(run(Visibility.PUBLIC))
        s.activities.sync()
        assertTrue(pds.writes.any { it.startsWith("put ${pds.repo} ${Nsid.FEED_ACTIVITY}/") }, pds.writes.toString())
    }

    @Test
    fun aFalseFlagIsUnsupportedToo() = runTest {
        pds.spacesAlpha = false
        val s = pds.session()
        s.clients.resolveSpacesSupport()
        assertEquals(SpacesSupport.Unsupported, s.clients.spacesSupport.value)
    }

    @Test
    fun aFailedLookupLeavesTheScopeToDecide() = runTest {
        pds.describeServerFailing = true
        val failing = pds.session()
        failing.clients.resolveSpacesSupport()
        assertEquals(SpacesSupport.Unknown, failing.clients.spacesSupport.value)
        assertTrue(failing.clients.canUseSpaces)

        pds.describeServerFailing = false
        pds.describeServerUnreachable = true
        val unreachable = pds.session()
        unreachable.clients.resolveSpacesSupport()
        assertEquals(SpacesSupport.Unknown, unreachable.clients.spacesSupport.value)
        assertTrue(unreachable.clients.canWriteToSpaces(Nsid.FEED_ACTIVITY))
    }

    @Test
    fun aMissingScopeOnASupportedPdsStillAsksToSignInAgain() = runTest {
        val s = pds.session(scope = "atproto transition:generic")
        s.clients.resolveSpacesSupport()

        assertEquals(SpacesSupport.Supported, s.clients.spacesSupport.value)
        val refused = assertFailsWith<XrpcException> { s.activities.publish(run(Visibility.FOLLOWERS)) }
        assertEquals(SpaceErrors.SCOPE_MISSING, refused.error)
        assertEquals(emptyList(), pds.writes)
    }
}
