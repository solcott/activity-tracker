package io.github.solcott.activitytracker.app.invites

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.solcott.activitytracker.app.ui.LocalUnitSystem
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.lexicon.app.InviteView

/** Who added the athlete to an invite's activity, and the activity: its title or type, distance and time. */
@Composable
internal fun InviteSummary(invite: InviteView, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            ActivityInvitesTexts.from(invite).string(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(ActivityInvitesTexts.title(invite).string(), style = MaterialTheme.typography.titleMedium)
        Text(ActivityInvitesTexts.summary(invite, LocalUnitSystem.current).string(), style = MaterialTheme.typography.bodyMedium)
    }
}
