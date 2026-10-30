package io.github.solcott.activitytracker.app.ui.preview

import androidx.compose.runtime.Composable

/** Gives Compose Multiplatform resources what they need to load under a preview renderer: Android's layoutlib has no app context. */
@Composable expect fun PreviewResources()
