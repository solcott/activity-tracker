package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.CardRoute
import io.github.solcott.activitytracker.lexicon.app.ImportCardView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SportCount
import kotlin.time.Instant
import kotlinx.serialization.SerializationException
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select

/** A viewer sees a batch as one import card once they may see at least this many of its backfilled activities (feed/import-cards). */
internal const val MIN_CARD_ACTIVITIES = 5L

/**
 * One batch that collapses into an import card for a viewer: [count] of [author]'s backfilled activities in [batch] they may see, the
 * latest posted at [postedAt], which places the card.
 */
internal class CardBatch(val author: String, val batch: String, val postedAt: Long, val count: Long) {
    /** The card's keyset key: never equal to an activity URI (`at://…`), and after every one, so cards and activities order totally. */
    val key: String = "$CARD_KEY_PREFIX$author:$batch"
}

private const val CARD_KEY_PREFIX = "batch:"

/** Rows posted more than the grace after they ended: their feed position ([Activities.sortAt]) is earlier than their post time. */
internal fun backfilled(): Op<Boolean> = Activities.sortAt less Activities.postedAt

/** The batches whose backfilled [rows] (the timeline's rows for one viewer) number at least [MIN_CARD_ACTIVITIES]. */
internal fun JdbcTransaction.cardBatches(rows: Op<Boolean>): List<CardBatch> {
    val count = Activities.uri.count()
    val postedAt = Activities.postedAt.max()
    return Activities.select(Activities.authorDid, Activities.importBatch, postedAt, count)
        .where { rows and backfilled() and Activities.importBatch.isNotNull() }
        .groupBy(Activities.authorDid, Activities.importBatch)
        .having { count greaterEq MIN_CARD_ACTIVITIES }
        .mapNotNull { row ->
            val batch = row[Activities.importBatch] ?: return@mapNotNull null
            CardBatch(row[Activities.authorDid], batch, row[postedAt] ?: return@mapNotNull null, row[count])
        }
}

/** The rows [batches]' cards stand for, which the timeline leaves out; nothing when there are no cards. */
internal fun collapsedInto(batches: List<CardBatch>): Op<Boolean> =
    batches
        .map { (Activities.authorDid eq it.author) and (Activities.importBatch eq it.batch) }
        .reduceOrNull { a, b -> a or b }
        ?.let { backfilled() and it } ?: Op.FALSE

/** Rows not collapsed into any of [batches]' cards. A row in no batch is tested apart, since `NOT` of its null comparison is null. */
internal fun notCollapsedInto(batches: List<CardBatch>): Op<Boolean> =
    if (batches.isEmpty()) Op.TRUE else Activities.importBatch.isNull() or not(collapsedInto(batches))

/**
 * [batch]'s card as its viewer sees it, from the same backfilled [rows] that [cardBatches] counted: the count, start range, sports and
 * distance from every one, and routes from the latest started that have one in their own record.
 */
internal fun JdbcTransaction.importCardView(batch: CardBatch, rows: Op<Boolean>, author: ProfileViewBasic): ImportCardView {
    val inBatch = rows and backfilled() and (Activities.authorDid eq batch.author) and (Activities.importBatch eq batch.batch)
    val members =
        Activities.select(Activities.startedAt, Activities.sport, Activities.distance)
            .where { inBatch }
            .map { Triple(it[Activities.startedAt], it[Activities.sport], it[Activities.distance]) }
    val routes =
        Activities.select(Activities.record)
            .where { inBatch }
            .orderBy(Activities.startedAt to SortOrder.DESC, Activities.uri to SortOrder.DESC)
            .limit(ROUTE_CANDIDATES)
            .asSequence()
            .mapNotNull { routeOf(it[Activities.record]) }
            .take(ImportCardView.MAX_ROUTES)
            .toList()
    val sports = members.groupingBy { it.second }.eachCount().entries.sortedWith(compareBy({ -it.value }, { it.key }))
    val distances = members.mapNotNull { it.third }
    return ImportCardView(
        author = author,
        batch = batch.batch,
        count = members.size.toLong(),
        earliestStartedAt = Instant.fromEpochMilliseconds(members.minOf { it.first }),
        latestStartedAt = Instant.fromEpochMilliseconds(members.maxOf { it.first }),
        sports = sports.map { SportCount(it.key, it.value.toLong()) },
        distance = distances.takeIf { it.isNotEmpty() }?.sum(),
        routes = routes.ifEmpty { null },
        postedAt = Instant.fromEpochMilliseconds(batch.postedAt),
    )
}

/** Latest started rows read for a card's routes, since not every activity has one. */
private const val ROUTE_CANDIDATES = 20

private fun routeOf(record: String): CardRoute? =
    try {
        AtJson.decodeFromString(ActivityRecord.serializer(), record).let { r -> r.polyline?.let { CardRoute(it, r.polylineGaps) } }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
