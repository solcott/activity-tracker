package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.data.moderation.ModerationDecision
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic

/**
 * A feed card's author line (activities, import batches, posts): [author]'s avatar, blurred when their account's labels say so (the card's
 * own labels blur only its media, never the avatar), beside their name, anything [beside] it (a visibility badge), and the lines [below] it
 * (when, and with whom). With [onClick] the line opens the athlete.
 */
@Composable
fun AuthorHeader(
    author: ProfileViewBasic,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    beside: @Composable RowScope.() -> Unit = {},
    below: @Composable ColumnScope.() -> Unit = {},
) {
    val name = author.shownName
    Row(
        modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick).handCursor() else Modifier),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(author.avatar, name, 32.dp, blur = ModerationDecision.mediaOf(null, author.labels))
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.labelLarge)
                beside()
            }
            below()
        }
    }
}
