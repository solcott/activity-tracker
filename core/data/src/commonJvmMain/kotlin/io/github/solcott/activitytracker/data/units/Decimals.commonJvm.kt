package io.github.solcott.activitytracker.data.units

import java.text.DecimalFormatSymbols

actual fun decimalSeparator(): Char = DecimalFormatSymbols.getInstance().decimalSeparator

internal actual fun platformGroupingSeparator(): Char? = DecimalFormatSymbols.getInstance().groupingSeparator
