package io.github.solcott.activitytracker.appview.db

import org.jetbrains.exposed.v1.core.Column

/**
 * A table whose rows are served by tier, like an activity's: each row's [visibility] is the tier space it's stored in or copied at, its
 * [authorDid] is the athlete whose relationships admit viewers, and [servedRecord] (with [servedCid] when the row is that record itself) is
 * what a takedown hides. Queries judge such rows with `visibleTo` in one call over these columns, so author and record can't be swapped.
 */
interface TierRow {
    val visibility: Column<String>
    val authorDid: Column<String>
    val servedRecord: Column<String>
    val servedCid: Column<String>?
        get() = null
}

/** A [TierRow] that belongs to an activity, whose takedown hides it: its [activityUri] is the [servedRecord]. */
interface ActivityTierRow : TierRow {
    val activityUri: Column<String>
    override val servedRecord: Column<String>
        get() = activityUri
}
