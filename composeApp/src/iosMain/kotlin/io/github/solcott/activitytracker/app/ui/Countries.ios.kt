package io.github.solcott.activitytracker.app.ui

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localizedStringForCountryCode

actual fun countryName(code: String): String = NSLocale.currentLocale.localizedStringForCountryCode(code) ?: code
