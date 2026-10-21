package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Blocks
import io.github.solcott.activitytracker.appview.db.Mutes
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.stringParam
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select

/**
 * True while either of [a] and [b] blocks the other, with a `graph.block` or an `app.bsky.graph.block`: a block works both ways and its
 * source doesn't matter. Correlated to the outer query when either is a column.
 */
internal fun blocked(a: Expression<String>, b: Expression<String>): Op<Boolean> =
    exists(
        Blocks.select(Blocks.uri).where {
            ((Blocks.authorDid eq a) and (Blocks.subjectDid eq b)) or ((Blocks.authorDid eq b) and (Blocks.subjectDid eq a))
        }
    )

/** [blocked] for a known [viewer]; a null viewer (signed out) is in no block. */
internal fun notBlocked(viewer: Did?, other: Expression<String>): Op<Boolean> =
    if (viewer == null) Op.TRUE else not(blocked(stringParam(viewer.value), other))

/** True while [viewer] mutes [subject]. Mutes are one-way: only the muter's own reads apply it. */
internal fun notMuted(viewer: Did?, subject: Expression<String>): Op<Boolean> =
    if (viewer == null) Op.TRUE
    else not(exists(Mutes.select(Mutes.uri).where { (Mutes.authorDid eq viewer.value) and (Mutes.subjectDid eq subject) }))

/** Whether [a] and [b] are in a block, made by either. */
internal fun JdbcTransaction.isBlocked(a: String, b: String): Boolean =
    !Blocks.select(Blocks.uri)
        .where { ((Blocks.authorDid eq a) and (Blocks.subjectDid eq b)) or ((Blocks.authorDid eq b) and (Blocks.subjectDid eq a)) }
        .limit(1)
        .empty()

/**
 * [viewer]'s own block of [subject]: their `graph.block` if they have one, else their `app.bsky.graph.block`. Never [subject]'s block of
 * [viewer], so a block stays silent.
 */
internal fun JdbcTransaction.ownBlock(viewer: String, subject: String): String? =
    Blocks.select(Blocks.uri)
        .where { (Blocks.authorDid eq viewer) and (Blocks.subjectDid eq subject) }
        .map { it[Blocks.uri] }
        .minByOrNull { if (it.contains("/${Nsid.GRAPH_BLOCK}/")) 0 else 1 }

/** [viewer]'s mute of [subject]. */
internal fun JdbcTransaction.ownMute(viewer: String, subject: String): String? =
    Mutes.select(Mutes.uri).where { (Mutes.authorDid eq viewer) and (Mutes.subjectDid eq subject) }.firstOrNull()?.get(Mutes.uri)
