package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic

/** The athlete's display name, or their handle (without `@`) when they have none. */
val ProfileViewBasic.shownName: String
    get() = displayName ?: handle.value

/** The athlete's display name, or their handle (without `@`) when they have none. */
val ProfileView.shownName: String
    get() = displayName ?: handle.value

/** What an avatar shows until its picture loads, or when it has none: the first letter of the athlete's [name], capitalized. */
fun avatarInitial(name: String): AppText = AppText.written(name.trim().firstOrNull()?.uppercase().orEmpty())
