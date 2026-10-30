package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.data.activity.ExportedFile

/**
 * Hands a file the app built to the platform: the share sheet on Android and iOS, a save dialog on desktop, a download on the web. The
 * returned function starts that and returns at once; dismissing the sheet or dialog writes nothing.
 */
@Composable expect fun rememberFileHandOff(): (ExportedFile) -> Unit
