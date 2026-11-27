package io.github.solcott.activitytracker.app.interactions

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.highlight
import io.github.solcott.activitytracker.app.ui.adaptive.highlightInset
import io.github.solcott.activitytracker.app.ui.string

/**
 * A subject's comments in brief, under its social card's counts row (feed/comments): the first comments, or "Loading comments…" while they
 * load, "View all" at the end while there are more, and "Add a comment", shaped like a field but a button, so the page never holds a
 * keyboard. Each opens the Comments screen. The count is the row's, so it isn't repeated here.
 */
@Composable
fun CommentsPreviewBlock(preview: CommentsPreview, modifier: Modifier = Modifier) {
    val openAll = { preview.eventSink(CommentsPreviewEvent.OpenAll) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        preview.loadingText?.let { Loading(it.string()) }
        preview.rows.forEach { row -> PreviewRow(row, openAll) }
        preview.viewAll?.let { viewAll ->
            AppTextButton(onClick = openAll, Modifier.align(Alignment.End).padding(end = 4.dp)) { Text(viewAll.string()) }
        }
        AddCommentRow(
            preview.addComment.string(),
            onClick = { preview.eventSink(CommentsPreviewEvent.Compose) },
            Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp),
        )
    }
}

/** "Loading comments…" beside a small spinner, in place of the comments until their first page answers. */
@Composable
private fun Loading(text: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp).clearAndSetSemantics {
            contentDescription = text
            liveRegion = LiveRegionMode.Polite
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppProgressIndicator(small = true)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One comment in brief: its author and when it was written over the start of its text, two lines at most. */
@Composable
private fun PreviewRow(row: CommentPreviewRow, onClick: () -> Unit) {
    Column(
        // On the block's own edges: its other rows sit the highlight's inset in, so this one's text lines up with theirs.
        Modifier.fillMaxWidth().highlight().clickable(role = Role.Button, onClick = onClick).highlightInset(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CommentAuthorLine(row.author, row.createdAt)
        Text(row.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** "Add a comment" in an outlined box with a field's corner, which opens the Comments screen ready to type. */
@Composable
private fun AddCommentRow(hint: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(FIELD_CORNER)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Material's outlined field corner, so the row reads as the field it opens. */
private val FIELD_CORNER = 4.dp
