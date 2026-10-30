package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/** Whether times read on a 24-hour clock, when a preview or test fixes it; `null` follows the device ([platformUses24HourClock]). */
val LocalHourClock = staticCompositionLocalOf<Boolean?> { null }

/** Whether the viewer reads times on a 24-hour clock: [LocalHourClock] when set, else the device's setting. */
@Composable @ReadOnlyComposable fun uses24HourClock(): Boolean = LocalHourClock.current ?: platformUses24HourClock()

/** The device's setting where it has one, else the locale's convention. */
@Composable @ReadOnlyComposable expect fun platformUses24HourClock(): Boolean
