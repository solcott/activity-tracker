package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.export.ExportArchive
import io.github.solcott.activitytracker.data.export.downloadArchive
import kotlinx.coroutines.launch

// The browser downloads it; the zip streams through the origin private file system, never whole in memory.
@Composable
actual fun rememberArchiveHandOff(onDone: (ArchiveHandOffResult) -> Unit): (ExportArchive) -> Unit {
    val done by rememberUpdatedState(onDone)
    val scope = rememberCoroutineScope()
    return remember(scope) {
        { archive ->
            scope.launch {
                done(runSuspendCatching { downloadArchive(archive) }.fold({ ArchiveHandOffResult.Saved }, { ArchiveHandOffResult.Failed }))
            }
        }
    }
}
