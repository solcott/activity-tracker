package io.github.solcott.activitytracker.app.names

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.DateText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.SegmentNameAdoption
import io.github.solcott.activitytracker.lexicon.app.SegmentNameProposalView
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * What a place's names section says and decides (segments/segment-names, dive-sites/site-names, dev/shared-display-text), for a segment's
 * page and a dive site's alike: its original name, its open proposals with their totals and votes, and its name history.
 */
object PlaceNameTexts {
    /** "originally …" under a place's title, when a community name replaced its [original]. */
    fun originally(original: String): AppText = AppText.named("segment_originally", AppText.written(original))

    val propose: AppText = AppText.named("segment_name_propose")
    val proposals: AppText = AppText.named("segment_name_proposals")
    val history: AppText = AppText.named("segment_name_history")
    val edit: AppText = AppText.named("segment_name_edit")
    val delete: AppText = AppText.named("segment_name_delete")

    /** Why the votes are disabled: this session can't send one ([PlaceNameFields.canVoteHere]). */
    val votingUnavailable: AppText = AppText.named("segment_name_voting_unavailable")

    /**
     * Whether the section shows at all: nothing to list, nothing to propose and no failure take no space, given the place's [proposals] and
     * [history] counts.
     */
    fun shows(proposals: Int, history: Int, canPropose: Boolean, fields: PlaceNameFields): Boolean =
        proposals > 0 || history > 0 || canPropose || fields.error != null

    /** Who proposed [proposal] and its totals: "By @bob.test · 3 up · 1 down", the counts grouped. */
    fun proposedBy(proposal: SegmentNameProposalView): AppText =
        AppText.named(
            "place_name_proposal_line",
            AppText.named("segment_name_proposed_by", LabelText.handle(proposal.proposer.handle.value)),
            AppText.named("segment_name_totals", whole(proposal.up), whole(proposal.down)),
        )

    /** The spoken label of a vote for [proposal]. */
    fun voteUp(proposal: SegmentNameProposalView): AppText = AppText.named("segment_name_vote_up", AppText.written(proposal.name))

    /** The spoken label of a vote against [proposal]. */
    fun voteDown(proposal: SegmentNameProposalView): AppText = AppText.named("segment_name_vote_down", AppText.written(proposal.name))

    /** The viewer voted [proposal] up. */
    fun votedUp(proposal: SegmentNameProposalView): Boolean = proposal.viewerVote?.direction == SegmentNameVoteDirection.UP

    /** The viewer voted [proposal] down. */
    fun votedDown(proposal: SegmentNameProposalView): Boolean = proposal.viewerVote?.direction == SegmentNameVoteDirection.DOWN

    /**
     * The vote a tap on [up] or down sends for [proposal]: that direction, or null (taking the vote back) when it's the viewer's vote
     * already.
     */
    fun voteEvent(proposal: SegmentNameProposalView, up: Boolean): PlaceNameEvent.Vote {
        val direction = if (up) SegmentNameVoteDirection.UP else SegmentNameVoteDirection.DOWN
        return PlaceNameEvent.Vote(proposal.uri, direction.takeIf { it != proposal.viewerVote?.direction })
    }

    /** A history entry: the name, the day it was adopted in [zone] and its totals ("Shark Alley · Jan 5, 2026 · 3 up, 1 down"). */
    fun historyEntry(adoption: SegmentNameAdoption, zone: TimeZone): AppText {
        val day = adoption.adoptedAt.toLocalDateTime(zone).date
        return AppText.named(
            "segment_name_history_entry",
            AppText.written(adoption.name),
            DateText.date(day.year, day.month.ordinal + 1, day.day),
            whole(adoption.up),
            whole(adoption.down),
        )
    }

    /** Who proposed an adopted name; null when the record no longer says. */
    fun historyBy(adoption: SegmentNameAdoption): AppText? =
        adoption.proposer?.let { AppText.named("segment_name_history_by", LabelText.handle(it.handle.value)) }

    private fun whole(count: Int): AppText = AppText.written(formatWhole(count))
}
