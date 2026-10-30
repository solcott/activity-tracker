package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Composable

/** Returns a launcher that requests location permission and reports whether it was granted. */
@Composable expect fun rememberLocationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit
