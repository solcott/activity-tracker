package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.AvatarUrls
import io.github.solcott.activitytracker.appview.api.LabelSources
import io.github.solcott.activitytracker.appview.api.SegmentViews
import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.SegmentNameProposals
import io.github.solcott.activitytracker.appview.db.SegmentNameVotes
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.appview.labels.LabelStore
import io.github.solcott.activitytracker.appview.segments.ProposalState
import io.github.solcott.activitytracker.appview.segments.SegmentNameRules
import io.github.solcott.activitytracker.appview.segments.SegmentNaming
import io.github.solcott.activitytracker.appview.segments.Tally
import io.github.solcott.activitytracker.appview.segments.tallies
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetActivitySegments
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.Visibility
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/** Community names for segments (segments/segment-names): proposals, votes, the naming pass, labels and what `getSegment` serves. */
@Suppress("LargeClass") // A test per scenario of segments/segment-names.
class SegmentNamesTest : SegmentTestBase() {
    private val dave = "did:plc:dave"
    private val labeler = "did:plc:labeler"
    private val clock = MutableClock(Instant.parse("2026-10-28T12:00:00Z"))
    private val rules = SegmentNameRules()
    private val names = Indexer(db, { did -> did.value.substringAfterLast(':') + ".test" }, clock, Did(importer), rules)
    private val naming = SegmentNaming(db, clock, rules)
    private val views = SegmentViews(db, AvatarUrls("https://appview.test"), clock, names = rules)
    private val seg = segmentUri("3ks", importer)
    private var effortIds = 0

    @AfterTest fun forgetLabelers() = run { LabelSources.trusted = emptyList() }

    private fun later(by: Duration) = run { clock.now += by }

    /** The importer's active "Trail 214" ride segment. */
    private fun activeSegment(rkey: String = "3ks", name: String = "Trail 214") {
        segment(rkey, sport = "ride", did = importer, name = name)
        runBlocking { db.tx { Segments.update({ Segments.uri eq segmentUri(rkey, importer) }) { it[status] = SegmentStatus.ACTIVE } } }
    }

    /** [days] efforts by [did] on [segment] on distinct dates (or all on one date), at [visibility], within the speed cap. */
    private fun efforts(did: String, days: Int, segment: String = seg, sameDay: Boolean = false, visibility: String = Visibility.SELF) =
        runBlocking {
            db.tx {
                SegmentEfforts.batchInsert(0 until days) { i ->
                    val date = LocalDate.of(2026, 9, 1).plusDays(if (sameDay) 0 else i.toLong())
                    this[SegmentEfforts.id] = "effort-${effortIds++}"
                    this[SegmentEfforts.segmentUri] = segment
                    this[SegmentEfforts.activityUri] = activityUri("3k$i", did)
                    this[SegmentEfforts.trackUri] = trackUri("3k$i", did)
                    this[SegmentEfforts.authorDid] = did
                    this[SegmentEfforts.sport] = "ride"
                    this[SegmentEfforts.eBike] = false
                    this[SegmentEfforts.startOffsetS] = i * 10L
                    this[SegmentEfforts.elapsedS] = 600
                    this[SegmentEfforts.visibility] = visibility
                    this[SegmentEfforts.startedOn] = date.toString()
                }
            }
        }

    /** One recent effort on [segment] by each of [count] other riders, so it has that many riders this year. */
    private fun riders(count: Int, segment: String = seg) = repeat(count) { efforts("did:plc:rider$it", 1, segment) }

    private fun proposalUri(rkey: String, did: String = alice) = "at://$did/${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/$rkey"

    private fun propose(rkey: String, name: String, did: String = alice, segment: String = seg) =
        commitNames(
            did,
            Nsid.FEED_SEGMENT_NAME_PROPOSAL,
            rkey,
            """{"segment":"$segment","name":"$name","createdAt":"2026-10-28T00:00:00Z"}""",
        )

    private fun withdraw(rkey: String, did: String = alice) = commitNames(did, Nsid.FEED_SEGMENT_NAME_PROPOSAL, rkey, null)

    private fun commitNames(did: String, collection: String, rkey: String, record: String?) = runBlocking {
        names.handle(
            JetstreamEvent.Commit(
                did = did,
                time = "2026-10-28T12:00:00.000000Z",
                operation = if (record == null) "delete" else "create",
                collection = collection,
                rkey = rkey,
                record = record?.let { AtJson.parseToJsonElement(it).jsonObject },
                cid = "bafy$rkey${record?.hashCode()}",
            )
        )
    }

    private fun voteJson(proposal: String, direction: String, cid: String = cidOf(proposal)) =
        """{"subject":{"uri":"$proposal","cid":"$cid"},"direction":"$direction","createdAt":"2026-10-28T00:00:00Z"}"""

    private fun vote(voter: String, proposal: String, direction: String = SegmentNameVoteDirection.UP, rkey: String = "3kv") = runBlocking {
        names.spaceWrite(
            interactions(voter),
            Nsid.FEED_SEGMENT_NAME_VOTE,
            rkey,
            "bafyvote",
            AtJson.parseToJsonElement(voteJson(proposal, direction)).jsonObject,
        )
    }

    private fun unvote(voter: String, rkey: String = "3kv") = runBlocking {
        names.spaceDelete(interactions(voter), Nsid.FEED_SEGMENT_NAME_VOTE, rkey)
    }

    private fun interactions(did: String) = space(AthleteSpace.INTERACTIONS, did)

    private data class Proposal(
        val state: String,
        val name: String,
        val cid: String,
        val openedAt: Long,
        val finalUp: Int?,
        val finalDown: Int?,
    )

    private fun proposals(): Map<String, Proposal> = runBlocking {
        db.tx {
            SegmentNameProposals.selectAll().associate {
                it[SegmentNameProposals.uri] to
                    Proposal(
                        it[SegmentNameProposals.state],
                        it[SegmentNameProposals.name],
                        it[SegmentNameProposals.cid],
                        it[SegmentNameProposals.openedAt],
                        it[SegmentNameProposals.finalUp],
                        it[SegmentNameProposals.finalDown],
                    )
            }
        }
    }

    private fun cidOf(proposal: String) = proposals().getValue(proposal).cid

    private fun state(proposal: String) = proposals()[proposal]?.state

    private fun tally(proposal: String): Tally = runBlocking { db.tx { tallies(seg, listOf(proposal)) } }.getValue(proposal)

    private fun currentName(segment: String = seg) = runBlocking {
        db.tx { Segments.selectAll().where { Segments.uri eq segment }.single()[Segments.currentName] }
    }

    private fun view(viewer: String? = null, segment: String = seg): SegmentView = runBlocking {
        views.segment(viewer?.let(::Did), GetSegment.Params(AtUri(segment)))
    }
        .segment

    private fun pass() = runBlocking { naming.update() }

    /** Alice's open "Wiggly Woods", with [up] up votes and [down] down votes from riders. */
    private fun votedProposal(up: Int, down: Int, name: String = "Wiggly Woods", rkey: String = "3kp", did: String = alice): String {
        val uri = proposalUri(rkey, did)
        propose(rkey, name, did)
        repeat(up) { i -> efforts("did:plc:up$rkey$i", 1).also { vote("did:plc:up$rkey$i", uri) } }
        repeat(down) { i -> efforts("did:plc:down$rkey$i", 1).also { vote("did:plc:down$rkey$i", uri, SegmentNameVoteDirection.DOWN) } }
        return uri
    }

    /** Alice's "Wiggly Woods", adopted after 8 days with 6 up and 1 down. */
    private fun adoptWigglyWoods(): String {
        activeSegment()
        efforts(alice, 5)
        val uri = votedProposal(up = 6, down = 1)
        later(8.days)
        pass()
        assertEquals(ProposalState.ADOPTED, state(uri))
        return uri
    }

    private fun label(uri: String, value: String, neg: Boolean = false, cts: String = "2026-10-29T10:00:00Z") = runBlocking {
        LabelSources.trusted = listOf(labeler)
        LabelStore(db, labeler, null, clock).apply(labeler, 1, listOf(testLabel(labeler, uri, value, cts, labelExtras(neg = neg))))
    }

    // ---------------------------------------------------------------- A name proposal is a public record

    @Test
    fun aProposalIsWrittenToTheRepo() {
        activeSegment()
        efforts(alice, 5)
        propose("3kp", "Wiggly Woods")
        val proposal = proposals().getValue(proposalUri("3kp"))
        assertEquals(ProposalState.OPEN, proposal.state)
        assertEquals("Wiggly Woods", proposal.name)
        assertEquals(clock.now.toEpochMilliseconds(), proposal.openedAt)
    }

    @Test
    fun aBlankNameIsIgnored() {
        activeSegment()
        efforts(alice, 5)
        propose("3kp", "   ")
        propose("3kq", "x".repeat(101))
        assertEquals(emptyMap(), proposals())
    }

    @Test
    fun aProposalInASpaceIsIgnored() {
        activeSegment()
        efforts(alice, 5)
        runBlocking {
            names.spaceWrite(
                interactions(alice),
                Nsid.FEED_SEGMENT_NAME_PROPOSAL,
                "3kp",
                "bafyp",
                AtJson.parseToJsonElement("""{"segment":"$seg","name":"Wiggly Woods","createdAt":"2026-10-28T00:00:00Z"}""").jsonObject,
            )
        }
        assertEquals(emptyMap(), proposals())
    }

    @Test
    fun aProposalForAnUnknownSegmentIsIgnored() {
        efforts(alice, 5)
        propose("3kp", "Wiggly Woods")
        assertEquals(emptyMap(), proposals())
    }

    // ---------------------------------------------------------------- Only regulars may propose

    @Test
    fun fiveDaysOfEffortsOfAnyVisibilityAreEnough() {
        activeSegment()
        efforts(alice, 5, visibility = Visibility.SELF)
        propose("3kp", "Wiggly Woods")
        assertEquals(ProposalState.OPEN, state(proposalUri("3kp")))
    }

    @Test
    fun fiveEffortsInOneDayAreNotEnough() {
        activeSegment()
        efforts(bob, 5, sameDay = true)
        propose("3kp", "Wiggly Woods", did = bob)
        assertEquals(emptyMap(), proposals())
    }

    @Test
    fun implausiblyFastEffortsDontCount() {
        activeSegment()
        efforts(bob, 5)
        runBlocking { db.tx { SegmentEfforts.update({ SegmentEfforts.authorDid eq bob }) { it[elapsedS] = 1 } } }
        propose("3kp", "Wiggly Woods", did = bob)
        assertEquals(emptyMap(), proposals())
    }

    @Test
    fun aProposedSegmentTakesNoProposals() {
        segment("3ks", sport = "ride", did = importer, name = "Trail 214")
        efforts(alice, 5)
        propose("3kp", "Wiggly Woods")
        assertEquals(emptyMap(), proposals())
    }

    @Test
    fun anAcceptedProposalStaysWhenItsProposerLosesEfforts() {
        activeSegment()
        efforts(alice, 5)
        propose("3kp", "Wiggly Woods")
        runBlocking { db.tx { SegmentEfforts.deleteAll() } }
        pass()
        assertEquals(ProposalState.OPEN, state(proposalUri("3kp")))
    }

    // ---------------------------------------------------------------- A proposal must offer a new name

    @Test
    fun theCurrentNameIsntANewName() {
        activeSegment()
        efforts(alice, 5)
        propose("3kp", "trail 214 ")
        assertEquals(emptyMap(), proposals())
    }

    @Test
    fun anotherOpenProposalsNameIsntANewName() {
        activeSegment()
        efforts(alice, 5)
        efforts(bob, 5)
        propose("3kp", "Wiggly Woods")
        propose("3kq", "WIGGLY WOODS", did = bob)
        assertEquals(setOf(proposalUri("3kp")), proposals().keys)
    }

    @Test
    fun goingBackToTheOriginalIsAnOrdinaryProposal() {
        adoptWigglyWoods()
        later(91.days)
        efforts(bob, 5)
        propose("3kq", "Trail 214", did = bob)
        assertEquals(ProposalState.OPEN, state(proposalUri("3kq", bob)))
    }

    // ---------------------------------------------------------------- One open proposal per athlete per segment

    @Test
    fun aSecondOpenProposalIsIgnored() {
        activeSegment()
        efforts(alice, 5)
        propose("3kp", "Wiggly Woods")
        propose("3kq", "Rooty Run")
        assertEquals(setOf(proposalUri("3kp")), proposals().keys)
    }

    @Test
    fun withdrawingRemovesTheProposalAndItsVotes() {
        activeSegment()
        efforts(alice, 5)
        val uri = votedProposal(up = 2, down = 0)
        withdraw("3kp")
        assertEquals(emptyMap(), proposals())
        assertEquals(0L, runBlocking { db.tx { SegmentNameVotes.selectAll().count() } })
        propose("3kq", "Rooty Run")
        assertEquals(ProposalState.OPEN, state(proposalUri("3kq")))
        assertFalse(uri in proposals())
    }

    // ---------------------------------------------------------------- Editing a proposal's name clears its votes

    @Test
    fun anEditTakesTheNameAndClearsVotes() {
        activeSegment()
        efforts(alice, 5)
        val uri = votedProposal(up = 4, down = 1)
        val before = proposals().getValue(uri)
        later(1.days)
        propose("3kp", "Wiggly Woods Loop")
        val after = proposals().getValue(uri)
        assertEquals("Wiggly Woods Loop", after.name)
        assertEquals(clock.now.toEpochMilliseconds(), after.openedAt)
        assertTrue(after.cid != before.cid)
        assertEquals(Tally.NONE, tally(uri))
    }

    @Test
    fun anEditToTheCurrentNameKeepsTheProposal() {
        activeSegment()
        efforts(alice, 5)
        val uri = votedProposal(up = 4, down = 1)
        propose("3kp", "Trail 214")
        assertEquals("Wiggly Woods", proposals().getValue(uri).name)
        assertEquals(Tally(4, 1), tally(uri))
    }

    @Test
    fun anEditSkipsEligibilityAndTheOpenProposalCheck() {
        activeSegment()
        efforts(alice, 5)
        val uri = votedProposal(up = 1, down = 0)
        runBlocking { db.tx { SegmentEfforts.deleteAll() } }
        propose("3kp", "Rooty Run")
        assertEquals("Rooty Run", proposals().getValue(uri).name)
    }

    @Test
    fun aRewriteAfterAdoptionIsIgnored() {
        val uri = adoptWigglyWoods()
        propose("3kp", "Rooty Run")
        assertEquals("Wiggly Woods", proposals().getValue(uri).name)
        assertEquals("Wiggly Woods", currentName())
        assertEquals(listOf("Wiggly Woods"), view().nameHistory.map { it.name })
    }

    @Test
    fun aRewriteOfARejectedOrExpiredProposalIsIgnored() {
        activeSegment()
        efforts(alice, 5)
        efforts(bob, 5)
        val rejected = votedProposal(up = 0, down = 5)
        val expired = votedProposal(up = 0, down = 0, name = "Rooty Run", rkey = "3kq", did = bob)
        later(91.days)
        pass()
        assertEquals(ProposalState.REJECTED, state(rejected))
        assertEquals(ProposalState.EXPIRED, state(expired))
        propose("3kp", "Something Else")
        propose("3kq", "Another Thing", did = bob)
        assertEquals("Wiggly Woods", proposals().getValue(rejected).name)
        assertEquals("Rooty Run", proposals().getValue(expired).name)
    }

    @Test
    fun editingBackToAnEarlierNameDoesntBringItsVotesBack() {
        activeSegment()
        efforts(alice, 5)
        val uri = votedProposal(up = 3, down = 0)
        propose("3kp", "Rooty Run")
        // The same JSON again: a rewrite to the earlier name, which is a new version to the indexer.
        commitNames(
            alice,
            Nsid.FEED_SEGMENT_NAME_PROPOSAL,
            "3kp",
            """{"segment":"$seg","name":"Wiggly Woods","createdAt":"2026-10-29T00:00:00Z"}""",
        )
        assertEquals("Wiggly Woods", proposals().getValue(uri).name)
        assertEquals(Tally.NONE, tally(uri))
    }

    // ---------------------------------------------------------------- Proposals pause after an adoption

    @Test
    fun proposalsPauseAfterAnAdoption() {
        adoptWigglyWoods()
        efforts(bob, 5)
        later(30.days)
        propose("3kq", "Rooty Run", did = bob)
        assertNull(state(proposalUri("3kq", bob)))
        later(61.days)
        propose("3kq", "Rooty Run", did = bob)
        assertEquals(ProposalState.OPEN, state(proposalUri("3kq", bob)))
    }

    // ---------------------------------------------------------------- Votes are private records

    @Test
    fun aVoteFromTheInteractionsSpaceCounts() {
        activeSegment()
        efforts(alice, 5)
        efforts(bob, 1)
        propose("3kp", "Wiggly Woods")
        vote(bob, proposalUri("3kp"))
        assertEquals(Tally(1, 0), tally(proposalUri("3kp")))
    }

    @Test
    fun onlyTheLatestVoteCounts() {
        activeSegment()
        efforts(alice, 5)
        efforts(bob, 1)
        propose("3kp", "Wiggly Woods")
        vote(bob, proposalUri("3kp"), SegmentNameVoteDirection.UP)
        vote(bob, proposalUri("3kp"), SegmentNameVoteDirection.DOWN)
        assertEquals(Tally(0, 1), tally(proposalUri("3kp")))
        vote(bob, proposalUri("3kp"), SegmentNameVoteDirection.UP, rkey = "3kw")
        assertEquals(Tally(1, 0), tally(proposalUri("3kp")), "a newer vote record replaces the older one")
        unvote(bob, rkey = "3kw")
        assertEquals(Tally(0, 1), tally(proposalUri("3kp")))
        unvote(bob)
        assertEquals(Tally.NONE, tally(proposalUri("3kp")))
    }

    @Test
    fun aVoteInARepoIsIgnored() {
        activeSegment()
        efforts(alice, 5)
        efforts(bob, 1)
        propose("3kp", "Wiggly Woods")
        commitNames(bob, Nsid.FEED_SEGMENT_NAME_VOTE, "3kv", voteJson(proposalUri("3kp"), SegmentNameVoteDirection.UP))
        assertEquals(Tally.NONE, tally(proposalUri("3kp")))
    }

    @Test
    fun anUnknownDirectionRemovesTheVote() {
        activeSegment()
        efforts(alice, 5)
        efforts(bob, 1)
        propose("3kp", "Wiggly Woods")
        vote(bob, proposalUri("3kp"))
        vote(bob, proposalUri("3kp"), "sideways")
        assertEquals(Tally.NONE, tally(proposalUri("3kp")))
    }

    // ---------------------------------------------------------------- Only riders may vote

    @Test
    fun aVoterWithoutEffortsDoesntCountUntilTheyRide() {
        activeSegment()
        efforts(alice, 5)
        propose("3kp", "Wiggly Woods")
        vote(carol, proposalUri("3kp"))
        assertEquals(Tally.NONE, tally(proposalUri("3kp")))
        efforts(carol, 1)
        assertEquals(Tally(1, 0), tally(proposalUri("3kp")))
    }

    @Test
    fun theProposersOwnVoteDoesntCount() {
        activeSegment()
        efforts(alice, 5)
        propose("3kp", "Wiggly Woods")
        vote(alice, proposalUri("3kp"))
        assertEquals(Tally.NONE, tally(proposalUri("3kp")))
    }

    @Test
    fun aVoteOnAClosedProposalChangesNothing() {
        val uri = adoptWigglyWoods()
        efforts(dave, 1)
        vote(dave, uri, SegmentNameVoteDirection.DOWN)
        val history = view().nameHistory.single()
        assertEquals(6 to 1, history.up to history.down)
    }

    // ---------------------------------------------------------------- Adoption

    @Test
    fun aQuietTrailAdoptsWithFiveUpVotes() {
        activeSegment()
        efforts(alice, 5)
        riders(10)
        val uri = votedProposal(up = 5, down = 1)
        later(8.days)
        pass()
        assertEquals(ProposalState.ADOPTED, state(uri))
        assertEquals(5 to 1, proposals().getValue(uri).let { it.finalUp to it.finalDown })
        assertEquals("Wiggly Woods", currentName())
    }

    @Test
    fun aBusyTrailNeedsTenPercentOfItsRiders() {
        activeSegment()
        efforts(alice, 5)
        riders(270)
        // With the 27 voters and alice: 298 riders this year, so 30 up votes.
        val uri = votedProposal(up = 25, down = 2)
        later(8.days)
        pass()
        assertEquals(ProposalState.OPEN, state(uri))
        // Riders already counted, so the bar stays at 30.
        repeat(5) { i -> vote("did:plc:rider$i", uri) }
        pass()
        assertEquals(ProposalState.ADOPTED, state(uri))
    }

    @Test
    fun ridersFromOverAYearAgoDontRaiseTheBar() {
        activeSegment()
        efforts(alice, 5)
        val uri = votedProposal(up = 5, down = 0)
        runBlocking { db.tx { SegmentEfforts.update { it[startedOn] = "2025-01-01" } } }
        riders(40)
        runBlocking { db.tx { SegmentEfforts.update({ SegmentEfforts.authorDid eq "did:plc:rider0" }) { it[startedOn] = "2026-10-01" } } }
        later(8.days)
        pass()
        assertEquals(ProposalState.ADOPTED, state(uri))
    }

    @Test
    fun aProposalMustBeOpenSevenDays() {
        activeSegment()
        efforts(alice, 5)
        val uri = votedProposal(up = 40, down = 0)
        later(3.days)
        pass()
        assertEquals(ProposalState.OPEN, state(uri))
        later(4.days)
        pass()
        assertEquals(ProposalState.ADOPTED, state(uri))
    }

    @Test
    fun twoThirdsMustBeUp() {
        activeSegment()
        efforts(alice, 5)
        val uri = votedProposal(up = 6, down = 4)
        later(8.days)
        pass()
        assertEquals(ProposalState.OPEN, state(uri))
    }

    @Test
    fun theBestScoreWinsAndSupersedesTheRest() {
        activeSegment()
        efforts(alice, 5)
        efforts(bob, 5)
        val nine = votedProposal(up = 9, down = 0, name = "Rooty Run", rkey = "3kq", did = bob)
        val twelve = votedProposal(up = 12, down = 0)
        later(8.days)
        pass()
        assertEquals(ProposalState.ADOPTED, state(twelve))
        assertEquals(ProposalState.SUPERSEDED, state(nine))
        assertEquals(9 to 0, proposals().getValue(nine).let { it.finalUp to it.finalDown })
    }

    @Test
    fun aProposalVotedDownIsRejected() {
        activeSegment()
        efforts(alice, 5)
        val uri = votedProposal(up = 2, down = 7)
        pass()
        assertEquals(ProposalState.REJECTED, state(uri))
    }

    @Test
    fun anUndecidedProposalExpires() {
        activeSegment()
        efforts(alice, 5)
        val uri = votedProposal(up = 1, down = 0)
        later(89.days)
        pass()
        assertEquals(ProposalState.OPEN, state(uri))
        later(1.days)
        pass()
        assertEquals(ProposalState.EXPIRED, state(uri))
    }

    @Test
    fun aSegmentThatIsntActiveAdoptsNothing() {
        activeSegment()
        efforts(alice, 5)
        val uri = votedProposal(up = 6, down = 0)
        runBlocking { db.tx { Segments.update { it[status] = SegmentStatus.ARCHIVED } } }
        later(8.days)
        pass()
        assertEquals(ProposalState.OPEN, state(uri))
        assertEquals("Trail 214", currentName())
    }

    // ---------------------------------------------------------------- Current and original names

    @Test
    fun theImporterRefiningARenamedSegmentChangesOnlyItsOriginal() {
        adoptWigglyWoods()
        segment("3ks", sport = "ride", did = importer, name = "Trail 214 Connector")
        assertEquals("Wiggly Woods", currentName())
        val view = view()
        assertEquals("Wiggly Woods", view.name)
        assertEquals("Trail 214 Connector", view.record.name)
    }

    @Test
    fun aRenameWithNothingAdoptedRenamesTheSegment() {
        activeSegment()
        segment("3ks", sport = "ride", did = importer, name = "Trail 214 Connector")
        assertEquals("Trail 214 Connector", currentName())
    }

    @Test
    fun deletingASegmentDeletesItsProposalsAndVotes() {
        adoptWigglyWoods()
        commit(importer, Nsid.FEED_SEGMENT, "3ks", null)
        assertEquals(emptyMap(), proposals())
        assertEquals(0L, runBlocking { db.tx { SegmentNameVotes.selectAll().count() } })
    }

    @Test
    fun aDeletedAccountsOpenProposalsAndVotesGo() {
        activeSegment()
        efforts(alice, 5)
        efforts(bob, 5)
        val uri = votedProposal(up = 1, down = 0)
        propose("3kq", "Rooty Run", did = bob)
        vote(bob, uri)
        runBlocking {
            names.handle(
                JetstreamEvent.Account(
                    did = bob,
                    time = "2026-10-28T12:00:00.000000Z",
                    account = JetstreamEvent.Account.Body(bob, active = false, status = "deleted"),
                )
            )
        }
        assertEquals(setOf(uri), proposals().keys)
        assertEquals(Tally(1, 0), tally(uri))
    }

    // ---------------------------------------------------------------- Labels

    @Test
    fun aTakenDownAdoptedNameFallsBackAndReturnsWhenNegated() {
        val wiggly = adoptWigglyWoods()
        later(91.days)
        efforts(bob, 5)
        val rooty = votedProposal(up = 6, down = 0, name = "Rooty Run", rkey = "3kq", did = bob)
        later(8.days)
        pass()
        assertEquals(ProposalState.ADOPTED, state(rooty))
        assertEquals("Rooty Run", currentName())

        label(rooty, "!takedown")
        assertEquals("Wiggly Woods", currentName())
        assertEquals(listOf("Wiggly Woods"), view().nameHistory.map { it.name })

        label(rooty, "!takedown", neg = true, cts = "2026-10-29T11:00:00Z")
        assertEquals("Rooty Run", currentName())
        assertEquals(listOf("Rooty Run", "Wiggly Woods"), view().nameHistory.map { it.name })
        assertTrue(wiggly in proposals())
    }

    @Test
    fun aLabelledProposersAdoptedNameFallsBack() {
        adoptWigglyWoods()
        label(alice, "!takedown")
        assertEquals("Trail 214", currentName())
    }

    @Test
    fun aHiddenOpenProposalIsntListedCountedOrAdopted() {
        activeSegment()
        efforts(alice, 5)
        val uri = votedProposal(up = 6, down = 0)
        label(uri, "!hide")
        assertEquals(emptyList(), view(bob).nameProposals)
        later(8.days)
        pass()
        assertEquals(ProposalState.OPEN, state(uri))
    }

    // ---------------------------------------------------------------- Views

    @Test
    fun everyViewServesTheCurrentName() {
        matching(true)
        activity("3ka", sport = "ride")
        track("3ka")
        segment("3ks", sport = "ride", did = importer, name = "Trail 214")
        match()
        runBlocking { db.tx { Segments.update { it[status] = SegmentStatus.ACTIVE } } }
        efforts(alice, 5)
        val uri = votedProposal(up = 6, down = 0)
        later(8.days)
        pass()
        assertEquals(ProposalState.ADOPTED, state(uri))

        assertEquals("Wiggly Woods", view().name)
        val effort = runBlocking { views.activitySegments(null, GetActivitySegments.Params(AtUri(activityUri("3ka")))) }.efforts.single()
        assertEquals("Wiggly Woods", effort.segment.name)
        val found = runBlocking {
            views.search(
                null,
                SearchSegments.Params(minLat = 469_900_000, minLng = 79_900_000, maxLat = 470_100_000, maxLng = 80_100_000),
            )
        }
            .segments
        assertEquals(listOf("Wiggly Woods"), found.map { it.name })
        assertEquals(listOf("Trail 214"), found.map { it.record.name })
    }

    @Test
    fun historyListsAdoptionsNewestFirstAndDropsADeletedProposer() {
        val wiggly = adoptWigglyWoods()
        later(91.days)
        efforts(bob, 5)
        votedProposal(up = 6, down = 0, name = "Rooty Run", rkey = "3kq", did = bob)
        later(8.days)
        pass()
        val history = view(carol).nameHistory
        assertEquals(listOf("Rooty Run", "Wiggly Woods"), history.map { it.name })
        assertEquals(listOf(bob, alice), history.map { it.proposer?.did?.value })

        withdraw("3kp")
        assertTrue(wiggly in proposals(), "an adopted proposal keeps its row")
        assertEquals("Rooty Run", currentName())
        assertEquals(listOf(bob, null), view(carol).nameHistory.map { it.proposer?.did?.value })
    }

    @Test
    fun openProposalsShowTotalsAndTheViewersOwnVoteOnly() {
        activeSegment()
        efforts(alice, 5)
        efforts(bob, 1)
        val uri = votedProposal(up = 2, down = 0)
        vote(bob, uri, SegmentNameVoteDirection.DOWN)
        val proposal = view(bob).nameProposals.single()
        assertEquals(2 to 1, proposal.up to proposal.down)
        assertEquals(SegmentNameVoteDirection.DOWN, proposal.viewerVote?.direction)
        assertNull(view(carol).nameProposals.single().viewerVote)
        assertFalse(AtJson.encodeToString(SegmentView.serializer(), view(carol)).contains("did:plc:up"), "voters are never served")
    }

    @Test
    fun blockedAndMutedProposersArentListed() {
        activeSegment()
        efforts(alice, 5)
        votedProposal(up = 1, down = 0)
        block(bob, alice)
        runBlocking {
            names.spaceWrite(
                interactions(carol),
                Nsid.GRAPH_MUTE,
                alice,
                "bafymute",
                AtJson.parseToJsonElement("""{"subject":"$alice","createdAt":"2026-10-21T09:00:00Z"}""").jsonObject,
            )
        }
        assertEquals(emptyList(), view(bob).nameProposals)
        assertEquals(emptyList(), view(carol).nameProposals)
        assertEquals(1, view(dave).nameProposals.size)
    }

    @Test
    fun viewersLearnWhetherTheyMayProposeAndVote() {
        activeSegment()
        efforts(alice, 6)
        efforts(bob, 1)
        val alices = view(alice).viewer!!
        assertTrue(alices.canProposeName)
        assertTrue(alices.canVoteOnNames)
        val bobs = view(bob).viewer!!
        assertFalse(bobs.canProposeName)
        assertTrue(bobs.canVoteOnNames)
        assertFalse(view(carol).viewer!!.canVoteOnNames)
        assertNull(view().viewer)

        propose("3kp", "Wiggly Woods")
        assertFalse(view(alice).viewer!!.canProposeName, "she already has an open proposal")
    }
}
