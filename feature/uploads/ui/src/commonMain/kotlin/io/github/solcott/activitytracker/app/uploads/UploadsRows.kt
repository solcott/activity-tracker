package io.github.solcott.activitytracker.app.uploads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.material3.HorizontalDivider
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
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.uploads_cancel
import io.github.solcott.activitytracker.composeApp.resources.uploads_cancel_named
import io.github.solcott.activitytracker.composeApp.resources.uploads_send_again
import io.github.solcott.activitytracker.composeApp.resources.uploads_send_again_named
import io.github.solcott.activitytracker.composeApp.resources.uploads_state_refused
import io.github.solcott.activitytracker.data.activity.RejectedChange
import io.github.solcott.activitytracker.data.activity.UploadItem
import io.github.solcott.activitytracker.data.activity.UploadState
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ItemRow(item: UploadItem, state: UploadsState) {
    val name = itemName(item)
    val rkey = item.uri?.rkey
    val busy = rkey != null && rkey in state.busy
    val uri = item.uri
    val opens = item.state == UploadState.Uploaded && uri != null
    Column {
        Row(
            Modifier.fillMaxWidth()
                .then(
                    if (opens && uri != null) {
                        Modifier.clickable(role = Role.Button) { state.eventSink(UploadsEvent.OpenActivity(uri.value)) }
                    } else {
                        Modifier
                    }
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Detail(stateText(item), error = item.state == UploadState.Refused)
            }
            if (rkey != null && item.state == UploadState.Refused) {
                SendAgainButton(name, busy) { state.eventSink(UploadsEvent.SendAgain(rkey)) }
            }
            if (rkey != null && item.cancellable) CancelButton(name, busy) { state.eventSink(UploadsEvent.Cancel(rkey)) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
internal fun RefusedChangeRow(change: RejectedChange, state: UploadsState) {
    val name = changeName(change)
    val busy = change.rkey in state.busy
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Detail(stringResource(Res.string.uploads_state_refused), error = true)
            }
            SendAgainButton(name, busy) { state.eventSink(UploadsEvent.SendAgain(change.rkey)) }
            CancelButton(name, busy) { state.eventSink(UploadsEvent.Cancel(change.rkey)) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SendAgainButton(name: String, busy: Boolean, onClick: () -> Unit) {
    val description = stringResource(Res.string.uploads_send_again_named, name)
    AppTextButton(onClick = onClick, Modifier.semantics { contentDescription = description }, enabled = !busy) {
        Text(stringResource(Res.string.uploads_send_again))
    }
}

@Composable
private fun CancelButton(name: String, busy: Boolean, onClick: () -> Unit) {
    val description = stringResource(Res.string.uploads_cancel_named, name)
    AppTextButton(onClick = onClick, Modifier.semantics { contentDescription = description }, enabled = !busy) {
        Text(stringResource(Res.string.uploads_cancel))
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
internal fun Note(text: String) {
    Text(
        text,
        Modifier.padding(vertical = 2.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun Detail(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
