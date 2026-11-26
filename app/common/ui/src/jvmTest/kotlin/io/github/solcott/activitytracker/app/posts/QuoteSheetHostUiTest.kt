package io.github.solcott.activitytracker.app.posts

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.solcott.activitytracker.app.fixtures.fakePost
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals

/** The quote delete confirmation (feed/quote-posts): Delete answers yes alone, since a dismiss first would clear what it deletes. */
@OptIn(ExperimentalTestApi::class)
class QuoteSheetHostUiTest {
    @Test
    fun deleteConfirmsWithoutDismissingFirst() = runComposeUiTest {
        val events = mutableListOf<QuoteEvent>()
        setContent { AppPreview { QuoteSheetHost(QuoteFields(confirmDelete = fakePost(), eventSink = { events += it })) } }
        onNodeWithText("Delete").performClick()
        assertEquals(listOf<QuoteEvent>(QuoteEvent.ConfirmDelete(true)), events)
    }

    @Test
    fun cancelAnswersNo() = runComposeUiTest {
        val events = mutableListOf<QuoteEvent>()
        setContent { AppPreview { QuoteSheetHost(QuoteFields(confirmDelete = fakePost(), eventSink = { events += it })) } }
        onNodeWithText("Cancel").performClick()
        assertEquals(listOf<QuoteEvent>(QuoteEvent.ConfirmDelete(false)), events)
    }
}
