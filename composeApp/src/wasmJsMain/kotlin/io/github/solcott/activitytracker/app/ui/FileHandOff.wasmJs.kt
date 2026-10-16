package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.solcott.activitytracker.data.activity.ExportedFile
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.download
import kotlinx.coroutines.launch

// The browser downloads it under its name.
@Composable
actual fun rememberFileHandOff(): (ExportedFile) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(scope) { { file -> scope.launch { FileKit.download(file.bytes, file.name) } } }
}
