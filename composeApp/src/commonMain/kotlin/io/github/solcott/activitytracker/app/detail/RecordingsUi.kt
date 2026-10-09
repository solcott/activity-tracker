package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.detail_open_recorded
import io.github.solcott.activitytracker.composeApp.resources.detail_recording_of
import io.github.solcott.activitytracker.composeApp.resources.detail_recording_unnamed
import io.github.solcott.activitytracker.composeApp.resources.detail_recordings
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
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
            recordings.forEach { recording ->
                Link(recording.device ?: stringResource(Res.string.detail_recording_unnamed)) { onOpen(recording.uri) }
            }
        }
    }
}

@Composable
private fun Link(text: String, onClick: () -> Unit) {
    Text(
        text,
        Modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
