package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable actual fun AppVerticalScrollbar(state: LazyListState, modifier: Modifier) = Unit

@Composable actual fun AppVerticalScrollbar(state: ScrollState, modifier: Modifier) = Unit
