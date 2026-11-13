package io.github.solcott.activitytracker.app.nav

import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon

/** A tab's icon in the Compose shell; its name is the shared [TopLevelDestination.title]. */
val TopLevelDestination.icon: AppIcon
    get() =
        when (this) {
            TopLevelDestination.Feed -> AppIcon.Feed
            TopLevelDestination.Search -> AppIcon.Search
            TopLevelDestination.You -> AppIcon.You
        }
