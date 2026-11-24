package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.AvatarUrls
import io.github.solcott.activitytracker.appview.api.LabelSources
import io.github.solcott.activitytracker.appview.api.SegmentViews
import io.github.solcott.activitytracker.appview.db.SegmentEfforts
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.TrailDetailSuggestions
import io.github.solcott.activitytracker.appview.db.TrailDetailVotes
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.appview.labels.LabelStore
import io.github.solcott.activitytracker.appview.segments.ProposalState
import io.github.solcott.activitytracker.appview.segments.SegmentNameRules
import io.github.solcott.activitytracker.appview.segments.TrailDetailAdoption
import io.github.solcott.activitytracker.appview.segments.TrailDetailRules
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.TrailDetail
import io.github.solcott.activitytracker.lexicon.app.TrailDetailInfo
import io.github.solcott.activitytracker.lexicon.app.TrailDetailVoteDirection
import io.github.solcott.activitytracker.lexicon.app.TrailSource
import io.github.solcott.activitytracker.lexicon.app.Visibility
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/** Crowd-sourced trail details (segments/trail-detail-suggestions): suggestions, votes, confirmations, adoption, labels and views. */
@Suppress("LargeClass") // A test per scenario of segments/trail-detail-suggestions.
class TrailDetailSuggestionsTest : SegmentTestBase() {
    private val labeler = "did:plc:labeler"
    private val clock = MutableClock(Instant.parse("2026-11-20T12:00:00Z"))
    private val rules = TrailDetailRules()
    private val idx = Indexer(db, { did -> did.value.substringAfterLast(':') + ".test" }, clock, Did(importer), SegmentNameRules(), rules)
    private val adoption = TrailDetailAdoption(db, clock, rules)
    private val views = SegmentViews(db, AvatarUrls("https://appview.test"), clock, segmentImporter = Did(importer), trailDetails = rules)
    private val seg = segmentUri("3ks", importer)
    private var effortIds = 0

    @AfterTest fun forgetLabelers() = run { LabelSources.trusted = emptyList() }

    private fun later(by: Duration) = run { clock.now += by }

    /** An active ride segment by [did], its record holding [trail]. */
    private fun activeSegment(rkey: String = "3ks", did: String = importer, trail: String? = """{"dog":"leashed"}""") {
        val json =
            segmentJson(sport = "ride", name = "Wiggly Woods").let { base ->
                trail?.let { base.replaceFirst("{", """{"trail":$it,""") } ?: base
            }
        commitAs(did, Nsid.FEED_SEGMENT, rkey, json)
        runBlocking { db.tx { Segments.update({ Segments.uri eq segmentUri(rkey, did) }) { it[status] = SegmentStatus.ACTIVE } } }
    }

    /** [days] efforts by [did] on [segment], on distinct dates ending [endingDaysAgo] before now. */
    private fun efforts(did: String, days: Int, segment: String = seg, endingDaysAgo: Long = 30) = runBlocking {
        val last = LocalDate.of(2026, 11, 20).minusDays(endingDaysAgo)
        db.tx {
            SegmentEfforts.batchInsert(0 until days) { i ->
                this[SegmentEfforts.id] = "effort-${effortIds++}"
                this[SegmentEfforts.segmentUri] = segment
                this[SegmentEfforts.activityUri] = activityUri("3k$i", did)
                this[SegmentEfforts.trackUri] = trackUri("3k$i", did)
                this[SegmentEfforts.authorDid] = did
                this[SegmentEfforts.sport] = "ride"
                this[SegmentEfforts.eBike] = false
                this[SegmentEfforts.startOffsetS] = i * 10L
                this[SegmentEfforts.elapsedS] = 600
                this[SegmentEfforts.visibility] = Visibility.SELF
                this[SegmentEfforts.startedOn] = last.minusDays(i.toLong()).toString()
            }
        }
    }

    private fun commitAs(did: String, collection: String, rkey: String, record: String?) = runBlocking {
        idx.handle(
            JetstreamEvent.Commit(
                did = did,
                time = "2026-11-20T12:00:00.000000Z",
                operation = if (record == null) "delete" else "create",
                collection = collection,
                rkey = rkey,
                record = record?.let { AtJson.parseToJsonElement(it).jsonObject },
                cid = "bafy$rkey${record?.hashCode()}",
            ),
        )
    }

    private fun suggestionUri(rkey: String, did: String = alice) = "at://$did/${Nsid.FEED_TRAIL_DETAIL_SUGGESTION}/$rkey"

    private fun suggest(rkey: String, detail: String, value: String, did: String = alice, segment: String = seg) =
        commitAs(
            did,
            Nsid.FEED_TRAIL_DETAIL_SUGGESTION,
            rkey,
            """{"segment":"$segment","detail":"$detail","value":$value,"createdAt":"2026-11-20T00:00:00Z"}""",
        )

    private fun interactions(did: String) = space(AthleteSpace.INTERACTIONS, did)

    private fun vote(voter: String, suggestion: String, direction: String = TrailDetailVoteDirection.UP, rkey: String = "3kv") =
        runBlocking {
            idx.spaceWrite(
                interactions(voter),
                Nsid.FEED_TRAIL_DETAIL_VOTE,
                rkey,
                "bafyvote",
                AtJson.parseToJsonElement(
                        """{"subject":{"uri":"$suggestion","cid":"${cidOf(suggestion)}"},"direction":"$direction",""" +
                            """"createdAt":"2026-11-20T00:00:00Z"}""",
                    )
                    .jsonObject,
            )
        }

    private fun confirm(
        voter: String,
        detail: String,
        value: String,
        direction: String = TrailDetailVoteDirection.STILL,
        rkey: String = "3kc",
    ) = runBlocking {
        idx.spaceWrite(
            interactions(voter),
            Nsid.FEED_TRAIL_DETAIL_VOTE,
            rkey,
            "bafyconfirm",
            AtJson.parseToJsonElement(
                    """{"segment":"$seg","detail":"$detail","value":$value,"direction":"$direction","createdAt":"2026-11-20T00:00:00Z"}""",
                )
                .jsonObject,
        )
    }

    private fun rows() = runBlocking { db.tx { TrailDetailSuggestions.selectAll().associateBy { it[TrailDetailSuggestions.uri] } } }

    private fun cidOf(suggestion: String) = rows().getValue(suggestion)[TrailDetailSuggestions.cid]

    private fun state(suggestion: String) = rows()[suggestion]?.get(TrailDetailSuggestions.state)

    private fun view(viewer: String? = null, segment: String = seg): SegmentView = runBlocking {
        views.segment(viewer?.let(::Did), GetSegment.Params(AtUri(segment)))
    }
        .segment

    private fun info(detail: String, viewer: String? = null): TrailDetailInfo? = view(viewer).trailDetails.find { it.detail == detail }

    private fun pass() = runBlocking { adoption.update() }

    private fun label(uri: String, value: String, neg: Boolean = false, cts: String = "2026-11-29T10:00:00Z") = runBlocking {
        LabelSources.trusted = listOf(labeler)
        LabelStore(db, labeler, null, clock).apply(labeler, 1, listOf(testLabel(labeler, uri, value, cts, labelExtras(neg = neg))))
    }

    /** Alice's open "No dogs", voted on by [up] and [down] fresh riders. */
    private fun votedNoDogs(up: Int, down: Int, rkey: String = "3kp"): String {
        efforts(alice, 5)
        val uri = suggestionUri(rkey)
        suggest(rkey, TrailDetail.DOG, """{"dog":"no"}""")
        repeat(up) { i -> efforts("did:plc:up$i", 1).also { vote("did:plc:up$i", uri) } }
        repeat(down) { i -> efforts("did:plc:down$i", 1).also { vote("did:plc:down$i", uri, TrailDetailVoteDirection.DOWN) } }
        return uri
    }

    // A trail detail suggestion is a public record; only regulars may suggest

    @Test
    fun aRegularsSuggestionIsKept() {
        activeSegment()
        efforts(alice, 5)
        suggest("3kp", TrailDetail.DOG, """{"dog":"no"}""")
        assertEquals(ProposalState.OPEN, state(suggestionUri("3kp")))
        assertEquals(listOf(TrailDetail.DOG), view(bob).trailSuggestions.map { it.detail })
    }

    @Test
    fun aNonRegularsSuggestionIsIgnored() {
        activeSegment()
        efforts(alice, 4)
        suggest("3kp", TrailDetail.DOG, """{"dog":"no"}""")
        assertNull(state(suggestionUri("3kp")))
    }

    @Test
    fun valuesOutsideTheVocabularyAndOfficialDetailsAreIgnored() {
        activeSegment()
        efforts(alice, 5)
        suggest("3kp", TrailDetail.DOG, """{"dog":"sometimes"}""")
        suggest("3kq", TrailDetail.WEBSITE, """{"website":"https://example.com"}""")
        assertTrue(rows().isEmpty())
    }

    @Test
    fun aSuggestionInASpaceIsIgnored() = runBlocking {
        activeSegment()
        efforts(alice, 5)
        idx.spaceWrite(
            interactions(alice),
            Nsid.FEED_TRAIL_DETAIL_SUGGESTION,
            "3kp",
            "bafy",
            AtJson.parseToJsonElement("""{"segment":"$seg","detail":"dog","value":{"dog":"no"},"createdAt":"2026-11-20T00:00:00Z"}""")
                .jsonObject,
        )
        assertTrue(rows().isEmpty())
    }

    @Test
    fun anAthletesOwnSegmentTakesSuggestions() {
        activeSegment(rkey = "3kb", did = bob, trail = null)
        efforts(alice, 5, segment = segmentUri("3kb", bob))
        suggest("3kp", TrailDetail.SURFACE, """{"surfaces":[{"surface":"gravel","percent":100}]}""", segment = segmentUri("3kb", bob))
        assertEquals(ProposalState.OPEN, state(suggestionUri("3kp")))
    }

    // A suggestion must offer a new value

    @Test
    fun theValueInEffectAndASecondOpenSuggestionAreIgnored() {
        activeSegment()
        efforts(alice, 5)
        suggest("3kp", TrailDetail.DOG, """{"dog":"leashed"}""")
        assertNull(state(suggestionUri("3kp")), "the same as the importer's value")
        suggest("3kq", TrailDetail.DOG, """{"dog":"no"}""")
        suggest("3kr", TrailDetail.DOG, """{"dog":"yes"}""")
        assertNull(state(suggestionUri("3kr")), "alice already has an open dogs suggestion")
        suggest("3ks", TrailDetail.LIT, """{"lit":"yes"}""")
        assertEquals(ProposalState.OPEN, state(suggestionUri("3ks")), "another detail")
    }

    // Editing a suggestion clears its votes

    @Test
    fun anEditClearsVotesAndRestartsItsTimeOpen() {
        activeSegment()
        val uri = votedNoDogs(up = 3, down = 0)
        later(2.days)
        suggest("3kp", TrailDetail.DOG, """{"dog":"yes"}""")
        val edited = view(bob).trailSuggestions.single()
        assertEquals("yes", edited.value.dog)
        assertEquals(0, edited.up)
        assertEquals(clock.now.toEpochMilliseconds(), rows().getValue(uri)[TrailDetailSuggestions.openedAt])
    }

    // Votes and confirmations are private records; only riders' votes count

    @Test
    fun votesFromARepoAreIgnoredAndNonRidersDontCount() {
        activeSegment()
        val uri = votedNoDogs(up = 0, down = 0)
        commitAs(
            bob,
            Nsid.FEED_TRAIL_DETAIL_VOTE,
            "3kv",
            """{"subject":{"uri":"$uri","cid":"${cidOf(uri)}"},"direction":"up","createdAt":"2026-11-20T00:00:00Z"}""",
        )
        assertTrue(runBlocking { db.tx { TrailDetailVotes.selectAll().toList() } }.isEmpty())
        vote(carol, uri)
        assertEquals(0, view(bob).trailSuggestions.single().up, "carol has no efforts")
        vote(alice, uri)
        assertEquals(0, view(bob).trailSuggestions.single().up, "the author's own vote")
        efforts(carol, 1)
        assertEquals(1, view(bob).trailSuggestions.single().up, "carol's vote counts once she has an effort")
    }

    // A suggestion is adopted once enough riders agree; adopted values become the effective details

    @Test
    fun aQuietSegmentAdoptsAfterAWeek() {
        activeSegment()
        val uri = votedNoDogs(up = 3, down = 1)
        pass()
        assertEquals(ProposalState.OPEN, state(uri), "too soon")
        later(8.days)
        pass()
        assertEquals(ProposalState.ADOPTED, state(uri))
        val view = view()
        assertEquals("no", view.trail?.dog)
        assertEquals(TrailDetailInfo(TrailDetail.DOG, TrailSource.COMMUNITY, riders = 3, confirmations = 0), view.trailDetails.single())
    }

    @Test
    fun reimportKeepsTheAdoptedValue() {
        aQuietSegmentAdoptsAfterAWeek()
        activeSegment(trail = """{"dog":"yes","lit":"yes"}""")
        assertEquals("no", view().trail?.dog)
        assertEquals("yes", view().trail?.lit)
    }

    @Test
    fun anAthletesSegmentServesOnlyItsCommunityValue() {
        activeSegment(rkey = "3kb", did = bob, trail = """{"mtbScale":2}""")
        val bobs = segmentUri("3kb", bob)
        efforts(alice, 5, segment = bobs)
        suggest("3kp", TrailDetail.MTB_SCALE, """{"mtbScale":3}""", segment = bobs)
        repeat(3) { i -> efforts("did:plc:up$i", 1, segment = bobs).also { vote("did:plc:up$i", suggestionUri("3kp")) } }
        assertNull(view(segment = bobs).trail, "an athlete's own trail is never served")
        later(8.days)
        pass()
        assertEquals(3, view(segment = bobs).trail?.mtbScale)
    }

    @Test
    fun adoptingSupersedesAndStartsTheDetailsCooldown() {
        activeSegment()
        val adopted = votedNoDogs(up = 3, down = 0)
        efforts(bob, 5)
        suggest("3kb", TrailDetail.DOG, """{"dog":"yes"}""", did = bob)
        later(8.days)
        pass()
        assertEquals(ProposalState.ADOPTED, state(adopted))
        assertEquals(ProposalState.SUPERSEDED, state(suggestionUri("3kb", bob)))
        later(10.days)
        suggest("3kq", TrailDetail.DOG, """{"dog":"yes"}""")
        assertNull(state(suggestionUri("3kq")), "in the dogs cooldown")
        suggest("3kr", TrailDetail.LIT, """{"lit":"yes"}""")
        assertEquals(ProposalState.OPEN, state(suggestionUri("3kr")), "lighting is outside it")
        assertEquals(listOf(TrailDetail.LIT, TrailDetail.DOG), view(alice).viewer?.closedTrailDetails)
    }

    @Test
    fun votedDownIsRejected() {
        activeSegment()
        val uri = votedNoDogs(up = 1, down = 6)
        pass()
        assertEquals(ProposalState.REJECTED, state(uri))
        assertTrue(view(bob).trailSuggestions.isEmpty())
    }

    // Each served detail says where it came from

    @Test
    fun importedDetailsNameTheirSource() {
        activeSegment(trail = """{"dog":"leashed","widthCm":60,"sources":{"width":"usfs"}}""")
        val details = view().trailDetails.associate { it.detail to it.source }
        assertEquals(mapOf(TrailDetail.WIDTH to TrailSource.USFS, TrailDetail.DOG to TrailSource.IMPORTED), details)
        assertNull(view().trail?.sources, "the sources travel in trailDetails")
    }

    // Confirmations and doubts are served

    @Test
    fun confirmationsCountRidersAndShowTheMonthFromTwo() {
        activeSegment()
        efforts(bob, 1)
        confirm(bob, TrailDetail.DOG, """{"dog":"leashed"}""")
        assertEquals(1, info(TrailDetail.DOG)?.confirmations)
        assertNull(info(TrailDetail.DOG)?.confirmedMonth)
        assertEquals(TrailDetailVoteDirection.STILL, info(TrailDetail.DOG, bob)?.viewerConfirmation?.direction)
        efforts(carol, 1)
        confirm(carol, TrailDetail.DOG, """{"dog":"leashed"}""")
        assertEquals(2, info(TrailDetail.DOG)?.confirmations)
        assertEquals("2026-11", info(TrailDetail.DOG)?.confirmedMonth)
        confirm("did:plc:stranger", TrailDetail.DOG, """{"dog":"leashed"}""")
        assertEquals(2, info(TrailDetail.DOG)?.confirmations, "a confirmation counts only from a rider")
    }

    @Test
    fun manyChangedSaysItMayHaveChanged() {
        activeSegment()
        repeat(4) { i ->
            efforts("did:plc:r$i", 1).also {
                confirm("did:plc:r$i", TrailDetail.DOG, """{"dog":"leashed"}""", TrailDetailVoteDirection.CHANGED)
            }
        }
        efforts(bob, 1)
        confirm(bob, TrailDetail.DOG, """{"dog":"leashed"}""")
        assertTrue(info(TrailDetail.DOG)?.mayHaveChanged == true)
    }

    @Test
    fun aConfirmationOfAReplacedValueStopsCounting() {
        activeSegment()
        efforts(bob, 1)
        confirm(bob, TrailDetail.DOG, """{"dog":"leashed"}""")
        val uri = votedNoDogs(up = 3, down = 0)
        later(8.days)
        pass()
        assertEquals(ProposalState.ADOPTED, state(uri))
        assertEquals(0, info(TrailDetail.DOG)?.confirmations)
    }

    // Viewers learn whether they may suggest and vote

    @Test
    fun aRiderWhoIsntARegularMayVoteButNotSuggest() {
        activeSegment()
        efforts(carol, 2, endingDaysAgo = 3)
        val state = assertNotNull(view(carol).viewer)
        assertTrue(state.canVoteOnTrailDetails)
        assertFalse(state.canSuggestTrailDetails)
        assertTrue(state.recentEffort)
        efforts(bob, 1, endingDaysAgo = 20)
        assertFalse(view(bob).viewer?.recentEffort == true, "20 days ago isn't recent")
        assertNull(view(null).viewer)
    }

    // Labels remove a suggestion or its value

    @Test
    fun aTakenDownAdoptedValueFallsBack() {
        activeSegment()
        val uri = votedNoDogs(up = 3, down = 0)
        later(8.days)
        pass()
        assertEquals("no", view().trail?.dog)
        label(uri, "!takedown")
        assertEquals("leashed", view().trail?.dog)
        label(uri, "!takedown", neg = true, cts = "2026-11-29T11:00:00Z")
        assertEquals("no", view().trail?.dog)
    }

    // Deletes

    @Test
    fun withdrawingAndDeletingTheSegmentDropRows() {
        activeSegment()
        val uri = votedNoDogs(up = 1, down = 0)
        commitAs(alice, Nsid.FEED_TRAIL_DETAIL_SUGGESTION, "3kp", null)
        assertNull(state(uri))
        assertTrue(runBlocking { db.tx { TrailDetailVotes.selectAll().toList() } }.isEmpty())
        votedNoDogs(up = 1, down = 0, rkey = "3kq")
        commitAs(importer, Nsid.FEED_SEGMENT, "3ks", null)
        assertTrue(rows().isEmpty())
    }
}
