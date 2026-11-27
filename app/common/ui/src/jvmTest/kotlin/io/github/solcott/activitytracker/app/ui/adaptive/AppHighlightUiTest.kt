package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.preview.AppPreview
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AppHighlightUiTest {
    @Test
    fun bleedKeepsTextInLineAndReachesPastTheColumn() = runComposeUiTest {
        setContent {
            AppPreview {
                Column(Modifier.width(300.dp).padding(horizontal = 16.dp)) {
                    Text("Heading")
                    Text(
                        "Row",
                        Modifier.fillMaxWidth().highlight(placement = HighlightPlacement.Bleed).testTag(ROW).clickable {}.highlightInset(),
                    )
                }
            }
        }
        val heading = onNodeWithText("Heading").getBoundsInRoot()
        val row = onNodeWithTag(ROW, useUnmergedTree = true).getBoundsInRoot()
        // The highlight's start is its inset before the heading's, and its width the column's plus both insets.
        assertEquals(heading.left - AppHighlight.Horizontal, row.left)
        assertEquals(268.dp + AppHighlight.Horizontal * 2, row.right - row.left)
    }

    @Test
    fun gutterSitsInsideTheListAndPutsTextAtTheGutter() = runComposeUiTest {
        setContent {
            AppPreview {
                Column(Modifier.width(300.dp).testTag(LIST)) {
                    Text(
                        "Row",
                        Modifier.fillMaxWidth().highlight(placement = HighlightPlacement.Gutter).testTag(ROW).clickable {}.highlightInset(),
                    )
                }
            }
        }
        val list = onNodeWithTag(LIST).getBoundsInRoot()
        val row = onNodeWithTag(ROW, useUnmergedTree = true).getBoundsInRoot()
        assertEquals(list.left + AppHighlight.Gutter, row.left)
        assertEquals(300.dp - AppHighlight.Gutter * 2, row.right - row.left)
    }

    private companion object {
        const val ROW = "row"
        const val LIST = "list"
    }
}
