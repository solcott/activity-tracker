package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.TimelineItem
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Reposts in the timeline (feed/reposts, feed/timeline-order): a post comes once, at the latest of itself and its reposts there. */
class TimelineRepostsTest : PostTestBase() {
    private val dan = "did:plc:dan"
    private val sharedAt = now - 2.hours

    private fun repost(author: String, rkey: String, at: Instant, tier: String = Visibility.PUBLIC) =
        put(
            author,
            Nsid.FEED_REPOST,
            rkey,
            """{"subject":{"uri":"${postUri()}","cid":"bafy"},"createdAt":"$at"}""",
            if (tier == Visibility.PUBLIC) Visibility.PUBLIC else AthleteSpace.INTERACTIONS,
        )

    /** Alice shares her proposal publicly two hours ago; dan is a known account too. */
    private fun aliceShares(tier: String = Visibility.PUBLIC) {
        aliceProposes()
        commit(dan, Nsid.ACTOR_PROFILE, "self", """{"displayName":"d"}""")
        share(tier = tier, createdAt = sharedAt)
    }

    /** [viewer]'s timeline as "post", "repost:<reposter>+<others>@<position>" or an activity's rkey. */
    private fun items(viewer: String, params: GetTimeline.Params = GetTimeline.Params(limit = 50)): List<String> =
        timeline(viewer, params).feed.map { it.label() }

    private fun TimelineItem.label(): String =
        when (this) {
            is TimelineItem.Post -> "post"
            is TimelineItem.Repost -> {
                val reason = reposted.reason
                "repost:${reason.by.did.value.substringAfterLast(':')}+${reason.others}@${reason.createdAt.toEpochMilliseconds()}"
            }
            is TimelineItem.Activity -> view.uri.rkey!!
            else -> "other"
        }

    private fun mute(author: String, subject: String) =
        spaceWrite(space(AthleteSpace.INTERACTIONS, author), Nsid.GRAPH_MUTE, subject, """{"subject":"$subject","createdAt":"$now"}""")

    private fun activityJson(at: Instant) = """{"sport":"ride","startedAt":"${at - 1.hours}","elapsedTime":3600,"createdAt":"$at"}"""

    @Test
    fun aFollowedReposterBringsInAPostBySomeoneTheViewerDoesntFollow() {
        aliceShares()
        follow(carol, bob)
        val at = now - 10.minutes
        repost(bob, "3kr1", at)
        assertEquals(listOf("repost:bob+0@${at.toEpochMilliseconds()}"), items(carol))
        val item = timeline(carol).reposted.single()
        assertEquals(postUri(), item.post.uri.value)
        assertEquals(1, item.post.repostCount)
    }

    @Test
    fun theViewersOwnRepostIsInTheirTimeline() {
        aliceShares()
        val at = now - 10.minutes
        repost(bob, "3kr1", at)
        assertEquals(listOf("repost:bob+0@${at.toEpochMilliseconds()}"), items(bob))
    }

    @Test
    fun aPostAndALaterRepostComeOnceAtTheRepost() {
        aliceShares()
        follow(carol, alice)
        follow(carol, bob)
        val at = now - 10.minutes
        repost(bob, "3kr1", at)
        assertEquals(listOf("repost:bob+0@${at.toEpochMilliseconds()}"), items(carol))
    }

    @Test
    fun undoingTheRepostReturnsThePostToItsOwnPosition() {
        aliceShares()
        follow(carol, alice)
        follow(carol, bob)
        put(alice, Nsid.FEED_ACTIVITY, "3ka1", activityJson(now - 1.hours))
        repost(bob, "3kr1", now - 10.minutes)
        remove(bob, Nsid.FEED_REPOST, "3kr1")
        assertEquals(listOf("3ka1", "post"), items(carol))
    }

    @Test
    fun aTieGoesToThePostItself() {
        aliceShares()
        follow(carol, alice)
        follow(carol, bob)
        repost(bob, "3kr1", sharedAt)
        assertEquals(listOf("post"), items(carol))
    }

    @Test
    fun severalRepostersNameTheLatestAndCountTheOthers() {
        aliceShares()
        follow(carol, bob)
        follow(carol, dan)
        repost(bob, "3kr1", now - 20.minutes)
        val at = now - 10.minutes
        repost(dan, "3kr2", at)
        assertEquals(listOf("repost:dan+1@${at.toEpochMilliseconds()}"), items(carol))
    }

    @Test
    fun aRepostersFollowerOutsideThePostsAudienceSeesNothing() {
        aliceShares(tier = Visibility.FOLLOWERS)
        follow(bob, alice)
        follow(dan, bob)
        repost(bob, "3kr1", now - 10.minutes, tier = Visibility.FOLLOWERS)
        assertEquals(emptyList(), items(dan))
        assertEquals(listOf("repost:bob+0@${(now - 10.minutes).toEpochMilliseconds()}"), items(bob))
    }

    @Test
    fun aMutedReposterOrAuthorLeavesTheTimeline() {
        aliceShares()
        follow(carol, bob)
        follow(dan, bob)
        repost(bob, "3kr1", now - 10.minutes)
        mute(carol, bob)
        mute(dan, alice)
        assertEquals(emptyList(), items(carol))
        assertEquals(emptyList(), items(dan))
    }

    @Test
    fun aBlockedReposterOrAuthorLeavesTheTimeline() {
        aliceShares()
        follow(carol, bob)
        follow(dan, bob)
        repost(bob, "3kr1", now - 10.minutes)
        block(carol, bob)
        block(dan, alice)
        assertEquals(emptyList(), items(carol))
        assertEquals(emptyList(), items(dan))
    }

    @Test
    fun aMutedSecondReposterIsNotCountedAmongTheOthers() {
        aliceShares()
        follow(carol, bob)
        follow(carol, dan)
        val at = now - 20.minutes
        repost(bob, "3kr1", at)
        repost(dan, "3kr2", now - 10.minutes)
        mute(carol, dan)
        assertEquals(listOf("repost:bob+0@${at.toEpochMilliseconds()}"), items(carol))
    }

    @Test
    fun pagingShowsTheRepostedPostOnExactlyOnePage() {
        aliceShares()
        follow(carol, alice)
        follow(carol, bob)
        follow(carol, dan)
        repeat(4) { put(alice, Nsid.FEED_ACTIVITY, "3ka$it", activityJson(now - (it * 30 + 5).minutes)) }
        repost(bob, "3kr1", now - 50.minutes)
        repost(dan, "3kr2", now - 40.minutes)
        val seen = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page = timeline(carol, GetTimeline.Params(limit = 1, cursor = cursor))
            seen += page.feed.map { it.label() }
            cursor = page.cursor
        } while (cursor != null)
        assertEquals(listOf("3ka0", "3ka1", "repost:dan+1@${(now - 40.minutes).toEpochMilliseconds()}", "3ka2", "3ka3"), seen)
    }

    @Test
    fun aFilteredTimelineHasNoReposts() {
        aliceShares()
        follow(carol, bob)
        repost(bob, "3kr1", now - 10.minutes)
        assertTrue(timeline(carol, GetTimeline.Params(type = listOf("ride"))).reposted.isEmpty())
        assertTrue(timeline(carol, GetTimeline.Params(since = now - 48.hours)).reposted.isEmpty())
    }
}
