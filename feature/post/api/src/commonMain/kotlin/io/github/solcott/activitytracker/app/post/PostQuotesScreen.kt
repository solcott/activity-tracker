package io.github.solcott.activitytracker.app.post

import androidx.compose.runtime.Immutable
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.serialization.CircuitSerializable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.posts.PostEvent
import io.github.solcott.activitytracker.app.posts.QuoteFields
import io.github.solcott.activitytracker.app.report.Reporting
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.uistate.PagedState
import io.github.solcott.uistate.pagedStateOf

/** The quotes of a post the viewer is shown (feed/quote-posts), newest first. [uri] is the quoted `feed.post`'s AT-URI. */
@CircuitSerializable(SessionScope::class) data class PostQuotesScreen(val uri: String) : Screen

/**
 * The quotes list (ui/content-loading): [quotes] pages of post cards, the viewer's own unlisted quotes of the post first. [notFound]: the
 * viewer may no longer see the quoted post, which the page says in place of an error.
 */
@Immutable
data class PostQuotesState(
    val quotes: PagedState<PostView, Unit> = pagedStateOf(),
    val notFound: Boolean = false,
    /** The signed-in athlete, so cards leave likes and reposts off their own quotes and offer Delete on them. */
    val viewerDid: String,
    /** Why the viewer's last like, repost, vote or delete couldn't be done. */
    val error: UiError? = null,
    val reporting: Reporting = Reporting(),
    val quote: QuoteFields = QuoteFields(),
    val eventSink: (PostQuotesEvent) -> Unit,
) : CircuitUiState

sealed interface PostQuotesEvent {
    data object Back : PostQuotesEvent

    /** Loads the next page; ignored while a page is loading, after a failed page, or when there are no more. */
    data object LoadMore : PostQuotesEvent

    /** Loads again what failed to load, and only that: the first page, or the page that failed. */
    data object Retry : PostQuotesEvent

    /** A tap on one of the quotes' cards. */
    data class Post(val event: PostEvent) : PostQuotesEvent
}
