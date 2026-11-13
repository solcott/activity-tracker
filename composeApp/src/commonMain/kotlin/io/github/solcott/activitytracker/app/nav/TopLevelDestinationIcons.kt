package io.github.solcott.activitytracker.app.nav

import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.nav_feed
import io.github.solcott.activitytracker.composeApp.resources.nav_search
import io.github.solcott.activitytracker.composeApp.resources.nav_you
import org.jetbrains.compose.resources.StringResource

/** A tab's name in the Compose shell's bar, rail or sidebar. */
val TopLevelDestination.label: StringResource
    get() =
        when (this) {
            TopLevelDestination.Feed -> Res.string.nav_feed
            TopLevelDestination.Search -> Res.string.nav_search
            TopLevelDestination.You -> Res.string.nav_you
        }

/** A tab's icon in the Compose shell. */
val TopLevelDestination.icon: AppIcon
    get() =
        when (this) {
            TopLevelDestination.Feed -> AppIcon.Feed
            TopLevelDestination.Search -> AppIcon.Search
            TopLevelDestination.You -> AppIcon.You
        }
