package io.github.solcott.activitytracker.appview.segments

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityDetails
import io.github.solcott.activitytracker.appview.db.ActivityStreams
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.app.ActivityDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.DetailsView
import io.github.solcott.activitytracker.lexicon.app.TierChannels
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.effectiveRecord
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select

/**
 * One activity's tiers as indexed, read at most once each: its record, every held details record (all tiers, widest first) and its streams'
 * channels per tier. What the AppView derives from an activity's tiers (efforts', visits' and pairs' tiers, a pair's exact start) reads
 * them here, so a re-tier reads them once. Each part is read on first use, inside the transaction that made it ([activityTiers]).
 *
 * It isn't filtered for a viewer, unlike `detailsOf`, so nothing built from it is served as is. It decodes strictly: an undecodable details
 * record throws (the job fails and retries) rather than being skipped, since skipping a narrower tier's details could widen a derived tier.
 */
internal class ActivityTiers(private val tx: JdbcTransaction, val row: ResultRow) {
    /** The activity's own tier. */
    val tier: String = row[Activities.visibility]

    private val record by lazy { AtJson.decodeFromString(ActivityRecord.serializer(), row[Activities.record]) }

    private val details by lazy {
        with(tx) {
            val rows =
                ActivityDetails.select(ActivityDetails.visibility, ActivityDetails.record)
                    .where { ActivityDetails.activityUri eq row[Activities.uri] }
                    .map {
                        DetailsView(
                            it[ActivityDetails.visibility],
                            AtJson.decodeFromString(ActivityDetailsRecord.serializer(), it[ActivityDetails.record]),
                        )
                    }
            Visibility.widestFirst(rows) { it.visibility }
        }
    }

    private val categories by lazy {
        val parts =
            with(tx) {
                ActivityStreams.select(ActivityStreams.visibility, ActivityStreams.channels)
                    .where { ActivityStreams.activityUri eq row[Activities.uri] }
                    .map {
                        TierChannels(
                            it[ActivityStreams.visibility],
                            AtJson.decodeFromString(ListSerializer(String.serializer()), it[ActivityStreams.channels]),
                        )
                    }
            }
        DetailCategory.currentTiersOfChannels(tier, record, details, parts)
    }

    /** The record with every details record laid over it, widest first: its exact start and elapsed time. Never served. */
    val exactRecord: ActivityRecord by lazy { effectiveRecord(record, details.map { it.record }) }

    /** The narrowest of the activity's tier and [categories]' current tiers (`DetailCategory.currentTiersOfChannels`). */
    fun narrowestOf(vararg categories: String): String = categories.map { this.categories[it] ?: tier }.fold(tier, Visibility::narrower)
}

/** [row]'s [ActivityTiers], read on use within this transaction. */
internal fun JdbcTransaction.activityTiers(row: ResultRow): ActivityTiers = ActivityTiers(this, row)
