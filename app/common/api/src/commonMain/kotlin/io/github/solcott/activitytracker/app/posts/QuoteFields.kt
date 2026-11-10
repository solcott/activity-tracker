package io.github.solcott.activitytracker.app.posts

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.lexicon.app.PostRecord
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.textfield.TextFieldState
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * A host's quote sheet and delete confirmation (feed/quote-posts), filled by `rememberQuoting`. The sheet is open while [quoting] names the
 * post being quoted: [text] is the athlete's words, [audience] one of [audiences], which are no wider than the quoted post's visibility and
 * start on it. [confirmDelete] is the athlete's own quote while Delete asks to confirm. Changes go to [eventSink].
 */
@Immutable
data class QuoteFields(
    val quoting: PostView? = null,
    /** The holder's own while a post is being quoted; the shared [CLOSED_TEXT] by default, so default fields are equal. */
    val text: TextFieldState = CLOSED_TEXT,
    val audience: String = Visibility.PUBLIC,
    val audiences: ImmutableList<String> = persistentListOf(Visibility.PUBLIC),
    /** Why Followers and Mutuals aren't offered, when they're narrower than the quoted post but this session can't write them. */
    val publicOnly: Boolean = false,
    val posting: Boolean = false,
    val error: UiError? = null,
    val confirmDelete: PostView? = null,
    val eventSink: (QuoteEvent) -> Unit = {},
) {
    /**
     * The text, trimmed, fits a post. Counted in code points, so an emoji is one, not its two UTF-16 units; a code point is never more than
     * a grapheme, which the AppView counts, so this never lets through text it would refuse.
     */
    val textValid: Boolean
        get() = text.text.trim().count { !it.isLowSurrogate() } <= MAX_TEXT_LENGTH

    /** Post is offered: the text, trimmed, isn't empty and fits, and nothing is being posted. */
    val canPost: Boolean
        get() = quoting != null && text.text.isNotBlank() && textValid && !posting

    companion object {
        const val MAX_TEXT_LENGTH = PostRecord.MAX_TEXT_GRAPHEMES

        /** The text of a closed sheet, which is never shown or edited. */
        private val CLOSED_TEXT = textFieldStateOf()
    }
}

sealed interface QuoteEvent {
    data class AudienceChanged(val audience: String) : QuoteEvent

    /** Posts the quote, then closes the sheet. */
    data object Post : QuoteEvent

    data object Cancel : QuoteEvent

    /** The delete confirmation's answer. */
    data class ConfirmDelete(val confirmed: Boolean) : QuoteEvent
}
