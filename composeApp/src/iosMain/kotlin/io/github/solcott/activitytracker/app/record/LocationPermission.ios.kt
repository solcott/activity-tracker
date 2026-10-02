package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Composable

/** CLLocationManager prompts when updates start (see IosLocationTracker). */
@Composable actual fun rememberLocationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit = { onResult(true) }
