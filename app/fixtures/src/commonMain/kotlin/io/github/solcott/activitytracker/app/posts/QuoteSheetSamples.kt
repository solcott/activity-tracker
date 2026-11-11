package io.github.solcott.activitytracker.app.posts

import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.fixtures.fakePost
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import kotlinx.collections.immutable.persistentListOf

/** The quote sheet (feed/quote-posts), quoting Bob's Followers post. */
object QuoteSheetSamples {
    /** Words typed, on the quoted post's audience; a new text each time. */
    val open: QuoteFields
        get() =
            QuoteFields(
                quoting = fakePost(),
                text = textFieldStateOf("Yes, and the lower half should be its own segment."),
                audience = Visibility.FOLLOWERS,
                audiences = persistentListOf(Visibility.FOLLOWERS, Visibility.MUTUALS),
            )

    /** Just opened, with nothing typed yet. */
    val empty: QuoteFields
        get() = open.copy(text = textFieldStateOf())

    /** Words longer than a post holds: Post held back, and the note under the field. */
    val tooLong: QuoteFields
        get() = open.copy(text = textFieldStateOf("a".repeat(QuoteFields.MAX_TEXT_LENGTH + 1)))

    /** Followers and Mutuals aren't offered, since this session can't write them. */
    val publicOnly: QuoteFields
        get() = open.copy(audience = Visibility.PUBLIC, audiences = persistentListOf(Visibility.PUBLIC), publicOnly = true)

    /** The quote couldn't be posted. */
    val failed: QuoteFields
        get() = open.copy(error = UiError(UiError.Kind.QuotePost))

    /** Being posted. */
    val posting: QuoteFields
        get() = open.copy(posting = true)

    val all: Map<String, QuoteFields>
        get() = mapOf("open" to open, "empty" to empty)

    /** Beyond [all]: too long, public only, failed and posting (ios-native-post-card). */
    val variants: Map<String, QuoteFields>
        get() = mapOf("tooLong" to tooLong, "publicOnly" to publicOnly, "failed" to failed, "posting" to posting)
}
