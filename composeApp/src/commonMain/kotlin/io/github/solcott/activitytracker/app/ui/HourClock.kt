package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/** Whether the viewer reads times on a 24-hour clock: their device's setting where it has one, else their locale's convention. */
@Composable @ReadOnlyComposable expect fun uses24HourClock(): Boolean
