package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.AvatarUrls
import io.github.solcott.activitytracker.appview.api.LabelSources
import io.github.solcott.activitytracker.appview.api.SegmentCandidateViews
import io.github.solcott.activitytracker.appview.api.SegmentViews
import io.github.solcott.activitytracker.appview.api.XrpcError
import io.github.solcott.activitytracker.appview.db.SegmentNameProposals
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.SubjectHandoffs
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.appview.index.handoffId
import io.github.solcott.activitytracker.appview.labels.LabelStore
import io.github.solcott.activitytracker.appview.segments.ProposalState
import io.github.solcott.activitytracker.appview.segments.recomputeCurrentName
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetHandoffs
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.SubjectHandoff
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/** Handing off a removed active segment to the segment importer (segments/segment-handoffs). */
class SegmentHandoffsTest : SegmentTestBase() {
    private val labeler = "did:plc:labeler"
    private val views = SegmentViews(db, AvatarUrls("https://appview.test"))
    private val importerViews = SegmentCandidateViews(db, Did(importer), indexer.segmentJobs)
    private val aliceSeg = segmentUri("3ks")

    @AfterTest fun forgetLabelers() = run { LabelSources.trusted = emptyList() }

    private fun active(uri: String) = runBlocking {
        db.tx { Segments.update({ Segments.uri eq uri }) { it[status] = SegmentStatus.ACTIVE } }
    }

    /** Alice's active "Hill" run segment. */
    private fun aliceActive(name: String = "Hill") {
        segment("3ks", name = name)
        active(aliceSeg)
    }

    /** An adopted name [name] on [segment] proposed by [proposer], as the naming pass leaves it; and an open one by someone else. */
    private fun adopted(segment: String, name: String, proposer: String = bob) = runBlocking {
        db.tx {
            SegmentNameProposals.insert {
                it[uri] = "at://$proposer/${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/3kp"
                it[cid] = "bafyp"
                it[segmentUri] = segment
                it[proposerDid] = proposer
                it[SegmentNameProposals.name] = name
                it[state] = ProposalState.ADOPTED
                it[openedAt] = 1
                it[closedAt] = 2
                it[finalUp] = 5
                it[finalDown] = 0
            }
            SegmentNameProposals.insert {
                it[uri] = "at://$carol/${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/3ko"
                it[cid] = "bafyo"
                it[segmentUri] = segment
                it[proposerDid] = carol
                it[SegmentNameProposals.name] = "Open One"
                it[state] = ProposalState.OPEN
                it[openedAt] = 3
            }
            recomputeCurrentName(segment)
        }
    }

    private fun handoffs() = runBlocking { db.tx { SubjectHandoffs.selectAll().toList() } }

    private fun proposals() = runBlocking {
        db.tx { SegmentNameProposals.selectAll().associate { it[SegmentNameProposals.uri] to it[SegmentNameProposals.segmentUri] } }
    }

    private fun lookUp(uri: String) = runBlocking { views.segment(null, GetSegment.Params(AtUri(uri))).segment }

    private fun pending() = runBlocking { importerViews.handoffs(Did(importer), GetHandoffs.Params()).handoffs }

    private fun deleteAccount(did: String) = runBlocking {
        indexer.handle(
            JetstreamEvent.Account(did, "2026-10-15T12:00:00.000000Z", account = JetstreamEvent.Account.Body(did, false, "deleted"))
        )
    }

    /** The importer republishing [handoff] as its repo's `feed.segment` under the handoff's id. */
    private fun republish(handoff: SubjectHandoff) =
        commit(
            importer,
            Nsid.FEED_SEGMENT,
            handoff.id,
            """{"name":"${handoff.name}","sport":"${handoff.sport}","line":"${handoff.line}","source":"athlete",""" +
                """"license":"CC0-1.0","createdAt":"2026-10-20T00:00:00Z"}""",
        )

    // ---------------------------------------------------------------- Deleting an active segment stages a handoff

    @Test
    fun deletingAnActiveSegmentKeepsAHandoffWithItsLineAndAdoptedNames() {
        aliceActive()
        adopted(aliceSeg, "Wiggly Woods")
        remove(alice, Nsid.FEED_SEGMENT, "3ks")

        val handoff = handoffs().single()
        assertEquals(handoffId(aliceSeg), handoff[SubjectHandoffs.id])
        assertEquals(aliceSeg, handoff[SubjectHandoffs.oldUri])
        assertEquals("Hill", handoff[SubjectHandoffs.originalName])
        assertEquals(lineOf(straight), handoff[SubjectHandoffs.line])
        assertNull(handoff[SubjectHandoffs.successorUri])
        // The adopted name stays under the old URI; the open proposal goes.
        assertEquals(mapOf("at://$bob/${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/3kp" to aliceSeg), proposals())
        assertEquals(0, runBlocking { db.tx { Segments.selectAll().count() } })
    }

    @Test
    fun aProposedSegmentIsNotHandedOff() {
        segment("3ks")
        remove(alice, Nsid.FEED_SEGMENT, "3ks")
        assertEquals(emptyList(), handoffs())
    }

    @Test
    fun anImporterSegmentIsNotHandedOff() {
        segment("3ki", did = importer)
        remove(importer, Nsid.FEED_SEGMENT, "3ki")
        assertEquals(emptyList(), handoffs())
    }

    @Test
    fun aTakenDownSegmentIsNotHandedOff() = runBlocking {
        aliceActive()
        LabelSources.trusted = listOf(labeler)
        LabelStore(db, labeler, null).apply(labeler, 1, listOf(testLabel(labeler, aliceSeg, "!takedown", "2026-10-15T10:00:00Z")))
        remove(alice, Nsid.FEED_SEGMENT, "3ks")
        assertEquals(emptyList(), handoffs())
    }

    @Test
    fun aDeletedAccountHandsOffOnlyItsActiveSegments() {
        aliceActive()
        segment("3kq", path = listOf(500.0 to 0.0, 500.0 to 1000.0))
        deleteAccount(alice)
        assertEquals(listOf(aliceSeg), handoffs().map { it[SubjectHandoffs.oldUri] })
    }

    @Test
    fun theOriginalWrittenAgainTakesItsHandoffOver() {
        aliceActive()
        remove(alice, Nsid.FEED_SEGMENT, "3ks")
        segment("3ks")
        assertEquals(aliceSeg, handoffs().single()[SubjectHandoffs.successorUri])
        assertEquals(emptyList(), pending())
    }

    @Test
    fun aReplayedCreateAndDeleteLeavesTheHandoffPendingWithItsNames() {
        aliceActive()
        adopted(aliceSeg, "Wiggly Woods")
        remove(alice, Nsid.FEED_SEGMENT, "3ks")
        // A rewound Jetstream cursor replays the create, which indexes the segment again as proposed, then the delete.
        segment("3ks")
        remove(alice, Nsid.FEED_SEGMENT, "3ks")

        assertNull(handoffs().single()[SubjectHandoffs.successorUri])
        assertEquals(listOf(handoffId(aliceSeg)), pending().map { it.id })
        assertEquals(mapOf("at://$bob/${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/3kp" to aliceSeg), proposals())
    }

    // ---------------------------------------------------------------- A live duplicate takes over a handoff

    @Test
    fun aDuplicateMadeLiveTakesOverAndServesTheOldUri() {
        aliceActive()
        adopted(aliceSeg, "Wiggly Woods")
        segment("3kc", did = carol, name = "Carol's Hill")
        assertEquals(SegmentStatus.DUPLICATE, lookUp(segmentUri("3kc", carol)).status)

        remove(alice, Nsid.FEED_SEGMENT, "3ks")

        assertEquals(segmentUri("3kc", carol), handoffs().single()[SubjectHandoffs.successorUri])
        val view = lookUp(aliceSeg)
        assertEquals(segmentUri("3kc", carol), view.uri.value)
        assertEquals("Wiggly Woods", view.name)
        assertEquals(emptyList(), pending())
    }

    @Test
    fun aSuccessorRemovedWithoutAHandoffGivesTheHandoffBack() {
        aliceActive()
        adopted(aliceSeg, "Wiggly Woods")
        segment("3kc", did = carol)
        remove(alice, Nsid.FEED_SEGMENT, "3ks")
        // Carol's took over as proposed, so deleting it hands nothing off of its own.
        remove(carol, Nsid.FEED_SEGMENT, "3kc")

        assertNull(handoffs().single()[SubjectHandoffs.successorUri])
        assertEquals(mapOf("at://$bob/${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/3kp" to aliceSeg), proposals())
        assertEquals(listOf(handoffId(aliceSeg)), pending().map { it.id })
    }

    // ---------------------------------------------------------------- The importer lists pending handoffs

    @Test
    fun theImporterListsAPendingHandoffWithNothingAboutRiders() {
        aliceActive()
        remove(alice, Nsid.FEED_SEGMENT, "3ks")
        assertEquals(
            listOf(
                SubjectHandoff(
                    id = handoffId(aliceSeg),
                    kind = SubjectHandoff.SEGMENT,
                    name = "Hill",
                    sport = "run",
                    segmentKind = "run",
                    line = lineOf(straight),
                )
            ),
            pending(),
        )
    }

    @Test
    fun anAthleteMayNotListHandoffs() {
        val error = assertFailsWith<XrpcError> { runBlocking { importerViews.handoffs(Did(bob), GetHandoffs.Params()) } }
        assertEquals("NotImporter", error.error)
    }

    @Test
    fun withNoImporterConfiguredNobodyMayListHandoffs() {
        val none = SegmentCandidateViews(db, null, indexer.segmentJobs)
        assertFailsWith<XrpcError> { runBlocking { none.handoffs(Did(importer), GetHandoffs.Params()) } }
    }

    @Test
    fun aHandoffALiveSegmentNowCoversIsLinkedAndLeftOut() {
        aliceActive()
        remove(alice, Nsid.FEED_SEGMENT, "3ks")
        segment("3kc", did = carol)

        assertEquals(emptyList(), pending())
        assertEquals(segmentUri("3kc", carol), handoffs().single()[SubjectHandoffs.successorUri])
    }

    // ---------------------------------------------------------------- Republishing

    @Test
    fun theOldUriIsNotFoundUntilRepublished() {
        aliceActive()
        remove(alice, Nsid.FEED_SEGMENT, "3ks")
        assertEquals("NotFound", assertFailsWith<XrpcError> { lookUp(aliceSeg) }.error)
    }

    @Test
    fun theRepublishedSegmentIsActiveWithItsNamesAndRidersEfforts() {
        matching(true, bob)
        aliceActive()
        adopted(aliceSeg, "Wiggly Woods")
        activity("3kr", did = bob)
        track("3kr", did = bob)
        match()
        assertEquals(listOf(aliceSeg), efforts().map { it.segment })

        remove(alice, Nsid.FEED_SEGMENT, "3ks")
        assertEquals(emptyList(), efforts())
        republish(pending().single())
        match()

        val copy = segmentUri(handoffId(aliceSeg), importer)
        assertEquals(copy, handoffs().single()[SubjectHandoffs.successorUri])
        val view = lookUp(aliceSeg)
        assertEquals(copy, view.uri.value)
        assertEquals(SegmentStatus.ACTIVE, view.status)
        assertEquals("Wiggly Woods", view.name)
        assertEquals("Hill", view.record.name)
        assertEquals(listOf(copy to bob), efforts().map { it.segment to it.activity.substringAfter("at://").substringBefore('/') })
        assertEquals(emptyList(), pending())
    }

    @Test
    fun aRepublishedSegmentHandedOffAgainResolvesThroughTheChain() {
        aliceActive()
        remove(alice, Nsid.FEED_SEGMENT, "3ks")
        republish(pending().single())
        val copy = segmentUri(handoffId(aliceSeg), importer)
        // An importer segment is never handed off itself, so a chain only grows through athletes' duplicates; the old URI still resolves.
        assertEquals(copy, lookUp(aliceSeg).uri.value)
        assertEquals(copy, lookUp(copy).uri.value)
    }
}
