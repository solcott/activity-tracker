package io.github.solcott.activitytracker.data.importsave

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/** A batch import's picked files are kept as FileKit bookmarks here. */
@ContributesTo(AppScope::class)
@BindingContainer
object SourceKeeperBindings {
    @Provides @SingleIn(AppScope::class) fun sourceKeeper(logger: Logger): SourceKeeper = BookmarkSourceKeeper(logger)
}
