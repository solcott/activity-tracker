package io.github.solcott.activitytracker.android.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.solcott.activitytracker.app.posts.QuoteSheet
import io.github.solcott.activitytracker.app.posts.QuoteSheetSamples

/** The quote sheet's content (feed/quote-posts): quoting Bob's Followers post, with words typed and the audience on Followers. */
@PreviewTest
@PreviewComponentVariants
@Composable
private fun QuoteSheetOpen() {
    ComponentPreview {
        QuoteSheet(QuoteSheetSamples.open, quotedAuthor = "Bob", quotedText = "Everyone at the trailhead already calls it this.")
    }
}
