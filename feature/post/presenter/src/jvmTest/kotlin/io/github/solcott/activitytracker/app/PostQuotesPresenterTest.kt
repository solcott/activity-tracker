package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.post.PostQuotesEvent
import io.github.solcott.activitytracker.app.post.PostQuotesPresenter
import io.github.solcott.activitytracker.app.post.PostQuotesScreen
import io.github.solcott.activitytracker.app.post.PostQuotesState
import io.github.solcott.activitytracker.app.posts.PostEvent
import io.github.solcott.activitytracker.app.posts.QuoteEvent
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.uistate.LoadStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/** The quotes list's presenter (feed/quote-posts): loading and paging, NotFound, and the viewer's new quote first. */
class PostQuotesPresenterTest : ActivityTestBase() {
    private val postUri = "at://did:plc:bob/${Nsid.FEED_POST}/3kpost"
    private val screen = PostQuotesScreen(postUri)

    /** [author]'s public quote of the post, under [rkey]. */
    private fun quote(rkey: String, author: String = "did:plc:carol") =
        """{"${'$'}type":"${Nsid.FEED_DEFS}#postView","uri":"at://$author/${Nsid.FEED_POST}/$rkey","cid":"bafy$rkey",""" +
            """"author":{"did":"$author","handle":"c.test"},"text":"Yes $rkey",""" +
            """"embed":{"${'$'}type":"${Nsid.FEED_DEFS}#postQuoteEmbedView","quoted":""" +
            """{"${'$'}type":"${Nsid.FEED_DEFS}#quotedPostUnavailable","uri":"$postUri"}},""" +
            """"createdAt":"2026-11-07T10:00:00Z","indexedAt":"2026-11-07T10:00:00Z"}"""

    private fun presenterOf(r: Repos): @Composable () -> PostQuotesState = {
        PostQuotesPresenter(screen, FakeNavigator(screen), r.feed, r.activities, r.profiles, r.names, r.blocks, r.reports, r.quotes)
    }

    @Test
    fun loadsAndPagesTheQuotes() = runTest {
        quotesFromAppView = listOf(quote("3kq1"), quote("3kq2"), quote("3kq3"))
        val r = repos()
        presenterTestOf(presenterOf(r)) {
            val first = awaitUntil { it.quotes.items.size == 2 && it.quotes.refresh is LoadStatus.Idle }
            first.eventSink(PostQuotesEvent.LoadMore)
            val all = awaitUntil { it.quotes.items.size == 3 }
            assertEquals(listOf("3kq1", "3kq2", "3kq3"), all.quotes.items.map { it.uri.rkey })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aPostTheViewerMayNotSeeIsNotFound() = runTest {
        val r = repos()
        presenterTestOf(presenterOf(r)) {
            awaitUntil { it.notFound }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theViewersNewQuoteIsListedFirst() = runTest {
        quotesFromAppView = listOf(quote("3kq1"))
        val r = repos()
        val quoted =
            AtJson.decodeFromString(PostView.serializer(), quote("3kq1"))
                .copy(uri = io.github.solcott.activitytracker.lexicon.AtUri(postUri))
        presenterTestOf(presenterOf(r)) {
            val loaded = awaitUntil { it.quotes.items.size == 1 }
            loaded.eventSink(PostQuotesEvent.Post(PostEvent.Quote(quoted)))
            val sheet = awaitUntil { it.quote.quoting != null }.quote
            assertEquals(Visibility.PUBLIC, sheet.audience)
            sheet.text.setTextAndPlaceCursorAtEnd("Mine")
            sheet.eventSink(QuoteEvent.Post)
            // Its own page lists it once the list loads again; a new screen here, as coming back to it does.
            awaitUntil { it.quote.quoting == null }
            cancelAndIgnoreRemainingEvents()
        }
        presenterTestOf(
            {
                PostQuotesPresenter(
                    PostQuotesScreen(postUri),
                    FakeNavigator(screen),
                    r.feed,
                    r.activities,
                    r.profiles,
                    r.names,
                    r.blocks,
                    r.reports,
                    r.quotes,
                )
            },
        ) {
            val listed = awaitUntil { it.quotes.items.size == 2 }
            assertEquals(alice, listed.quotes.items.first().author.did)
            assertEquals("Mine", listed.quotes.items.first().text)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
