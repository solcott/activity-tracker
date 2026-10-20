package io.github.solcott.activitytracker.data.units

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.decimalSeparator
import platform.Foundation.groupingSeparator

actual fun decimalSeparator(): Char = NSLocale.currentLocale.decimalSeparator.firstOrNull() ?: '.'

internal actual fun platformGroupingSeparator(): Char? = NSLocale.currentLocale.groupingSeparator.firstOrNull()
