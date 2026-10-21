package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ActivityViewerState
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.dataresult.DataError
import io.github.solcott.dataresult.Origin
import io.github.solcott.dataresult.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

/** `FeedRepository.activityOutcome`: the activity read as a stream for the screens that load it (ui/content-loading). */
class ActivityOutcomeTest {
    private val pds = FakeAthletePds()
    private val bob = Did("did:plc:bob")

    private fun view(comments: Long) =
        ActivityView(
            uri = AtUri.of(bob, Nsid.FEED_ACTIVITY, "3kbob"),
            cid = Cid("bafyrec"),
            author = ProfileViewBasic(bob, Handle("bob.test")),
            record =
                ActivityRecord(
                    sport = Sport.RUN,
                    startedAt = Instant.parse("2026-10-14T07:00:00Z"),
                    elapsedTime = 1800,
                    createdAt = Instant.parse("2026-10-14T07:31:00Z"),
                ),
            likeCount = 0,
            commentCount = comments,
            indexedAt = Instant.parse("2026-10-14T07:32:00Z"),
            visibility = Visibility.PUBLIC,
            viewer = ActivityViewerState(),
        )

    @Test
    fun loadsThenAnswersWithTheActivityAndUpdatesTheCache() = runTest {
        val s = pds.session()
        val cached = s.cache(view(comments = 1))
        val served = view(comments = 4)
        pds.served = GetActivity.Output(served, emptyList())

        val outcomes = s.feed.activityOutcome(cached.uri).toList()

        assertEquals(listOf(Outcome.Loading, Outcome.Data(served, Origin.Network)), outcomes)
        assertEquals(4, s.cached(cached).commentCount, "the feed's copy is the one just read")
    }

    @Test
    fun noConnectionIsNetwork() = runTest {
        val s = pds.session()
        pds.appViewOffline = true

        assertEquals(
            Outcome.Error(DataError.Network, Origin.Network),
            s.feed.activityOutcome(view(comments = 0).uri).toList().last(),
        )
    }
}
