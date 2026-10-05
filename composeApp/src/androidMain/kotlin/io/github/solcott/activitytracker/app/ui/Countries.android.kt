package io.github.solcott.activitytracker.app.ui

import java.text.Normalizer
import java.util.Locale

actual fun countryName(code: String): String = Locale.Builder().setRegion(code).build().displayCountry.ifEmpty { code }

private val COMBINING_MARKS = Regex("\\p{Mn}+")

actual fun stripDiacritics(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
