package io.github.solcott.activitytracker.app.ui

import java.util.Locale

actual fun countryName(code: String): String = Locale.Builder().setRegion(code).build().displayCountry.ifEmpty { code }
