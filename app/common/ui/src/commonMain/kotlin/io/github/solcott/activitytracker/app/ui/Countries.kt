package io.github.solcott.activitytracker.app.ui

/** The localized name of the country with ISO 3166-1 alpha-2 [code], or the code itself when the platform doesn't know it. */
expect fun countryName(code: String): String

/** [text] without diacritics, so "Côte" matches "cote". Case is left as is. */
expect fun stripDiacritics(text: String): String
