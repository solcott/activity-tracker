package io.github.solcott.activitytracker.app.posts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.slack.circuit.retained.rememberRetained
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.rememberSingleFlight
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.feed.QuotePosts
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.textfield.clearText
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * A host's quote sheet and delete confirmation (feed/quote-posts): [fields] goes in the host's state, and its post sink
 * ([rememberPostEventSink]) calls [open] for Quote and [delete] for Delete.
 */
@Stable
class Quoting
internal constructor(
    val fields: QuoteFields,
    private val openSheet: (PostView) -> Unit,
    private val askDelete: (PostView) -> Unit,
) {
    /** Opens the sheet for [post], empty, on its visibility. */
    fun open(post: PostView) = openSheet(post)

    /** Asks to confirm deleting the athlete's own quote [post]. */
    fun delete(post: PostView) = askDelete(post)
}

/**
 * Holds a host's quoting (feed/quote-posts): the sheet offers audiences no wider than the quoted post's, starting on it (Public only when
 * this session can't write posts to its spaces and the post is Public), posts through [quotes] once per tap and closes; a confirmed Delete
 * goes through [quotes] too, then tells [onDelete]. A quote that fails to queue keeps the sheet open with why; a delete that fails goes to
 * [onError].
 */
@Composable
@Suppress("CyclomaticComplexMethod") // One branch per sheet event, each checked against the state it acts on.
fun rememberQuoting(quotes: QuotePosts, onDelete: (PostView) -> Unit = {}, onError: (UiError) -> Unit): Quoting {
    var quoting by rememberRetained { mutableStateOf<PostView?>(null) }
    val text = rememberRetained { textFieldStateOf() }
    var audience by rememberRetained { mutableStateOf(Visibility.PUBLIC) }
    var error by rememberRetained { mutableStateOf<UiError?>(null) }
    var confirmDelete by rememberRetained { mutableStateOf<PostView?>(null) }
    val flights = rememberSingleFlight()
    val currentOnError by rememberUpdatedState(onError)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val canWriteSpaces = quotes.canQuoteBeyondPublic
    val audiences = quoting?.let { audiencesFor(it, canWriteSpaces) } ?: QuoteFields().audiences
    val fields =
        QuoteFields(
            quoting = quoting,
            text = text,
            audience = audience,
            audiences = audiences,
            // Only when narrower audiences were dropped for Public: a narrower post's quote keeps its own, and posting says why it can't.
            publicOnly = !canWriteSpaces && quoting != null && Visibility.PUBLIC in audiences,
            posting = flights.isRunning(QuoteEvent.Post),
            error = error,
            confirmDelete = confirmDelete,
        )
    return Quoting(
        fields.copy { event ->
            when (event) {
                is QuoteEvent.AudienceChanged -> if (event.audience in fields.audiences) audience = event.audience
                QuoteEvent.Cancel -> if (!fields.posting) quoting = null
                QuoteEvent.Post -> {
                    val post = quoting
                    if (post != null && fields.canPost && audience in fields.audiences) {
                        error = null
                        val words = text.text.toString()
                        val chosen = audience
                        flights.launch(QuoteEvent.Post) {
                            runSuspendCatching { quotes.quote(post, words, chosen) }
                                .onSuccess {
                                    quoting = null
                                    text.clearText()
                                }
                                .onFailure { error = uiErrorOf(UiError.Kind.QuotePost, it) }
                        }
                    }
                }
                is QuoteEvent.ConfirmDelete -> {
                    val post = confirmDelete
                    confirmDelete = null
                    if (event.confirmed && post != null) {
                        flights.launch(post.uri) {
                            runSuspendCatching { quotes.delete(post) }
                                .onSuccess { currentOnDelete(post) }
                                .onFailure { currentOnError(uiErrorOf(UiError.Kind.DeleteQuote, it)) }
                        }
                    }
                }
            }
        },
        openSheet = { post ->
            quoting = post
            text.clearText()
            error = null
            audience = audiencesFor(post, canWriteSpaces).firstOrNull() ?: Visibility.PUBLIC
        },
        askDelete = { post -> if (post.author.did == quotes.self) confirmDelete = post },
    )
}

/**
 * What a quote of [post] may be shown to: its visibility or narrower, widest first. Without spaces only Public can be written, so a Public
 * post's quote is offered Public alone; a narrower post's keeps its audiences, and posting is refused with the reason a like gets.
 */
private fun audiencesFor(post: PostView, canWriteSpaces: Boolean): ImmutableList<String> {
    val allowed = Visibility.atMost(post.visibilityOrPublic).filter { it in QUOTE_AUDIENCES }
    val writable = if (canWriteSpaces) allowed else allowed.filter { it == Visibility.PUBLIC }
    return writable.ifEmpty { allowed }.toImmutableList()
}

private val QUOTE_AUDIENCES = setOf(Visibility.PUBLIC, Visibility.FOLLOWERS, Visibility.MUTUALS)
