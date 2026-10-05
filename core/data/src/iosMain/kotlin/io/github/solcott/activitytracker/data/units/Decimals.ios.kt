package io.github.solcott.activitytracker.data.units

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.decimalSeparator

actual fun decimalSeparator(): Char = NSLocale.currentLocale.decimalSeparator.firstOrNull() ?: '.'
