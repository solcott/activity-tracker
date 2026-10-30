package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.runtime.Composable
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.rememberHazeState

@Composable internal actual fun rememberBarBackdrop(): HazeState? = rememberHazeState()
