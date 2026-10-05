package io.github.solcott.activitytracker.data.units

import java.text.DecimalFormatSymbols

actual fun decimalSeparator(): Char = DecimalFormatSymbols.getInstance().decimalSeparator
