package io.github.solcott.activitytracker.app.record

import androidx.compose.runtime.Composable

/** The browser prompts when geolocation is first used. */
@Composable actual fun rememberLocationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit = { onResult(true) }
