package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.feed.LocalKudos
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ActivityViewerState
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class LocalKudosTest {
    private val bob = Did("did:plc:bob")
    private val mine = AtUri.of(Did("did:plc:alice"), Nsid.FEED_KUDOS, "3kk1")

    private fun view(kudosCount: Long, kudos: AtUri?) =
        ActivityView(
            uri = AtUri.of(bob, Nsid.FEED_ACTIVITY, "3kact1"),
            cid = Cid("bafyact"),
            author = ProfileViewBasic(bob, Handle("bob.test")),
            record =
                ActivityRecord(
                    sport = "run",
                    startedAt = Instant.parse("2026-09-30T07:00:00Z"),
                    elapsedTime = 1800,
                    createdAt = Instant.parse("2026-09-30T07:31:00Z"),
                ),
            kudosCount = kudosCount,
            commentCount = 0,
            indexedAt = Instant.parse("2026-09-30T07:32:00Z"),
            viewer = ActivityViewerState(kudos),
        )

    @Test
    fun staleAppViewReadsReflectARemovedKudos() {
        val local = LocalKudos()
        val stale = view(kudosCount = 1, kudos = mine)
        local.set(stale.uri, null)

        val shown = local.applyTo(stale)
        assertEquals(0, shown.kudosCount)
        assertNull(shown.viewer?.kudos)
        // Applying twice, or once the AppView has caught up, changes nothing.
        assertEquals(shown, local.applyTo(shown))
        assertEquals(view(kudosCount = 0, kudos = null), local.applyTo(view(kudosCount = 0, kudos = null)))
    }

    @Test
    fun staleAppViewReadsReflectAGivenKudos() {
        val local = LocalKudos()
        val stale = view(kudosCount = 2, kudos = null)
        local.set(stale.uri, mine)

        assertEquals(view(kudosCount = 3, kudos = mine), local.applyTo(stale))
    }

    @Test
    fun untouchedActivitiesPassThrough() {
        val stale = view(kudosCount = 1, kudos = mine)
        assertEquals(stale, LocalKudos().applyTo(stale))
    }
}
