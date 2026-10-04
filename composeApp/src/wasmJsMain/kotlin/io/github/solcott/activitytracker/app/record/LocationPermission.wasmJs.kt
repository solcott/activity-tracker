package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Composable

/** Web has no GPS recording (see UnsupportedLocationTracker), so the record screen is never reached. */
@Composable actual fun rememberLocationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit = { onResult(false) }
