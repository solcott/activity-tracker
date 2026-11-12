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
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.ui.Avatar
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.string

/**
 * The athletes this activity was done together with (feed/group-activities), each opening their activity, and on the owner's page Leave
 * group or Rejoin group, Add people (feed/activity-invites) and, on their copy of another's activity, Leave group. Nothing shows when there
 * are no partners and nothing to offer.
 */
@Composable
internal fun TogetherWith(state: ActivityDetailPage, modifier: Modifier = Modifier) {
    val own = state.ownGroup
    if (!state.showsGroup) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (state.partners.isNotEmpty()) {
            Text(ActivityPageTexts.togetherWith.string(), style = MaterialTheme.typography.titleMedium)
            state.partners.forEach { partner ->
                val name = partner.author.shownName
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
            if (own.left) Text(ActivityPageTexts.groupLeft.string(), style = MaterialTheme.typography.bodySmall)
            AppTextButton(
                onClick = { state.eventSink(ActivityPageTexts.groupEvent(own)) },
                enabled = !own.busy,
            ) {
                Text(ActivityPageTexts.groupButton(own).string())
            }
            own.error?.let { ErrorText(it) }
        }
        if (state.isOwnCopy) {
            AppTextButton(onClick = { state.eventSink(ActivityDetailEvent.LeaveCopyGroup) }, enabled = !state.leavingCopyGroup) {
                Text(ActivityPageTexts.leaveCopyGroup.string())
            }
            state.copyGroupError?.let { ErrorText(it) }
        }
        if (state.canAddPeople) {
            AppTextButton(onClick = { state.eventSink(ActivityDetailEvent.AddPeople) }) { Text(ActivityPageTexts.addPeople.string()) }
        }
    }
}
