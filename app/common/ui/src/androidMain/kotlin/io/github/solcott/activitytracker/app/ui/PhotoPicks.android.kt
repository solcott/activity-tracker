package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.compose.rememberCameraPickerLauncher

@Composable
internal actual fun rememberTakePhoto(onResult: (PlatformFile?) -> Unit): (() -> Unit)? {
    val camera = rememberCameraPickerLauncher(onResult = onResult)
    return remember(camera) { { camera.launch() } }
}
