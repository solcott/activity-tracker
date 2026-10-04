package io.github.solcott.activitytracker.data.units

import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale

actual fun deviceRegion(): String? = NSLocale.currentLocale.countryCode
