package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent
import io.github.solcott.activitytracker.app.detail.ActivityDetailPresenter
import io.github.solcott.activitytracker.app.detail.ActivityDetailState
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** The detail screen's comments: paging, sending, deleting, hiding and unhiding, and the count through all of them. */
class ActivityDetailCommentsTest : ActivityDetailTestBase() {
    @Test
    fun commentsOnAFollowersActivityOnceForADoubleTap() = runTest {
        visibility = "followers"
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
            )
        }) {
            val loaded = awaitLoaded()
            loaded.draftComment.setTextAndPlaceCursorAtEnd("Nice")
            loaded.eventSink(ActivityDetailEvent.SendComment)
            loaded.eventSink(ActivityDetailEvent.SendComment)
            // The write is queued off the composition; it's the only thing that reaches the outbox here.
            r.db.outbox().count(alice.value).first { it > 0 }
            val queued = r.queuedComments()
            assertEquals(listOf(AthleteSpace.INTERACTIONS), queued.map { it.first }, "one comment, in the interactions space")
            assertTrue(queued.single().second!!.contains("\"audience\":\"followers\""))
            assertEquals("", loaded.draftComment.text.toString())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSessionWhoseScopeLacksCommentsIsAskedToSignInAgainAndKeepsTheText() = runTest {
        visibility = "followers"
        // Signed in before the interactions space's declaration listed comments.
        val r = repos(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}&collection=${Nsid.FEED_LIKE}")
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
            )
        }) {
            val loaded = awaitLoaded()
            loaded.draftComment.setTextAndPlaceCursorAtEnd("Nice")
            loaded.eventSink(ActivityDetailEvent.SendComment)
            val refused = awaitUntil { (it as? ActivityDetailState.Loaded)?.commentError != null } as ActivityDetailState.Loaded
            assertEquals(UiError(UiError.Kind.SignInAgain, null), refused.commentError)
            assertEquals("Nice", refused.draftComment.text.toString(), "what was typed isn't lost")
            assertEquals(emptyList(), r.queuedComments())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The deletes of comments Alice has queued, as (space key or null for the repo, rkey). */
    private suspend fun Repos.queuedCommentDeletes() =
        db.outbox()
            .pending(alice.value)
            .filter { it.collection == Nsid.FEED_COMMENT && it.operation == OutboxProcessor.OP_DELETE }
            .map { it.space?.substringAfterLast('/') to it.rkey }

    @Test
    fun deletesOwnCommentAtOnceAndOnceForADoubleConfirm() = runTest {
        visibility = "followers"
        commentsFromAppView = listOf(comment(staleAlice, alice.value, "3kc1"), comment(carol, "did:plc:carol", "3kc2"))
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
            )
        }) {
            val loaded = awaitUntil { (it as? ActivityDetailState.Loaded)?.comments?.roots?.size == 2 } as ActivityDetailState.Loaded
            assertEquals(alice, loaded.viewerDid)
            val mine = loaded.comments.roots.first().uri
            loaded.eventSink(ActivityDetailEvent.DeleteComment(mine))
            loaded.eventSink(ActivityDetailEvent.DeleteComment(mine))
            val after = awaitUntil { (it as? ActivityDetailState.Loaded)?.comments?.roots?.size == 1 } as ActivityDetailState.Loaded
            assertEquals(listOf("did:plc:carol"), after.comments.roots.map { it.author.did.value }, "gone at once, and the count with it")
            r.db.outbox().count(alice.value).first { it >= 2 }
            assertEquals(listOf(null to "3kc1", AthleteSpace.INTERACTIONS to "3kc1"), r.queuedCommentDeletes(), "one pair of deletes")

            // A reload while the AppView still lists it keeps it hidden.
            after.eventSink(ActivityDetailEvent.Retry)
            val reloaded = awaitUntil { it is ActivityDetailState.Loaded && it.comments.roots.size == 1 } as ActivityDetailState.Loaded
            assertEquals(listOf("did:plc:carol"), reloaded.comments.roots.map { it.author.did.value })
            assertEquals(1, r.feed.activity(AtUri(activityUri)).commentCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedDeletePutsTheCommentBackWithTheError() = runTest {
        commentsFromAppView = listOf(comment(carol, "did:plc:carol", "3kc0"), comment(staleAlice, alice.value, "3kc1"))
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
            )
        }) {
            val loaded = awaitUntil { (it as? ActivityDetailState.Loaded)?.comments?.roots?.size == 2 } as ActivityDetailState.Loaded
            // Queueing the delete fails once the database is gone.
            r.db.close()
            loaded.eventSink(ActivityDetailEvent.DeleteComment(loaded.comments.roots.last().uri))
            val failed = awaitUntil { (it as? ActivityDetailState.Loaded)?.commentError != null } as ActivityDetailState.Loaded
            assertEquals(UiError.Kind.DeleteComment, failed.commentError?.kind)
            assertEquals(loaded.comments.roots, failed.comments.roots, "back where it was")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The comment hides Alice has queued, as (operation, space key or null for the repo). */
    private suspend fun Repos.queuedHides() =
        db.outbox()
            .pending(alice.value)
            .filter { it.collection == Nsid.FEED_COMMENT_HIDE }
            .map { it.operation to it.space?.substringAfterLast('/') }

    @Test
    fun hidesOthersCommentOnOwnActivityAtOnceAndOnceForADoubleTap() = runTest {
        author = staleAlice
        commentsFromAppView = listOf(comment(bob, "did:plc:bob", "3kc1"), comment(carol, "did:plc:carol", "3kc2"))
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
            )
        }) {
            val loaded = awaitUntil { (it as? ActivityDetailState.Loaded)?.comments?.roots?.size == 2 } as ActivityDetailState.Loaded
            assertTrue(loaded.isOwn)
            val bobs = loaded.comments.roots.first().uri
            loaded.eventSink(ActivityDetailEvent.HideComment(bobs))
            loaded.eventSink(ActivityDetailEvent.HideComment(bobs))
            val hidden =
                awaitUntil { (it as? ActivityDetailState.Loaded)?.comments?.roots?.first()?.hidden == true } as ActivityDetailState.Loaded
            assertEquals(listOf(true, null), hidden.comments.roots.map { it.hidden }, "still listed for the athlete, marked")
            r.db.outbox().count(alice.value).first { it >= 1 }
            assertEquals(listOf(OutboxProcessor.OP_PUT to null), r.queuedHides(), "one hide, in the repo")

            // A reload while the AppView still shows it keeps it hidden, and out of the count.
            hidden.eventSink(ActivityDetailEvent.Retry)
            val reloaded =
                awaitUntil { it is ActivityDetailState.Loaded && it.comments.roots.firstOrNull()?.hidden == true }
                    as ActivityDetailState.Loaded
            assertEquals(listOf(true, null), reloaded.comments.roots.map { it.hidden })
            assertEquals(1, r.feed.activity(AtUri(activityUri)).commentCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unhidesAHiddenComment() = runTest {
        author = staleAlice
        visibility = "followers"
        commentsFromAppView = listOf(comment(bob, "did:plc:bob", "3kc1", hidden = true), comment(carol, "did:plc:carol", "3kc2"))
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
            )
        }) {
            val loaded = awaitUntil { (it as? ActivityDetailState.Loaded)?.comments?.roots?.size == 2 } as ActivityDetailState.Loaded
            assertEquals(true, loaded.comments.roots.first().hidden)
            loaded.eventSink(ActivityDetailEvent.UnhideComment(loaded.comments.roots.first().uri))
            val shown =
                awaitUntil { (it as? ActivityDetailState.Loaded)?.comments?.roots?.first()?.hidden == null } as ActivityDetailState.Loaded
            assertEquals(listOf(null, null), shown.comments.roots.map { it.hidden })
            r.db.outbox().count(alice.value).first { it >= 2 }
            assertEquals(
                listOf(OutboxProcessor.OP_DELETE to null, OutboxProcessor.OP_DELETE to AthleteSpace.INTERACTIONS),
                r.queuedHides(),
                "deleted from both places",
            )
            assertEquals(2, r.feed.activity(AtUri(activityUri)).commentCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSessionWhoseScopeLacksHidesIsAskedToSignInAgain() = runTest {
        author = staleAlice
        visibility = "followers"
        commentsFromAppView = listOf(comment(bob, "did:plc:bob", "3kc1"))
        // Signed in before the interactions space's declaration listed comment hides.
        val r = repos(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}&collection=${Nsid.FEED_COMMENT}")
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
            )
        }) {
            val loaded = awaitUntil { (it as? ActivityDetailState.Loaded)?.comments?.roots?.size == 1 } as ActivityDetailState.Loaded
            loaded.eventSink(ActivityDetailEvent.HideComment(loaded.comments.roots.single().uri))
            val refused = awaitUntil { (it as? ActivityDetailState.Loaded)?.commentError != null } as ActivityDetailState.Loaded
            assertEquals(UiError(UiError.Kind.SignInAgain, null), refused.commentError)
            assertEquals(listOf(null), refused.comments.roots.map { it.hidden }, "not marked")
            assertEquals(emptyList(), r.queuedHides())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedHidePutsTheMarkBackWithTheError() = runTest {
        author = staleAlice
        commentsFromAppView = listOf(comment(bob, "did:plc:bob", "3kc1"))
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
            )
        }) {
            val loaded = awaitUntil { (it as? ActivityDetailState.Loaded)?.comments?.roots?.size == 1 } as ActivityDetailState.Loaded
            // Queueing the hide fails once the database is gone.
            r.db.close()
            loaded.eventSink(ActivityDetailEvent.HideComment(loaded.comments.roots.single().uri))
            val failed = awaitUntil { (it as? ActivityDetailState.Loaded)?.commentError != null } as ActivityDetailState.Loaded
            assertEquals(UiError.Kind.HideComment, failed.commentError?.kind)
            assertEquals(loaded.comments.roots, failed.comments.roots, "not marked")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun loadsCommentsAPageAtATimeAsTheViewerScrolls() = runTest {
        commentsFromAppView = manyComments(45)
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
            )
        }) {
            val first = awaitUntil { it.loaded()?.comments?.roots?.size == 30 && it.loaded()?.comments?.isLoading == false }.loaded()!!
            assertEquals(45, first.activity.commentCount, "the count is the whole activity's")
            assertEquals(false, first.comments.endReached)

            commentsGate = CompletableDeferred()
            first.eventSink(ActivityDetailEvent.LoadMoreComments)
            awaitUntil { it.loaded()?.comments?.isLoading == true }
            // Reaching the end again while the page loads loads it once.
            first.eventSink(ActivityDetailEvent.LoadMoreComments)
            commentsGate!!.complete(Unit)
            val all = awaitUntil { it.loaded()?.comments?.roots?.size == 45 && it.loaded()?.comments?.isLoading == false }.loaded()!!
            assertEquals(true, all.comments.endReached)
            assertEquals((0 until 45).map { "3kc" + it.toString().padStart(3, '0') }, all.comments.roots.map { it.uri.rkey })

            all.eventSink(ActivityDetailEvent.LoadMoreComments)
            assertEquals(listOf(null, "30"), pageRequests, "nothing more once every page is loaded")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aPageThatFailsKeepsWhatsListedAndCanBeTriedAgain() = runTest {
        commentsFromAppView = manyComments(45)
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
            )
        }) {
            val first = awaitUntil { it.loaded()?.comments?.roots?.size == 30 && it.loaded()?.comments?.isLoading == false }.loaded()!!
            failComments = true
            first.eventSink(ActivityDetailEvent.LoadMoreComments)
            val failed = awaitUntil { it.loaded()?.comments?.error != null }.loaded()!!
            assertEquals(UiError.Kind.LoadComments, failed.comments.error?.kind)
            assertEquals(30, failed.comments.roots.size, "what's listed stays")

            failed.eventSink(ActivityDetailEvent.LoadMoreComments)
            failComments = false
            failed.eventSink(ActivityDetailEvent.RetryComments)
            val all = awaitUntil { it.loaded()?.comments?.roots?.size == 45 }.loaded()!!
            assertEquals(null, all.comments.error)
            assertEquals(listOf(null, "30", "30"), pageRequests, "no page asked for while failed but the retry")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theActivityShowsWhenItsCommentsFail() = runTest {
        commentsFromAppView = manyComments(3)
        failComments = true
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
            )
        }) {
            val failed = awaitUntil { it.loaded()?.comments?.error != null }.loaded()!!
            assertEquals(3, failed.activity.commentCount)
            assertEquals(emptyList(), failed.comments.roots)
            failComments = false
            failed.eventSink(ActivityDetailEvent.RetryComments)
            assertEquals(3, awaitUntil { it.loaded()?.comments?.roots?.size == 3 }.loaded()!!.activity.commentCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSentCommentShowsAtOnceAfterTheLoadedOnesUntilTheAppViewListsIt() = runTest {
        commentsFromAppView = manyComments(45)
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
            )
        }) {
            val first = awaitUntil { it.loaded()?.comments?.roots?.size == 30 && it.loaded()?.comments?.isLoading == false }.loaded()!!
            first.draftComment.setTextAndPlaceCursorAtEnd("Mine")
            first.eventSink(ActivityDetailEvent.SendComment)
            val sent = awaitUntil { it.loaded()?.sentComments?.size == 1 && it.loaded()?.activity?.commentCount == 46L }.loaded()!!
            assertEquals("Mine", sent.sentComments.single().text)
            assertEquals(46, sent.activity.commentCount, "counted at once")
            assertEquals(30, sent.comments.roots.size, "nothing loaded again")

            // Loading more before the AppView has it lists the new page before it.
            sent.eventSink(ActivityDetailEvent.LoadMoreComments)
            val more = awaitUntil { it.loaded()?.comments?.roots?.size == 45 }.loaded()!!
            assertEquals(listOf("Mine"), more.sentComments.map { it.text })
            assertEquals(46, more.activity.commentCount)

            // The AppView indexes it: after a reload it's listed once, in its place, and counted once.
            val rkey = sent.sentComments.single().uri.rkey!!
            commentsFromAppView = commentsFromAppView + comment(staleAlice, alice.value, rkey)
            more.eventSink(ActivityDetailEvent.Retry)
            val reloaded =
                awaitUntil {
                    it.loaded()?.comments?.roots?.size == 30 &&
                        it.loaded()?.comments?.isLoading == false &&
                        it.loaded()?.sentComments?.isEmpty() == true
                }
                    .loaded()!!
            assertEquals(46, reloaded.activity.commentCount)
            reloaded.eventSink(ActivityDetailEvent.LoadMoreComments)
            val end = awaitUntil { it.loaded()?.comments?.endReached == true }.loaded()!!
            assertEquals(rkey, end.comments.roots.last().uri.rkey)
            assertEquals(1, end.comments.roots.count { it.uri.rkey == rkey } + end.sentComments.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aPageListingASentCommentDoesntMoveTheCountAgain() = runTest {
        commentsFromAppView = manyComments(45)
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
            )
        }) {
            val first = awaitUntil { it.loaded()?.comments?.roots?.size == 30 && it.loaded()?.comments?.isLoading == false }.loaded()!!
            first.draftComment.setTextAndPlaceCursorAtEnd("Mine")
            first.eventSink(ActivityDetailEvent.SendComment)
            val sent = awaitUntil { it.loaded()?.sentComments?.size == 1 && it.loaded()?.activity?.commentCount == 46L }.loaded()!!

            // The AppView indexes it, and the next page lists it: shown in its place, and still counted once.
            commentsFromAppView = commentsFromAppView + comment(staleAlice, alice.value, sent.sentComments.single().uri.rkey!!)
            sent.eventSink(ActivityDetailEvent.LoadMoreComments)
            val listed = awaitUntil { it.loaded()?.comments?.roots?.size == 46 && it.loaded()?.sentComments?.isEmpty() == true }.loaded()!!
            assertEquals(emptyList(), listed.sentComments)
            assertEquals(46, listed.activity.commentCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deletingAJustSentCommentTakesItOffTheListAndTheCount() = runTest {
        commentsFromAppView = manyComments(2)
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
            )
        }) {
            val loaded = awaitUntil { it.loaded()?.comments?.roots?.size == 2 }.loaded()!!
            loaded.draftComment.setTextAndPlaceCursorAtEnd("Oops")
            loaded.eventSink(ActivityDetailEvent.SendComment)
            val sent = awaitUntil { it.loaded()?.sentComments?.size == 1 && it.loaded()?.activity?.commentCount == 3L }.loaded()!!
            sent.eventSink(ActivityDetailEvent.DeleteComment(sent.sentComments.single().uri))
            // Gone from the list and the count; nothing else moves it.
            val gone = awaitUntil { it.loaded()?.sentComments?.isEmpty() == true && it.loaded()?.activity?.commentCount == 2L }.loaded()!!
            assertEquals(2, gone.comments.roots.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aDeleteOnTheSecondPageHoldsAfterAReload() = runTest {
        commentsFromAppView = manyComments(45, own = 40)
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
            )
        }) {
            val first = awaitUntil { it.loaded()?.comments?.roots?.size == 30 && it.loaded()?.comments?.isLoading == false }.loaded()!!
            first.eventSink(ActivityDetailEvent.LoadMoreComments)
            val all = awaitUntil { it.loaded()?.comments?.roots?.size == 45 }.loaded()!!
            val mine = all.comments.roots.single { it.author.did == alice }.uri
            all.eventSink(ActivityDetailEvent.DeleteComment(mine))
            val after = awaitUntil { it.loaded()?.comments?.roots?.size == 44 && it.loaded()?.activity?.commentCount == 44L }.loaded()!!

            // Reloaded before the AppView drops it: the count still leaves it out, though it's on a page not loaded yet.
            after.eventSink(ActivityDetailEvent.Retry)
            val reloaded = awaitUntil { it.loaded()?.comments?.roots?.size == 30 && it.loaded()?.comments?.isLoading == false }.loaded()!!
            assertEquals(44, reloaded.activity.commentCount)
            reloaded.eventSink(ActivityDetailEvent.LoadMoreComments)
            val end = awaitUntil { it.loaded()?.comments?.endReached == true }.loaded()!!
            assertTrue(end.comments.roots.none { it.uri == mine })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aHideOnTheSecondPageHoldsAfterAReload() = runTest {
        author = staleAlice
        commentsFromAppView = manyComments(45)
        val r = repos()
        presenterTestOf({
            ActivityDetailPresenter(
                screen,
                FakeNavigator(screen),
                r.feed,
                r.activities,
                r.profiles,
                localLikes,
                r.manager,
                r.exporter,
                r.units,
                r.suggestions,
            )
        }) {
            val first = awaitUntil { it.loaded()?.comments?.roots?.size == 30 && it.loaded()?.comments?.isLoading == false }.loaded()!!
            first.eventSink(ActivityDetailEvent.LoadMoreComments)
            val all = awaitUntil { it.loaded()?.comments?.roots?.size == 45 }.loaded()!!
            val target = all.comments.roots[35].uri
            all.eventSink(ActivityDetailEvent.HideComment(target))
            val hidden =
                awaitUntil { it.loaded()?.comments?.roots?.get(35)?.hidden == true && it.loaded()?.activity?.commentCount == 44L }
                    .loaded()!!

            hidden.eventSink(ActivityDetailEvent.Retry)
            val reloaded = awaitUntil { it.loaded()?.comments?.roots?.size == 30 && it.loaded()?.comments?.isLoading == false }.loaded()!!
            assertEquals(44, reloaded.activity.commentCount)
            reloaded.eventSink(ActivityDetailEvent.LoadMoreComments)
            val end = awaitUntil { it.loaded()?.comments?.endReached == true }.loaded()!!
            assertEquals(true, end.comments.roots.single { it.uri == target }.hidden, "marked when its page is listed")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
