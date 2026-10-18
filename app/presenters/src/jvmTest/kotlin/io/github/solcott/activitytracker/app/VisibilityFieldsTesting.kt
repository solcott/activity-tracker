package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.app.visibility.VisibilityFields

/** Who [category] (a `DetailCategory` token) is shown to in these fields, or null when it isn't offered. */
fun VisibilityFields.tierOf(category: String): String? = categories.firstOrNull { it.category == category }?.visibility
