package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.ActivityDetails
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ActivityDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.DetailsView
import io.github.solcott.activitytracker.lexicon.app.Visibility
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select

// Serving `feed.activityDetails` records in `activityView.details`, which `Views.hydrate` asks for a page at a time.

/**
 * The details records of [activities] (each one [viewer] may see) from the tiers [viewer] is admitted to, widest first, in one query. A
 * details record in a tier the viewer isn't admitted to is never read.
 */
internal fun JdbcTransaction.detailsOf(activities: List<String>, viewer: Did?): Map<String, List<DetailsView>> =
    ActivityDetails.select(ActivityDetails.activityUri, ActivityDetails.visibility, ActivityDetails.record)
        .where {
            (ActivityDetails.activityUri inList activities) and
                visibleTo(viewer, ActivityDetails.visibility, ActivityDetails.authorDid, ActivityDetails.activityUri, null)
        }
        .mapNotNull { row ->
            val record = runCatching {
                AtJson.decodeFromString(ActivityDetailsRecord.serializer(), row[ActivityDetails.record])
            }
                .getOrNull()
            record?.let { row[ActivityDetails.activityUri] to DetailsView(row[ActivityDetails.visibility], it) }
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, views) -> views.sortedBy { Visibility.rank(it.visibility) } }
