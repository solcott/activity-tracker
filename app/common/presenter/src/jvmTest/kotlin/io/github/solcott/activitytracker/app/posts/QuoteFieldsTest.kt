package io.github.solcott.activitytracker.app.posts

import androidx.compose.foundation.text.input.TextFieldState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The quote sheet's text limit (feed/quote-posts), which the AppView counts in graphemes. */
class QuoteFieldsTest {
    private fun fieldsWith(text: String) = QuoteFields(text = TextFieldState(text))

    @Test
    fun anEmojiCountsOnceNotAsItsTwoUtf16Units() {
        // 600 emoji: 1,200 UTF-16 units, but 600 graphemes, well inside the limit.
        assertTrue(fieldsWith("🚴".repeat(600)).textValid)
    }

    @Test
    fun textOverTheLimitIsRefused() {
        assertTrue(fieldsWith("a".repeat(QuoteFields.MAX_TEXT_LENGTH)).textValid)
        assertFalse(fieldsWith("a".repeat(QuoteFields.MAX_TEXT_LENGTH + 1)).textValid)
        assertFalse(fieldsWith("🚴".repeat(QuoteFields.MAX_TEXT_LENGTH + 1)).textValid)
    }
}
