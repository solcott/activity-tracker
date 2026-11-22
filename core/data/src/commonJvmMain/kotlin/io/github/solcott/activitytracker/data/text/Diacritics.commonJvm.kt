package io.github.solcott.activitytracker.data.text

import java.text.Normalizer

private val COMBINING_MARKS = Regex("\\p{Mn}+")

actual fun stripDiacritics(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
