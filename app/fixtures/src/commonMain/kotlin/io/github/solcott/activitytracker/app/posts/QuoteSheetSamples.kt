package io.github.solcott.activitytracker.app.posts

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

    val all: Map<String, QuoteFields>
        get() = mapOf("open" to open, "empty" to empty)
}
