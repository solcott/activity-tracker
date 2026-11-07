package io.github.solcott.activitytracker.app.posts

import androidx.compose.foundation.text.input.TextFieldState
import io.github.solcott.activitytracker.app.fixtures.fakePost
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlinx.collections.immutable.persistentListOf

/** The quote sheet (feed/quote-posts), quoting Bob's Followers post. */
object QuoteSheetSamples {
    /** Words typed, on the quoted post's audience; a new text each time. */
    val open: QuoteFields
        get() =
            QuoteFields(
                quoting = fakePost(),
                text = TextFieldState("Yes, and the lower half should be its own segment."),
                audience = Visibility.FOLLOWERS,
                audiences = persistentListOf(Visibility.FOLLOWERS, Visibility.MUTUALS),
            )

    /** Just opened, with nothing typed yet. */
    val empty: QuoteFields
        get() = open.copy(text = TextFieldState())

    val all: Map<String, QuoteFields>
        get() = mapOf("open" to open, "empty" to empty)
}
