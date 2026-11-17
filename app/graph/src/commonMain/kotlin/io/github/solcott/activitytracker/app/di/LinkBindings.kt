package io.github.solcott.activitytracker.app.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import io.github.solcott.activitytracker.app.NetworkConfigs
import io.github.solcott.activitytracker.app.links.AppLinks

/** Which URLs are the app's links (ui/deep-links): https on this build's link host, if it has one, and the app's own scheme. */
@ContributesTo(AppScope::class)
@BindingContainer
object LinkBindings {
    @Provides fun appLinks(): AppLinks = AppLinks(NetworkConfigs.LINK_HOST)
}
