package io.github.solcott.activitytracker.app.invites

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.ui.Format
import io.github.solcott.activitytracker.app.ui.activityTypeLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.activity_invites_from
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.InviteView
import org.jetbrains.compose.resources.stringResource

/** Who added the athlete to an invite's activity, and the activity: its title or type, distance and time. */
@Composable
internal fun InviteSummary(invite: InviteView, modifier: Modifier = Modifier) {
    val record = invite.record
    Column(modifier) {
        Text(
            stringResource(Res.string.activity_invites_from, invite.inviter.shownName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(record.title ?: activityTypeLabel(ActivityType(record.sport, record.subSport)), style = MaterialTheme.typography.titleMedium)
        Text(
            listOfNotNull(record.distance?.let { Format.distance(it.toDouble()) }, Format.duration(record.elapsedTime)).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
