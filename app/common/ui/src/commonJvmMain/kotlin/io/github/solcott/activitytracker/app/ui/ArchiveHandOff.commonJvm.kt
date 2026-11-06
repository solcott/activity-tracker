package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.export.ExportArchive
import io.github.solcott.activitytracker.data.export.writeTo
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import kotlinx.coroutines.launch

// A save dialog with the name filled in (the Storage Access Framework's create-document dialog on Android); the zip streams into the file
// the athlete picks.
@Composable
actual fun rememberArchiveHandOff(onDone: (ArchiveHandOffResult) -> Unit): (ExportArchive) -> Unit {
    val done by rememberUpdatedState(onDone)
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<ExportArchive?>(null) }
    val saver =
        rememberFileSaverLauncher(FileKitDialogSettings.createDefault()) { target ->
            val archive = pending
            pending = null
            when {
                archive == null -> Unit
                target == null -> done(ArchiveHandOffResult.Dismissed)
                else -> scope.launch { done(archive.saveTo(target)) }
            }
        }
    return remember(saver) {
        { archive ->
            pending = archive
            saver.launch(suggestedName = archive.name.removeSuffix(".zip"), defaultExtension = "zip")
        }
    }
}

private suspend fun ExportArchive.saveTo(target: PlatformFile): ArchiveHandOffResult = runSuspendCatching {
    writeTo(target)
}
    .fold({ ArchiveHandOffResult.Saved }, { ArchiveHandOffResult.Failed })
