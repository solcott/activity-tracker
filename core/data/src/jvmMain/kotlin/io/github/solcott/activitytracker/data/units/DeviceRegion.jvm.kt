package io.github.solcott.activitytracker.data.units

import java.util.Locale

actual fun deviceRegion(): String? = Locale.getDefault().country.ifEmpty { null }
