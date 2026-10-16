package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.solcott.activitytracker.data.activity.ExportedFile
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.dialogs.compose.rememberShareFileLauncher
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.launch

// The share sheet needs a file: write it under its name to the cache dir, which FileKit's merged FileProvider serves on Android.
@Composable
actual fun rememberFileHandOff(): (ExportedFile) -> Unit {
    val share = rememberShareFileLauncher()
    val scope = rememberCoroutineScope()
    return remember(share, scope) {
        { file ->
            scope.launch {
                val target = FileKit.cacheDir / file.name
                target.write(file.bytes)
                share.launch(target)
            }
        }
    }
}
