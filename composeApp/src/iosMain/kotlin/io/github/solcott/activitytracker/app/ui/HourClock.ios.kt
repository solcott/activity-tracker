package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

// "j" is the locale's preferred hour, which follows the iPhone's 24-Hour Time setting: the pattern has an "a" on a 12-hour clock.
@Composable
@ReadOnlyComposable
actual fun uses24HourClock(): Boolean = NSDateFormatter.dateFormatFromTemplate("j", 0u, NSLocale.currentLocale)?.contains('a') != true
