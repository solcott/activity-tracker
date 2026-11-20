package io.github.solcott.activitytracker.appview.admin

import io.github.solcott.activitytracker.appview.api.NameMatch
import io.github.solcott.activitytracker.appview.api.served
import io.github.solcott.activitytracker.appview.db.SegmentCandidateRows
import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.SegmentMatchJobs
import io.github.solcott.activitytracker.appview.db.SegmentNameProposals
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.segments.ProposalState
import io.github.solcott.activitytracker.appview.segments.SegmentElevation
import io.github.solcott.activitytracker.appview.segments.SegmentPin
import io.github.solcott.activitytracker.appview.segments.SegmentPins
import io.github.solcott.activitytracker.appview.segments.listedProposal
import io.github.solcott.activitytracker.appview.segments.servedElevation
import io.github.solcott.activitytracker.appview.segments.tallies
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.concat
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select

/** What a segment search asks for; null fields don't filter. [uri] is an AT-URI or a record key. */
data class SegmentSearch(
    val uri: String? = null,
    val name: String? = null,
    val status: String? = null,
    val sport: String? = null,
    val kind: String? = null,
    val region: String? = null,
)

/** A search result: [name] is the served (current) name, null when the AppView wouldn't serve the segment signed out. */
data class SegmentRow(
    val uri: String,
    val authorDid: String,
    val sport: String,
    val kind: String,
    val status: String,
    val name: String?,
)

/** A page of results and the cursor to the next, null at the end. */
data class SegmentResults(val rows: List<SegmentRow>, val cursor: String?)

/**
 * A segment's elevation as the admin page shows it: what's [served] and the stored terrain values, with the effort-derived values only once
 * [effortAthletes] reaches the threshold `servedElevation` serves them at.
 */
data class ElevationState(
    val served: SegmentElevation?,
    val terrainGainM: Long?,
    val terrainLossM: Long?,
    val terrainMaxGrade: Int?,
    val effortAthletes: Int,
    val effortGainM: Long?,
    val effortLossM: Long?,
)

/** One name proposal: totals live for an open one, final for a closed one, and its [name] only where it'd be listed signed out. */
data class ProposalRow(
    val uri: String,
    val proposerDid: String,
    val state: String,
    val openedAt: Long,
    val closedAt: Long?,
    val up: Int,
    val down: Int,
    val name: String?,
)

/**
 * The segment page's state: identifiers, statuses, counts and times, the names only while served signed out ([originalName] and
 * [currentName] null otherwise), and never an effort, track, voter or athlete.
 */
data class SegmentDetail(
    val uri: String,
    val authorDid: String,
    val sport: String,
    val kind: String,
    val distanceM: Long,
    val firstIndexedAt: Long,
    val indexedAt: Long,
    val originalName: String?,
    val currentName: String?,
    val status: String,
    val duplicateOf: String?,
    val groupUri: String,
    val efforts: Long,
    val athletes: Long,
    val activeAthletes: Int,
    val elevation: ElevationState,
    val queuedJobs: List<String>,
    val pins: List<SegmentPin>,
    val proposals: List<ProposalRow>,
)

/** One import revision staged in a region: counts and the latest staging time, never riders. */
data class ImporterRun(
    val region: String,
    val revision: String,
    val candidates: Long,
    val riddenUnpublished: Long,
    val published: Long,
    val lastStagedAt: Long,
)

/**
 * Every read the Segments admin page makes (admin/segment-tools), in one place so its privacy is reviewable: each `select` names its
 * columns, none reads `segments.record`, an effort row, a track or a vote by voter, and names are read only under the serving rules.
 */
class SegmentAdminQueries(
    private val db: Database,
    private val pins: SegmentPins,
    private val activeAthletes: Int,
    private val importer: Did?,
) {
    /** Up to [PAGE_SIZE] segments matching [search], by URI after [cursor]. */
    suspend fun search(search: SegmentSearch, cursor: String? = null): SegmentResults = db.tx {
        var where: Op<Boolean> = Op.TRUE
        search.uri?.let { where = where and byUri(it) }
        search.status?.let { where = where and (Segments.status eq it) }
        search.sport?.let { where = where and (Segments.sport eq it) }
        search.kind?.let { where = where and (Segments.kind eq it) }
        search.name?.let { q ->
            val match = NameMatch(q.lowercase()).anywhere
            where =
                where and ((Segments.currentName.lowerCase() like match) or (Segments.name.lowerCase() like match)) and servedSignedOut()
        }
        search.region?.let { region -> where = where and (importer?.let { Segments.uri inSubQuery publishedFrom(it, region) } ?: Op.FALSE) }
        cursor?.let { where = where and (Segments.uri greater it) }
        val rows =
            Segments.select(Segments.uri, Segments.authorDid, Segments.sport, Segments.kind, Segments.status, Segments.currentName)
                .where(where)
                .orderBy(Segments.uri to SortOrder.ASC)
                .limit(PAGE_SIZE + 1)
                .toList()
        val page = rows.take(PAGE_SIZE)
        val shown = servedAmong(page.map { it[Segments.uri] })
        SegmentResults(
            page.map {
                val uri = it[Segments.uri]
                SegmentRow(
                    uri,
                    it[Segments.authorDid],
                    it[Segments.sport],
                    it[Segments.kind],
                    it[Segments.status],
                    it[Segments.currentName].takeIf { uri in shown },
                )
            },
            page.lastOrNull()?.takeIf { rows.size > PAGE_SIZE }?.get(Segments.uri),
        )
    }

    /** The segment page's state for [uri], or null when it isn't indexed. */
    suspend fun detail(uri: String): SegmentDetail? {
        val pinned = pins.pinsOf(uri)
        return db.tx {
            val row =
                Segments.select(
                        Segments.uri,
                        Segments.authorDid,
                        Segments.sport,
                        Segments.kind,
                        Segments.distanceM,
                        Segments.firstIndexedAt,
                        Segments.indexedAt,
                        Segments.name,
                        Segments.currentName,
                        Segments.status,
                        Segments.duplicateOf,
                        Segments.groupUri,
                        Segments.terrainGainM,
                        Segments.terrainLossM,
                        Segments.terrainMaxGrade,
                        Segments.effortGainM,
                        Segments.effortLossM,
                        Segments.effortAthletes,
                    )
                    .where { Segments.uri eq uri }
                    .firstOrNull() ?: return@tx null
            val shown = uri in servedAmong(listOf(uri))
            SegmentDetail(
                uri = uri,
                authorDid = row[Segments.authorDid],
                sport = row[Segments.sport],
                kind = row[Segments.kind],
                distanceM = row[Segments.distanceM],
                firstIndexedAt = row[Segments.firstIndexedAt],
                indexedAt = row[Segments.indexedAt],
                originalName = row[Segments.name].takeIf { shown },
                currentName = row[Segments.currentName].takeIf { shown },
                status = row[Segments.status],
                duplicateOf = row[Segments.duplicateOf],
                groupUri = row[Segments.groupUri] ?: uri,
                efforts = SegmentEfforts.select(SegmentEfforts.id).where { SegmentEfforts.segmentUri eq uri }.count(),
                athletes =
                    SegmentEfforts.select(SegmentEfforts.authorDid).where { SegmentEfforts.segmentUri eq uri }.withDistinct().count(),
                activeAthletes = activeAthletes,
                elevation = elevationOf(row),
                queuedJobs =
                    SegmentMatchJobs.select(SegmentMatchJobs.kind)
                        .where { SegmentMatchJobs.subjectUri eq uri }
                        .map { it[SegmentMatchJobs.kind] }
                        .sorted(),
                pins = pinned,
                proposals = proposalsOf(uri),
            )
        }
    }

    /** Each region's staged revisions with their counts, or null when no segment importer is configured. */
    suspend fun importerRuns(): List<ImporterRun>? {
        val did = importer ?: return null
        return db.tx {
            val published = Segments.select(Segments.uri).where { Segments.uri eq publishedUri(did) }
            val count = SegmentCandidateRows.id.count()
            fun counts(where: Op<Boolean>): Map<Pair<String, String>, Long> =
                SegmentCandidateRows.select(SegmentCandidateRows.region, SegmentCandidateRows.revision, count)
                    .where(where)
                    .groupBy(SegmentCandidateRows.region, SegmentCandidateRows.revision)
                    .associate { (it[SegmentCandidateRows.region] to it[SegmentCandidateRows.revision]) to it[count] }
            val ridden = counts((SegmentCandidateRows.ridden eq true) and notExists(published))
            val done = counts(exists(published))
            val latest = SegmentCandidateRows.stagedAt.max()
            SegmentCandidateRows.select(SegmentCandidateRows.region, SegmentCandidateRows.revision, count, latest)
                .groupBy(SegmentCandidateRows.region, SegmentCandidateRows.revision)
                .orderBy(SegmentCandidateRows.region to SortOrder.ASC, SegmentCandidateRows.revision to SortOrder.ASC)
                .map {
                    val key = it[SegmentCandidateRows.region] to it[SegmentCandidateRows.revision]
                    ImporterRun(key.first, key.second, it[count], ridden[key] ?: 0, done[key] ?: 0, it[latest] ?: 0)
                }
        }
    }

    /** [row]'s elevation as served, with the effort-derived values only once enough athletes give them. */
    private fun elevationOf(row: ResultRow): ElevationState {
        val enough = row[Segments.effortAthletes] >= activeAthletes
        return ElevationState(
            served = servedElevation(row, activeAthletes),
            terrainGainM = row[Segments.terrainGainM],
            terrainLossM = row[Segments.terrainLossM],
            terrainMaxGrade = row[Segments.terrainMaxGrade],
            effortAthletes = row[Segments.effortAthletes],
            effortGainM = row[Segments.effortGainM].takeIf { enough },
            effortLossM = row[Segments.effortLossM].takeIf { enough },
        )
    }

    private fun JdbcTransaction.proposalsOf(segment: String): List<ProposalRow> {
        val rows =
            SegmentNameProposals.select(
                    SegmentNameProposals.uri,
                    SegmentNameProposals.proposerDid,
                    SegmentNameProposals.state,
                    SegmentNameProposals.openedAt,
                    SegmentNameProposals.closedAt,
                    SegmentNameProposals.finalUp,
                    SegmentNameProposals.finalDown,
                )
                .where { SegmentNameProposals.segmentUri eq segment }
                .orderBy(SegmentNameProposals.openedAt to SortOrder.DESC, SegmentNameProposals.uri to SortOrder.ASC)
                .toList()
        val names =
            SegmentNameProposals.select(SegmentNameProposals.uri, SegmentNameProposals.name)
                .where { (SegmentNameProposals.segmentUri eq segment) and listedProposal(SIGNED_OUT) }
                .associate { it[SegmentNameProposals.uri] to it[SegmentNameProposals.name] }
        val open = rows.filter { it[SegmentNameProposals.state] == ProposalState.OPEN }.map { it[SegmentNameProposals.uri] }
        val live = tallies(segment, open)
        return rows.map {
            val uri = it[SegmentNameProposals.uri]
            val isOpen = it[SegmentNameProposals.state] == ProposalState.OPEN
            ProposalRow(
                uri = uri,
                proposerDid = it[SegmentNameProposals.proposerDid],
                state = it[SegmentNameProposals.state],
                openedAt = it[SegmentNameProposals.openedAt],
                closedAt = it[SegmentNameProposals.closedAt],
                up = if (isOpen) live[uri]?.up ?: 0 else it[SegmentNameProposals.finalUp] ?: 0,
                down = if (isOpen) live[uri]?.down ?: 0 else it[SegmentNameProposals.finalDown] ?: 0,
                name = names[uri],
            )
        }
    }

    /** Which of [uris] the AppView would serve a signed-out viewer. */
    private fun JdbcTransaction.servedAmong(uris: List<String>): Set<String> =
        if (uris.isEmpty()) emptySet()
        else Segments.select(Segments.uri).where { (Segments.uri inList uris) and servedSignedOut() }.map { it[Segments.uri] }.toSet()

    private fun servedSignedOut(): Op<Boolean> = served(SIGNED_OUT, Segments.authorDid, Segments.uri, Segments.cid)

    /** An exact AT-URI, or a bare record key matching any segment's. */
    private fun byUri(q: String): Op<Boolean> =
        if (q.startsWith("at://")) Segments.uri eq q
        else Segments.uri like (LikePattern("%", ESCAPE) + LikePattern.ofLiteral("/$q", ESCAPE))

    /** The URI a candidate's published segment has: `at://<importer>/<feed.segment>/<candidate id>`. */
    private fun publishedUri(importer: Did): Expression<String> =
        concat(stringLiteral("at://${importer.value}/${Nsid.FEED_SEGMENT}/"), SegmentCandidateRows.id)

    private fun publishedFrom(importer: Did, region: String) =
        SegmentCandidateRows.select(publishedUri(importer)).where { SegmentCandidateRows.region eq region }

    companion object {
        const val PAGE_SIZE = 50
        private val SIGNED_OUT: Did? = null
        private const val ESCAPE = '\\'
    }
}
