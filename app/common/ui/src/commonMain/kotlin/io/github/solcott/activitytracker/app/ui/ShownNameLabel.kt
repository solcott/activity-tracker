package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic

/** The athlete's display name, or their handle with its `@` prefix when they have none. */
@Composable fun shownNameLabel(account: ProfileViewBasic): String = account.displayName ?: handleLabel(account.handle.value)

/** The athlete's display name, or their handle with its `@` prefix when they have none. */
@Composable fun shownNameLabel(account: ProfileView): String = account.displayName ?: handleLabel(account.handle.value)
