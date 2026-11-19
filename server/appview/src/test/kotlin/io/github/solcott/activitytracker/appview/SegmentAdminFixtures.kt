package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.admin.SegmentAdminQueries
import io.github.solcott.activitytracker.appview.admin.SegmentsPage
import io.github.solcott.activitytracker.appview.api.LabelSources
import io.github.solcott.activitytracker.appview.api.SegmentCandidateViews
import io.github.solcott.activitytracker.appview.db.Labels
import io.github.solcott.activitytracker.appview.db.SegmentCandidateRiders
import io.github.solcott.activitytracker.appview.db.SegmentCandidateRows
import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.SegmentNameProposals
import io.github.solcott.activitytracker.appview.db.SegmentNameVotes
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.appview.segments.ProposalState
import io.github.solcott.activitytracker.appview.segments.SegmentPasses
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.PutSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidate
import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.time.Clock
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/**
 * Segments, efforts, proposals, votes, labels and candidates written straight into [db] for the Segments admin page's tests
 * (admin/segment-tools), with an [indexer] for the records and the pins. Efforts, proposals and votes are rows, not records, so a test sets
 * exactly the state it names.
 */
class SegmentAdminFixtures(val db: Database, val clock: Clock, val activeAthletes: Int = 3, terrain: Boolean = false) {
    val importer = "did:plc:importer"
    val labeler = "did:plc:labeler"
    val indexer =
        Indexer(
            db,
            { did -> did.value.substringAfterLast(':') + ".test" },
            clock,
            Did(importer),
            terrain = terrain,
            segmentActiveAthletes = activeAthletes,
        )
    val passes = SegmentPasses(db, indexer.segmentJobs, clock, activeAthletes, importer = Did(importer))
    val queries = SegmentAdminQueries(db, indexer.segmentPins, activeAthletes, Did(importer))
    val page = SegmentsPage(queries, passes, indexer.segmentPins, indexer.segmentJobs)
    private var ids = 0

    private val origin = GeoPoint(47.0, 8.0)
    private val metersPerDegreeLat = 6_371_000.0 * PI / 180
    private val metersPerDegreeLng = metersPerDegreeLat * cos(origin.lat * PI / 180)

    /** 1 km running north, [east] meters east of the origin: segments [east] apart don't duplicate each other. */
    fun line(east: Double = 0.0) =
        SegmentGeometry.encodePolyline(
            listOf(0.0, 1000.0).map { north -> GeoPoint(origin.lat + north / metersPerDegreeLat, origin.lng + east / metersPerDegreeLng) },
        )

    fun segmentUri(rkey: String, did: String = "did:plc:alice") = "at://$did/${Nsid.FEED_SEGMENT}/$rkey"

    /** [did]'s segment [rkey] [east] meters east, indexed from the repo; returns its URI. */
    fun segment(rkey: String, did: String = "did:plc:alice", east: Double = 0.0, sport: String = "run", name: String = "Hill"): String {
        commit(
            did,
            Nsid.FEED_SEGMENT,
            rkey,
            """{"name":"$name","sport":"$sport","line":"${line(east)}","source":"athlete","license":"CC0-1.0",""" +
                """"createdAt":"2026-10-01T00:00:00Z"}""",
        )
        return segmentUri(rkey, did)
    }

    fun commit(did: String, collection: String, rkey: String, record: String?) = runBlocking {
        indexer.handle(
            JetstreamEvent.Commit(
                did = did,
                time = "2026-10-14T12:00:00.000000Z",
                operation = if (record == null) "delete" else "create",
                collection = collection,
                rkey = rkey,
                record = record?.let { AtJson.parseToJsonElement(it).jsonObject },
                cid = "bafy$rkey${record?.hashCode()}",
            ),
        )
    }

    fun setStatus(uri: String, status: String) = runBlocking {
        db.tx { Segments.update({ Segments.uri eq uri }) { it[Segments.status] = status } }
    }

    fun row(uri: String) = runBlocking { db.tx { Segments.selectAll().where { Segments.uri eq uri }.single() } }

    fun status(uri: String): String = row(uri)[Segments.status]

    /** [count] efforts by [did] on [segment], one a day from [from], at [visibility], each [elapsed] seconds. */
    @Suppress("LongParameterList")
    fun efforts(
        did: String,
        segment: String,
        count: Int = 1,
        visibility: String = Visibility.PUBLIC,
        elapsed: Long = 600,
        from: java.time.LocalDate = java.time.LocalDate.of(2026, 10, 1),
    ) = runBlocking {
        db.tx {
            SegmentEfforts.batchInsert(0 until count) { i ->
                val n = ids++
                this[SegmentEfforts.id] = "effort-$n"
                this[SegmentEfforts.segmentUri] = segment
                this[SegmentEfforts.activityUri] = "at://$did/${Nsid.FEED_ACTIVITY}/3ke$n"
                this[SegmentEfforts.trackUri] = "at://$did/${Nsid.FEED_SEGMENT_TRACK}/3ke$n"
                this[SegmentEfforts.authorDid] = did
                this[SegmentEfforts.sport] = "run"
                this[SegmentEfforts.eBike] = false
                this[SegmentEfforts.startOffsetS] = 100L + n
                this[SegmentEfforts.elapsedS] = elapsed
                this[SegmentEfforts.visibility] = visibility
                this[SegmentEfforts.startedOn] = from.plusDays(i.toLong()).toString()
            }
        }
    }

    fun proposalUri(rkey: String, did: String) = "at://$did/${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/$rkey"

    /** An open proposal by [proposer] naming [segment] [name], opened at [openedAt]; returns its URI. */
    fun proposal(rkey: String, segment: String, proposer: String, name: String, openedAt: Long): String = runBlocking {
        val uri = proposalUri(rkey, proposer)
        db.tx {
            SegmentNameProposals.insert {
                it[SegmentNameProposals.uri] = uri
                it[cid] = "bafy$rkey"
                it[segmentUri] = segment
                it[proposerDid] = proposer
                it[SegmentNameProposals.name] = name
                it[state] = ProposalState.OPEN
                it[SegmentNameProposals.openedAt] = openedAt
            }
        }
        uri
    }

    fun proposalState(uri: String): String = runBlocking {
        db.tx { SegmentNameProposals.selectAll().where { SegmentNameProposals.uri eq uri }.single()[SegmentNameProposals.state] }
    }

    /** [voter]'s vote on [proposal] (at its stored CID). */
    fun vote(proposal: String, voter: String, up: Boolean = true) = runBlocking {
        db.tx {
            val cid = SegmentNameProposals.selectAll().where { SegmentNameProposals.uri eq proposal }.single()[SegmentNameProposals.cid]
            SegmentNameVotes.insert {
                it[uri] = "at://$voter/${Nsid.FEED_SEGMENT_NAME_VOTE}/${ids++}"
                it[voterDid] = voter
                it[proposalUri] = proposal
                it[proposalCid] = cid
                it[SegmentNameVotes.up] = up
                it[indexedAt] = clock.now().toEpochMilliseconds()
            }
        }
    }

    /** [value] on [uri] from the trusted labeler; call [forgetLabels] after. */
    fun label(uri: String, value: String) = runBlocking {
        LabelSources.trusted = listOf(labeler)
        db.tx {
            Labels.insert {
                it[src] = labeler
                it[Labels.uri] = uri
                it[Labels.value] = value
                it[cts] = 0
                it[indexedAt] = 0
            }
        }
    }

    fun forgetLabels() = run { LabelSources.trusted = emptyList() }

    /** Stages candidates [ids] for [region] in [revision] as the importer. */
    fun stage(region: String, revision: String, vararg ids: String, east: Double = 5000.0) = runBlocking {
        val candidates = ids.mapIndexed { i, id ->
            SegmentCandidate(id, "ride", "Trail $id", line(east + i * 3000), SegmentSource.OSM, "ODbL-1.0")
        }
        SegmentCandidateViews(db, Did(importer), indexer.segmentJobs)
            .put(Did(importer), PutSegmentCandidates.Input(region, revision, candidates))
    }

    fun ride(candidate: String, rider: String, ridden: Boolean = false) = runBlocking {
        db.tx {
            SegmentCandidateRiders.insert {
                it[candidateId] = candidate
                it[authorDid] = rider
                it[trackUri] = "at://$rider/${Nsid.FEED_SEGMENT_TRACK}/3kt${ids++}"
            }
            if (ridden) SegmentCandidateRows.update({ SegmentCandidateRows.id eq candidate }) { it[SegmentCandidateRows.ridden] = true }
        }
    }
}
