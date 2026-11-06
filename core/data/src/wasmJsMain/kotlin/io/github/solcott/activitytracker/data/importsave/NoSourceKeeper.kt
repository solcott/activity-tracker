package io.github.solcott.activitytracker.data.importsave

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** A browser can't reopen a picked file in a later visit, so the web keeps none: a save's sources are `none` (feed/batch-import). */
@ContributesBinding(AppScope::class) @Inject class NoSourceKeeper : SourceKeeper by KeepNothing
