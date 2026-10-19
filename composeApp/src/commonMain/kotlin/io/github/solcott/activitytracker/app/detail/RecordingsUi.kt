package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.sportLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.detail_member_of
import io.github.solcott.activitytracker.composeApp.resources.detail_members
import io.github.solcott.activitytracker.composeApp.resources.detail_open_combined
import io.github.solcott.activitytracker.composeApp.resources.detail_open_recorded
import io.github.solcott.activitytracker.composeApp.resources.detail_recording_of
import io.github.solcott.activitytracker.composeApp.resources.detail_recording_unnamed
import io.github.solcott.activitytracker.composeApp.resources.detail_recording_unnamed_earlier
import io.github.solcott.activitytracker.composeApp.resources.detail_recording_unnamed_later
import io.github.solcott.activitytracker.composeApp.resources.detail_recordings
import io.github.solcott.activitytracker.composeApp.resources.detail_remove_member
import io.github.solcott.activitytracker.composeApp.resources.remove_member_message
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.MemberView
import io.github.solcott.activitytracker.lexicon.app.RecordingView
import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The other devices that recorded this activity (a backup dive computer, say), each opening its own recording; or, on a recording, the
 * activity it records. Nothing for an activity recorded once.
 */
@Composable
internal fun Recordings(activity: ActivityView, onOpen: (AtUri) -> Unit, modifier: Modifier = Modifier) {
    val recordings = activity.recordings.orEmpty()
    val recordingOf = activity.record.recordingOf
    if (recordings.isEmpty() && recordingOf == null) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (recordingOf != null) {
            Text(stringResource(Res.string.detail_recording_of), style = MaterialTheme.typography.bodyMedium)
            Link(stringResource(Res.string.detail_open_recorded)) { onOpen(recordingOf) }
        } else {
            Text(stringResource(Res.string.detail_recordings), style = MaterialTheme.typography.labelLarge)
            recordings.forEach { recording -> Link(recordingName(recording, activity.record.startedAt)) { onOpen(recording.uri) } }
        }
    }
}

/** The recording's device; or, for a file that named none, when it started next to [activityStart], to tell such recordings apart. */
@Composable
private fun recordingName(recording: RecordingView, activityStart: Instant): String {
    recording.device?.let {
        return it
    }
    val minutes = (recording.startedAt - activityStart).inWholeMinutes.toInt()
    return when {
        minutes > 0 -> pluralStringResource(Res.plurals.detail_recording_unnamed_later, minutes, minutes)
        minutes < 0 -> pluralStringResource(Res.plurals.detail_recording_unnamed_earlier, abs(minutes), abs(minutes))
        else -> stringResource(Res.string.detail_recording_unnamed)
    }
}

/**
 * A combined activity's members, oldest first, each opening its own page, which the owner can take out ([onRemove], confirmed); or, on a
 * member, the combined activity it's part of. Nothing for an activity that combines nothing and is no member.
 */
@Composable
internal fun Members(activity: ActivityView, onOpen: (AtUri) -> Unit, onRemove: ((AtUri) -> Unit)?, modifier: Modifier = Modifier) {
    val members = activity.members.orEmpty()
    val combinedInto = activity.combinedInto
    if (members.isEmpty() && combinedInto == null) return
    var removing by remember { mutableStateOf<MemberView?>(null) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (combinedInto != null) {
            Text(stringResource(Res.string.detail_member_of), style = MaterialTheme.typography.bodyMedium)
            Link(stringResource(Res.string.detail_open_combined)) { onOpen(combinedInto) }
        } else {
            Text(stringResource(Res.string.detail_members), style = MaterialTheme.typography.labelLarge)
            members.forEach { member ->
                val name = memberName(member)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Link(name) { onOpen(member.uri) } }
                    if (onRemove != null) {
                        AppIconButton(AppIcon.Close, stringResource(Res.string.detail_remove_member, name), onClick = { removing = member })
                    }
                }
            }
        }
    }
    removing?.let { member ->
        val name = memberName(member)
        AppAlertDialog(
            title = stringResource(Res.string.detail_remove_member, name),
            message = stringResource(Res.string.remove_member_message),
            actions =
                persistentListOf(
                    AppDialogAction(stringResource(Res.string.detail_remove_member, name), destructive = true) {
                        onRemove?.invoke(member.uri)
                    }
                ),
            cancelLabel = stringResource(Res.string.action_cancel),
            onDismiss = { removing = null },
        )
    }
}

/**
 * A member's device; or, for a file that named none, its sport. Not its start: a member whose start is hidden is served at its local day's
 * midnight, so it would read as hours away from the combined activity.
 */
@Composable
internal fun memberName(member: MemberView): String =
    member.device ?: member.sport?.let { sportLabel(it) } ?: stringResource(Res.string.detail_recording_unnamed)

@Composable
private fun Link(text: String, onClick: () -> Unit) {
    Text(
        text,
        Modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
