package io.github.solcott.activitytracker.data.feed

import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.TimelineItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Quotes the viewer posted or deleted this session (feed/quote-posts), as a change to each quoted post's count, keyed by its URI. The
 * AppView only counts a quote once it comes back through Jetstream, so its reads are laid over with this until then: a count is shown at
 * least as high as when the viewer first quoted it this session plus their sent quotes, and at most as high after a delete.
 */
class LocalQuotes {
    val overrides: StateFlow<Map<String, QuoteCountChange>>
        field = MutableStateFlow(emptyMap())

    /** The viewer quoted [post]: its count is one higher until the AppView counts the quote. */
    fun sent(post: PostView) = overrides.update { held ->
        val change = held[post.uri.value] ?: QuoteCountChange(post.quoteCount, 0)
        held + (post.uri.value to change.copy(delta = change.delta + 1))
    }

    /**
     * The viewer deleted a quote of [quoted] they posted this session: its count goes back down. Others' counts are left to the AppView.
     */
    fun deleted(quoted: String) = overrides.update { held ->
        val change = held[quoted] ?: return@update held
        held + (quoted to change.copy(delta = change.delta - 1))
    }

    /** Puts the changes back as they were before a write that failed to queue. */
    fun restore(previous: Map<String, QuoteCountChange>) {
        overrides.value = previous
    }

    fun applyTo(post: PostView): PostView = post.withQuoteOverrides(overrides.value)
}

/** How the viewer's quotes this session move a post's count: from [base], the count when they first quoted it, by [delta]. */
data class QuoteCountChange(val base: Long, val delta: Int)

/** This post with its quote count moved by the viewer's own quotes this session ([LocalQuotes]), until the AppView's count passes it. */
fun PostView.withQuoteOverrides(overrides: Map<String, QuoteCountChange>): PostView {
    val change = overrides[uri.value] ?: return this
    val expected = (change.base + change.delta).coerceAtLeast(0)
    val shown = if (change.delta >= 0) maxOf(quoteCount, expected) else minOf(quoteCount, expected)
    return copy(quoteCount = shown)
}

/** A timeline item's post with its quote count as [withQuoteOverrides] leaves it; anything else as it is. */
internal fun TimelineItem.withQuoteOverrides(overrides: Map<String, QuoteCountChange>): TimelineItem =
    when (this) {
        is TimelineItem.Post -> TimelineItem.Post(post.withQuoteOverrides(overrides))
        is TimelineItem.Repost -> TimelineItem.Repost(reposted.copy(post = reposted.post.withQuoteOverrides(overrides)))
        else -> this
    }
