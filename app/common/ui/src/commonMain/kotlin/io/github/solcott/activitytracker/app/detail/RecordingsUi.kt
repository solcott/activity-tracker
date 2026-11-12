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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_edit
import io.github.solcott.activitytracker.composeApp.resources.edit_combination
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.MemberView
import io.github.solcott.activitytracker.lexicon.app.RecordingView
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource

/**
 * The other devices that recorded this activity (a backup dive computer, say), each opening its own recording; or, on a recording, the
 * activity it records. Nothing for an activity recorded once.
 */
@Composable
fun Recordings(activity: ActivityView, onOpen: (AtUri) -> Unit, modifier: Modifier = Modifier) {
    val recordings = activity.recordings.orEmpty()
    val recordingOf = activity.record.recordingOf
    if (recordings.isEmpty() && recordingOf == null) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (recordingOf != null) {
            Text(RecordingTexts.recordingOf.string(), style = MaterialTheme.typography.bodyMedium)
            Link(RecordingTexts.openRecorded.string()) { onOpen(recordingOf) }
        } else {
            Text(RecordingTexts.recordings.string(), style = MaterialTheme.typography.labelLarge)
            recordings.forEach { recording -> Link(recordingName(recording, activity.record.startedAt)) { onOpen(recording.uri) } }
        }
    }
}

@Composable
private fun recordingName(recording: RecordingView, activityStart: Instant): String =
    RecordingTexts.recordingName(recording, activityStart).string()

/**
 * A combined activity's members, oldest first, each opening its own page, with Edit for the owner ([onEdit]: the combination editor); or,
 * on a member, the combined activity it's part of. Nothing for an activity that combines nothing and is no member.
 */
@Composable
fun Members(activity: ActivityView, onOpen: (AtUri) -> Unit, onEdit: (() -> Unit)?, modifier: Modifier = Modifier) {
    val members = activity.members.orEmpty()
    val combinedInto = activity.combinedInto
    if (members.isEmpty() && combinedInto == null) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (combinedInto != null) {
            Text(RecordingTexts.memberOf.string(), style = MaterialTheme.typography.bodyMedium)
            Link(RecordingTexts.openCombined.string()) { onOpen(combinedInto) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(RecordingTexts.members.string(), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                if (onEdit != null) {
                    val description = stringResource(Res.string.edit_combination)
                    AppTextButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = description }) {
                        Text(stringResource(Res.string.action_edit))
                    }
                }
            }
            members.forEach { member -> Link(memberName(member)) { onOpen(member.uri) } }
        }
    }
}

/** A member's device; or, for a file that named none, its sport ([RecordingTexts.memberName]). */
@Composable fun memberName(member: MemberView): String = RecordingTexts.memberName(member).string()

@Composable
private fun Link(text: String, onClick: () -> Unit) {
    Text(
        text,
        Modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
