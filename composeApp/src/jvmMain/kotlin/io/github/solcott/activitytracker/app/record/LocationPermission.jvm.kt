package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Composable

/** JVM target exists only for presenter tests. */
@Composable actual fun rememberLocationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit = { onResult(true) }
