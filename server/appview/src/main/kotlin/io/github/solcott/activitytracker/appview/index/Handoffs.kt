@file:Suppress("TooManyFunctions") // Staging, linking, releasing and resolving handoffs of segments and dive sites.

package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.api.LabelValues
import io.github.solcott.activitytracker.appview.api.labelled
import io.github.solcott.activitytracker.appview.db.DiveSites
import io.github.solcott.activitytracker.appview.db.HandoffKind
import io.github.solcott.activitytracker.appview.db.SegmentNameProposals
import io.github.solcott.activitytracker.appview.db.SegmentNameVotes
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.SubjectHandoffs
import io.github.solcott.activitytracker.appview.segments.ProposalState
import io.github.solcott.activitytracker.appview.segments.recomputeCurrentName
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.location.SegmentGeometry
import java.security.MessageDigest
import java.util.HexFormat
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

// Handoffs (segments/segment-handoffs, dive-sites/site-handoffs): an active segment or dive site the segment importer didn't create
// outlives its record, so the importer can republish it and its riders' efforts, divers' visits and adopted names come back.

private const val HANDOFF_ID_PREFIX = "handoff-"
private const val HANDOFF_ID_HEX = 40

/** How many handoffs [resolveHandoff] follows: each removal of a successor that is itself handed off adds one. */
private const val MAX_HOPS = 8

/** The id a removed subject at [oldUri] is republished under: the record key the importer writes, derived from the URI alone. */
internal fun handoffId(oldUri: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(oldUri.toByteArray())
    return HANDOFF_ID_PREFIX + HexFormat.of().formatHex(digest).take(HANDOFF_ID_HEX)
}

/**
 * Stages a handoff for each of [uris] (segments about to be removed) that is `active`, not the [importer]'s, and carries no `!takedown` on
 * its record or its creator's account, and keeps its adopted names under its URI while dropping its open proposals and every vote. Returns
 * the URIs it staged, whose names [deleteSegments] must leave alone.
 */
internal fun JdbcTransaction.stageSegmentHandoffs(uris: List<String>, importer: Did?, now: Long): Set<String> {
    if (uris.isEmpty()) return emptySet()
    val rows =
        Segments.selectAll()
            .where {
                (Segments.uri inList uris) and
                    (Segments.status eq SegmentStatus.ACTIVE) and
                    not(labelled(Segments.uri, LabelValues.TAKEDOWN, Segments.cid)) and
                    not(labelled(Segments.authorDid, LabelValues.TAKEDOWN))
            }
            .filter { importer == null || it[Segments.authorDid] != importer.value }
    for (row in rows) {
        val uri = row[Segments.uri]
        SubjectHandoffs.upsert {
            it[id] = handoffId(uri)
            it[kind] = HandoffKind.SEGMENT
            it[oldUri] = uri
            it[sport] = row[Segments.sport]
            it[segmentKind] = row[Segments.kind]
            it[line] = row[Segments.line]
            it[latE7] = null
            it[lngE7] = null
            it[radiusM] = null
            it[originalName] = row[Segments.name]
            it[successorUri] = null
            it[stagedAt] = now
        }
    }
    val staged = rows.map { it[Segments.uri] }.toSet()
    keepAdoptedNames(staged)
    return staged
}

/** [stageSegmentHandoffs] for dive sites about to be removed: active, not the [importer]'s, not taken down. */
internal fun JdbcTransaction.stageSiteHandoffs(uris: List<String>, importer: Did?, now: Long): Set<String> {
    if (uris.isEmpty()) return emptySet()
    val rows =
        DiveSites.selectAll()
            .where {
                (DiveSites.uri inList uris) and
                    (DiveSites.status eq SegmentStatus.ACTIVE) and
                    not(labelled(DiveSites.uri, LabelValues.TAKEDOWN, DiveSites.cid)) and
                    not(labelled(DiveSites.authorDid, LabelValues.TAKEDOWN))
            }
            .filter { importer == null || it[DiveSites.authorDid] != importer.value }
    for (row in rows) {
        val uri = row[DiveSites.uri]
        SubjectHandoffs.upsert {
            it[id] = handoffId(uri)
            it[kind] = HandoffKind.SITE
            it[oldUri] = uri
            it[sport] = null
            it[segmentKind] = null
            it[line] = null
            it[latE7] = row[DiveSites.latE7]
            it[lngE7] = row[DiveSites.lngE7]
            it[radiusM] = row[DiveSites.radiusM]
            it[originalName] = row[DiveSites.name]
            it[successorUri] = null
            it[stagedAt] = now
        }
    }
    val staged = rows.map { it[DiveSites.uri] }.toSet()
    keepAdoptedNames(staged)
    return staged
}

/** Of the proposals on [subjects], keeps the adopted ones (the name history, with their final totals) and drops the rest and every vote. */
private fun JdbcTransaction.keepAdoptedNames(subjects: Set<String>) {
    if (subjects.isEmpty()) return
    val proposals =
        SegmentNameProposals.select(SegmentNameProposals.uri, SegmentNameProposals.state)
            .where { SegmentNameProposals.segmentUri inList subjects }
            .toList()
    val all = proposals.map { it[SegmentNameProposals.uri] }
    if (all.isNotEmpty()) SegmentNameVotes.deleteWhere { proposalUri inList all }
    val open = proposals.filter { it[SegmentNameProposals.state] != ProposalState.ADOPTED }.map { it[SegmentNameProposals.uri] }
    if (open.isNotEmpty()) SegmentNameProposals.deleteWhere { SegmentNameProposals.uri inList open }
}

/**
 * Before [removed] subjects' names are deleted: those that were successors and got no handoff of their own ([staged]) give their handoffs
 * back, pending again with the names they took.
 */
internal fun JdbcTransaction.releaseHandoffsOf(removed: List<String>, staged: Set<String>) {
    val released = removed.filter { it !in staged }
    if (released.isEmpty()) return
    SubjectHandoffs.selectAll()
        .where { SubjectHandoffs.successorUri inList released }
        .forEach { row ->
            val oldUri = row[SubjectHandoffs.oldUri]
            val successor = requireNotNull(row[SubjectHandoffs.successorUri])
            moveNames(from = successor, to = oldUri)
            SubjectHandoffs.update({ SubjectHandoffs.id eq row[SubjectHandoffs.id] }) { it[successorUri] = null }
        }
}

/** Of [uris], those that are the old URI of a handoff: their adopted names are the handoff's, so a removal leaves them. */
internal fun JdbcTransaction.handedOff(uris: List<String>): Set<String> =
    if (uris.isEmpty()) emptySet()
    else
        SubjectHandoffs.select(SubjectHandoffs.oldUri)
            .where { SubjectHandoffs.oldUri inList uris }
            .map { it[SubjectHandoffs.oldUri] }
            .toSet()

/**
 * After removed subjects' duplicates were checked again: each of [promoted] (a former duplicate that is live now, mapped to the removed
 * subject it duplicated) takes over that subject's pending handoff.
 */
internal fun JdbcTransaction.linkPromoted(promoted: Map<String, String>) {
    for ((oldUri, successor) in promoted) linkHandoff(oldUri, successor)
}

/**
 * Makes [successor] the successor of the pending handoff of [oldUri], if there is one, and gives it the handoff's adopted names. A later
 * handoff of the successor itself chains on from it ([resolveHandoff]).
 */
internal fun JdbcTransaction.linkHandoff(oldUri: String, successor: String) {
    val updated =
        SubjectHandoffs.update({ (SubjectHandoffs.oldUri eq oldUri) and SubjectHandoffs.successorUri.isNull() }) {
            it[successorUri] = successor
        }
    if (updated == 0) return
    moveNames(from = oldUri, to = successor)
}

private fun JdbcTransaction.moveNames(from: String, to: String) {
    SegmentNameProposals.update({ SegmentNameProposals.segmentUri eq from }) { it[segmentUri] = to }
    recomputeCurrentName(to)
}

/**
 * A segment or site record indexed at [uri]: if it's the old URI of a pending handoff, the original is back and takes its own handoff over
 * (its names are still under [uri]); the handoff stays, so a replay of the record's create and delete (a rewound Jetstream cursor) leaves
 * it pending again rather than losing it. If it's the [importer]'s record under a handoff id, it takes over that handoff, or, when it was
 * indexed as a duplicate, [duplicateOf] does.
 */
internal fun JdbcTransaction.handoffOnIndex(uri: AtUri, author: Did, importer: Did?, duplicateOf: String?) {
    linkHandoff(uri.value, uri.value)
    val rkey = uri.rkey?.takeIf { importer != null && author == importer }
    val handoff = rkey?.let { id ->
        SubjectHandoffs.select(SubjectHandoffs.oldUri, SubjectHandoffs.kind).where { SubjectHandoffs.id eq id }.firstOrNull()
    }
    val expected = handoff?.let { if (it[SubjectHandoffs.kind] == HandoffKind.SITE) Nsid.FEED_DIVE_SITE else Nsid.FEED_SEGMENT }
    if (handoff != null && uri.collection == expected) linkHandoff(handoff[SubjectHandoffs.oldUri], duplicateOf ?: uri.value)
}

/** Whether [uri], the importer's record, republishes a handoff: a site indexed under one is active at once (dive-sites/site-handoffs). */
internal fun JdbcTransaction.isHandoffRecord(uri: AtUri, author: Did, importer: Did?): Boolean {
    val rkey = uri.rkey ?: return false
    return importer != null && author == importer && SubjectHandoffs.select(SubjectHandoffs.id).where { SubjectHandoffs.id eq rkey }.any()
}

/**
 * The live subject a handed-off [uri] resolves to, following successors that were themselves handed off, or null when [uri] isn't a
 * handed-off URI or the chain ends at a handoff still pending. [indexed] says whether a URI is indexed now.
 */
internal fun JdbcTransaction.resolveHandoff(uri: String, indexed: (String) -> Boolean): String? {
    var current: String? = uri
    var found: String? = null
    var hops = 0
    while (found == null && current != null && hops++ < MAX_HOPS) {
        val from: String = current
        val next =
            SubjectHandoffs.select(SubjectHandoffs.successorUri)
                .where { SubjectHandoffs.oldUri eq from }
                .firstOrNull()
                ?.get(SubjectHandoffs.successorUri)
        if (next != null && indexed(next)) found = next
        current = next
    }
    return found
}

/**
 * Links each pending handoff whose subject a live one now duplicates (segments by their line, sites by their centre) to it, so only those
 * that need republishing stay pending (`importer.getHandoffs`).
 */
internal fun JdbcTransaction.linkCoveredHandoffs() {
    SubjectHandoffs.selectAll()
        .where { SubjectHandoffs.successorUri.isNull() }
        .toList()
        .forEach { row ->
            val covering =
                when (row[SubjectHandoffs.kind]) {
                    HandoffKind.SEGMENT -> {
                        val line = row[SubjectHandoffs.line]?.let(SegmentGeometry::decodePolyline)
                        val sport = row[SubjectHandoffs.sport]
                        val kind = row[SubjectHandoffs.segmentKind]
                        if (line == null || sport == null || kind == null) null else duplicatesOf(sport, kind, line).firstOrNull()
                    }
                    else -> {
                        val lat = row[SubjectHandoffs.latE7]
                        val lng = row[SubjectHandoffs.lngE7]
                        if (lat == null || lng == null) null else diveSiteDuplicatesOf(pointE7(lat, lng)).firstOrNull()
                    }
                }
            if (covering != null) linkHandoff(row[SubjectHandoffs.oldUri], covering)
        }
}
