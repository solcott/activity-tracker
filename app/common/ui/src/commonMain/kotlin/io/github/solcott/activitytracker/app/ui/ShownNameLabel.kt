package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic

/** The athlete's display name, or their handle with its `@` prefix when they have none ([LabelText.shownName]). */
@Composable fun shownNameLabel(account: ProfileViewBasic): String = LabelText.shownName(account).string()

/** The athlete's display name, or their handle with its `@` prefix when they have none ([LabelText.shownName]). */
@Composable fun shownNameLabel(account: ProfileView): String = LabelText.shownName(account).string()
