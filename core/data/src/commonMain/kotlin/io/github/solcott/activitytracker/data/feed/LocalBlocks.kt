package io.github.solcott.activitytracker.data.feed

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewerState
import io.github.solcott.activitytracker.lexicon.app.ThreadView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * The athlete's blocks and mutes made this session, laid over the AppView's reads until it serves them, so a block or mute shows at once
 * and a reload can't undo it: a blocked account's activities and comments leave every list, a muted one's leave the timeline and comments,
 * and a profile shows the new `viewer.blocking`/`viewer.muting`. A change stays for the session; once the AppView agrees (a profile it
 * serves says so, [applyTo]) it's settled, and only an unsettled one still moves comment counts ([commentDelta]).
 */
@Suppress("TooManyFunctions") // A change and its undo for blocks and mutes each, and every read it's laid over.
class LocalBlocks {
    /** One account's block or mute as the athlete left it: on with the record at [record], or off when it's null. */
    data class Change(val record: AtUri?, val settled: Boolean = false) {
        val on: Boolean
            get() = record != null
    }

    data class Entries(
        val blocks: Map<Did, Change> = emptyMap(),
        val mutes: Map<Did, Change> = emptyMap(),
        /** Comments the AppView listed, by activity, with their authors and whether they were hidden: what [commentDelta] counts. */
        val listed: Map<AtUri, Map<AtUri, Listed>> = emptyMap(),
    )

    data class Listed(val author: Did, val hidden: Boolean)

    val entries: StateFlow<Entries>
        field = MutableStateFlow(Entries())

    /** Records the athlete's block of [subject] at [record], or its removal when null; returns what it replaced, for [restoreBlock]. */
    fun block(subject: Did, record: AtUri?): Change? {
        val previous = entries.value.blocks[subject]
        entries.update { it.copy(blocks = it.blocks + (subject to Change(record))) }
        return previous
    }

    fun mute(subject: Did, record: AtUri?): Change? {
        val previous = entries.value.mutes[subject]
        entries.update { it.copy(mutes = it.mutes + (subject to Change(record))) }
        return previous
    }

    fun restoreBlock(subject: Did, previous: Change?) = entries.update {
        it.copy(blocks = if (previous == null) it.blocks - subject else it.blocks + (subject to previous))
    }

    fun restoreMute(subject: Did, previous: Change?) = entries.update {
        it.copy(mutes = if (previous == null) it.mutes - subject else it.mutes + (subject to previous))
    }

    /** Whether the athlete blocked [did] this session (true), unblocked them (false), or neither (null). */
    fun blocked(did: Did): Boolean? = entries.value.blocks[did]?.on

    fun muted(did: Did): Boolean? = entries.value.mutes[did]?.on

    /** Whether [author]'s comments are left out for the athlete: blocked or muted this session. */
    fun hidesCommentsBy(author: Did, at: Entries = entries.value): Boolean = at.blocks[author]?.on == true || at.mutes[author]?.on == true

    /** [activities] without those by accounts blocked this session, and on the [timeline] also those muted. */
    fun filter(activities: List<ActivityView>, timeline: Boolean, at: Entries = entries.value): List<ActivityView> = activities.filterNot {
        at.blocks[it.author.did]?.on == true || (timeline && at.mutes[it.author.did]?.on == true)
    }

    /** [comments] without those by accounts blocked or muted this session. */
    fun filter(comments: List<CommentView>, at: Entries = entries.value): List<CommentView> = comments.filterNot {
        hidesCommentsBy(it.author.did, at)
    }

    /**
     * [thread] without what's left out ([hidesCommentsBy]): a top-level comment leaves its place to a placeholder (no root), as one the
     * AppView doesn't list, and the reply count drops with the replies removed.
     */
    fun applyTo(thread: ThreadView, at: Entries = entries.value): ThreadView {
        val root = thread.root?.takeUnless { hidesCommentsBy(it.author.did, at) }
        val replies = filter(thread.replies, at)
        if (root == thread.root && replies.size == thread.replies.size) return thread
        return thread.copy(
            root = root,
            replies = replies,
            replyCount = (thread.replyCount - (thread.replies.size - replies.size)).coerceAtLeast(0),
        )
    }

    /**
     * [view] with the athlete's block and mute of its athlete as they left them. A change the AppView now serves is settled, and one it
     * contradicts still wins: the athlete's own write is newer.
     */
    fun applyTo(view: ProfileView): ProfileView {
        val at = entries.value
        val block = at.blocks[view.did]
        val mute = at.mutes[view.did]
        if (block == null && mute == null) return view
        val served = view.viewer ?: ProfileViewerState()
        if (block != null && (served.blocking != null) == block.on) settle(view.did, blocks = true)
        if (mute != null && (served.muting != null) == mute.on) settle(view.did, blocks = false)
        return view.copy(
            viewer =
                served.copy(
                    blocking = if (block == null) served.blocking else block.record,
                    muting = if (mute == null) served.muting else mute.record,
                )
        )
    }

    /** Accounts with an unsettled block or mute change, which [FeedRepository.activity] asks the AppView about. */
    fun unsettled(): Set<Did> =
        entries.value.let { at -> (at.blocks.filterValues { !it.settled }.keys + at.mutes.filterValues { !it.settled }.keys) }

    /** Notes the comments the AppView listed on [activity], for [commentDelta]. */
    fun report(activity: AtUri, comments: List<CommentView>) {
        if (comments.isEmpty()) return
        entries.update { at ->
            val listed = at.listed[activity].orEmpty() + comments.associate { it.uri to Listed(it.author.did, it.hidden == true) }
            at.copy(listed = at.listed + (activity to listed))
        }
    }

    /**
     * How far [activity]'s comment count is moved by blocks and mutes the AppView hasn't caught up with: down by each comment it listed,
     * and counted, by an account newly blocked or muted. [hiddenHere] says whether the athlete hid a comment this session, already counted
     * out by [LocalComments].
     */
    fun commentDelta(activity: AtUri, hiddenHere: (AtUri, Boolean) -> Boolean): Int {
        val at = entries.value
        val pending = { did: Did -> listOfNotNull(at.blocks[did], at.mutes[did]).any { it.on && !it.settled } }
        return -at.listed[activity].orEmpty().count { (uri, listed) -> pending(listed.author) && !hiddenHere(uri, listed.hidden) }
    }

    /** Settles every change for [did]: the AppView answers them as unknown, so it leaves them out whatever the athlete changed. */
    fun settleAll(did: Did) {
        settle(did, blocks = true)
        settle(did, blocks = false)
    }

    private fun settle(did: Did, blocks: Boolean) = entries.update { at ->
        if (blocks) at.copy(blocks = at.blocks.mapValues { (k, v) -> if (k == did) v.copy(settled = true) else v })
        else at.copy(mutes = at.mutes.mapValues { (k, v) -> if (k == did) v.copy(settled = true) else v })
    }
}
