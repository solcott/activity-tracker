package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.data.activity.ExportedFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.launch

// A save dialog with the name filled in; the bytes wait here until the athlete picks where they go, and are dropped on cancel.
@Composable
actual fun rememberFileHandOff(): (ExportedFile) -> Unit {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<ExportedFile?>(null) }
    val saver =
        rememberFileSaverLauncher(FileKitDialogSettings.createDefault()) { target ->
            val file = pending
            pending = null
            if (target != null && file != null) scope.launch { target.write(file.bytes) }
        }
    return remember(saver) {
        { file ->
            pending = file
            saver.launch(suggestedName = file.name.substringBeforeLast('.'), defaultExtension = file.name.substringAfterLast('.'))
        }
    }
}
