package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.Avatar
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.detail_group_leave
import io.github.solcott.activitytracker.composeApp.resources.detail_group_left
import io.github.solcott.activitytracker.composeApp.resources.detail_group_rejoin
import io.github.solcott.activitytracker.composeApp.resources.detail_together_with
import org.jetbrains.compose.resources.stringResource

/**
 * The athletes this activity was done together with (feed/group-activities), each opening their activity, and on the owner's page Leave
 * group or Rejoin group. Nothing shows when there are no partners and nothing to offer.
 */
@Composable
internal fun TogetherWith(state: ActivityDetailPage, modifier: Modifier = Modifier) {
    val own = state.ownGroup
    if (state.partners.isEmpty() && own == null) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.partners.isNotEmpty()) {
            Text(stringResource(Res.string.detail_together_with), style = MaterialTheme.typography.titleMedium)
            state.partners.forEach { partner ->
                val name = partner.author.displayName ?: partner.author.handle.value
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { state.eventSink(ActivityDetailEvent.OpenActivity(partner.uri)) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Avatar(partner.author.avatar, name, 32.dp)
                    Text(name, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        if (own != null) {
            if (own.left) Text(stringResource(Res.string.detail_group_left), style = MaterialTheme.typography.bodySmall)
            AppTextButton(
                onClick = { state.eventSink(if (own.left) ActivityDetailEvent.RejoinGroup else ActivityDetailEvent.LeaveGroup) },
                enabled = !own.busy,
            ) {
                Text(stringResource(if (own.left) Res.string.detail_group_rejoin else Res.string.detail_group_leave))
            }
            own.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
        }
    }
}
