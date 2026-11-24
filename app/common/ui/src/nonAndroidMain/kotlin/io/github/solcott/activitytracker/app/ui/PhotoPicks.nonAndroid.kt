package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.vinceglb.filekit.PlatformFile

// Desktop and web have no camera to open: photos come from files only.
@Composable internal actual fun rememberTakePhoto(onResult: (PlatformFile?) -> Unit): (() -> Unit)? = null
