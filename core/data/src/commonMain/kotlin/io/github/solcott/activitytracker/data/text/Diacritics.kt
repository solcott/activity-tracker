package io.github.solcott.activitytracker.data.text

/** [text] without diacritics, so "Côte" matches "cote". Case is left as is. */
expect fun stripDiacritics(text: String): String

/** [text] as a search compares it: without diacritics, lower case and trimmed, so " Pötato" matches "potato". */
fun searchKey(text: String): String = stripDiacritics(text).lowercase().trim()
