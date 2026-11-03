package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic

/** The athlete's display name, or their handle (without `@`) when they have none. */
val ProfileViewBasic.shownName: String
    get() = displayName ?: handle.value

/** The athlete's display name, or their handle (without `@`) when they have none. */
val ProfileView.shownName: String
    get() = displayName ?: handle.value
