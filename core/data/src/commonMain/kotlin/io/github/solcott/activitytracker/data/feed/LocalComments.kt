package io.github.solcott.activitytracker.data.feed

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.GetComments
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.ThreadView
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * A comment the viewer sent this session that the AppView doesn't list yet. Not a [CommentView]: the client can't compute its CID, so it
 * can't be replied to until the AppView lists it. A reply's [root] is its thread's top-level comment.
 */
@Immutable
data class SentComment(val uri: AtUri, val author: ProfileViewBasic, val text: String, val createdAt: Instant, val root: AtUri? = null)

/**
 * Comments the viewer sent, deleted, hid or unhid this session, each held as what the viewer wants of it ([Want]) against what the AppView
 * last said of it ([Seen]), until the two agree. A delete is kept for the session even then, as a TID is never reused: a comment sent and
 * deleted before the AppView indexed it may still be indexed, and listed, before its delete is. The AppView serves comments as they were
 * until Jetstream or a space sync brings it each change, so its reads are laid over with these: [applyTo] its pages, [sent] after them, and
 * [delta] onto its count.
 *
 * Pages tell what the AppView lists ([report]), but only a lookup by URI ([lookup]) can tell that it no longer lists a comment, which a
 * page the viewer hasn't loaded might hold. [FeedRepository.activity] makes one for [pending] each time it loads an activity, so its count
 * is right without loading every page.
 */
class LocalComments {
    /** What the viewer wants of a comment. */
    sealed interface Want {
        data class Present(val hidden: Boolean) : Want

        data object Gone : Want
    }

    /** What the AppView last said of a comment, to this viewer. */
    sealed interface Seen {
        data class Listed(val hidden: Boolean) : Seen

        data object Absent : Seen
    }

    data class Entry(val activity: AtUri, val want: Want, val seen: Seen, val sent: SentComment? = null, val order: Long) {
        /** The AppView has caught up. */
        val settled: Boolean
            get() =
                when (want) {
                    Want.Gone -> seen == Seen.Absent
                    is Want.Present -> seen == Seen.Listed(want.hidden)
                }

        /** What the AppView says once it has caught up. */
        val caughtUp: Seen
            get() =
                when (want) {
                    Want.Gone -> Seen.Absent
                    is Want.Present -> Seen.Listed(want.hidden)
                }

        /** What this comment adds to the activity's count beyond what the AppView counts. */
        val delta: Int
            get() = counts(want) - counts(seen)

        private fun counts(want: Want) = if (want is Want.Present && !want.hidden) 1 else 0

        private fun counts(seen: Seen) = if (seen is Seen.Listed && !seen.hidden) 1 else 0
    }

    private var next = 0L

    /** Every unsettled change, and every delete, by comment URI; collected to show changes as they're made. */
    val entries: StateFlow<Map<AtUri, Entry>>
        field = MutableStateFlow(emptyMap())

    /** The viewer sent [comment] on [activity]: shown after the loaded comments, and counted, until the AppView lists it. */
    fun send(activity: AtUri, comment: SentComment) = entries.update {
        it.settle(listOf(comment.uri to Entry(activity, Want.Present(hidden = false), Seen.Absent, comment, next++)))
    }

    /**
     * The viewer wants [comment] on [activity] as [want]: [Want.Gone] when they deleted it, [Want.Present] when the athlete hid or unhid
     * it. It was listed as [listedHidden] when they did. Returns what was held before, for [restore]. A sent comment the AppView doesn't
     * list yet was never counted by it, so deleting it moves no count until a page lists it.
     */
    fun change(activity: AtUri, comment: AtUri, want: Want, listedHidden: Boolean): Entry? {
        var previous: Entry? = null
        entries.update { held ->
            previous = held[comment]
            // What the AppView last said: from a change already held (a sent comment it doesn't list yet), else the view it was listed in.
            val seen = previous?.seen ?: Seen.Listed(listedHidden)
            held.settle(listOf(comment to Entry(activity, want, seen, previous?.sent, next++)))
        }
        return previous
    }

    /** Puts back what [change] replaced, when the change couldn't be made. */
    fun restore(comment: AtUri, previous: Entry?) = entries.update { if (previous == null) it - comment else it + (comment to previous) }

    /** Whether [comment] is hidden, preferring what the viewer did over the (possibly stale) view. */
    fun isHidden(comment: AtUri, viewHidden: Boolean): Boolean = (entries.value[comment]?.want as? Want.Present)?.hidden ?: viewHidden

    /** What [activity]'s count gains or loses by the viewer's changes the AppView hasn't caught up with. */
    fun delta(activity: AtUri): Int = entries.value.delta(activity)

    /** The comments on [activity] whose state to look up, newest change first, at most [GetComments.MAX_URIS]. */
    fun pending(activity: AtUri): List<AtUri> =
        entries.value.entries
            .filter { it.value.activity == activity && !it.value.settled }
            .sortedByDescending { it.value.order }
            .take(GetComments.MAX_URIS)
            .map { it.key }

    /** The AppView listed [comments] (a page, or a lookup's answer) as shown, so each change there is caught up with or not. */
    fun report(comments: List<CommentView>) = entries.update { held ->
        held.settle(comments.mapNotNull { c -> held[c.uri]?.let { c.uri to it.copy(seen = Seen.Listed(c.hidden == true)) } })
    }

    /**
     * A lookup of [requested] on [activity] answered [returned]: what it left out the AppView doesn't list. Changes on [activity] too old
     * to have been asked about are taken as caught up, being older than the [GetComments.MAX_URIS] newest. Nothing changes until it's
     * [Lookup.report]ed, so a caller can first use the count it gives.
     */
    fun lookup(activity: AtUri, requested: Collection<AtUri>, returned: List<CommentView>): Lookup =
        Lookup(entries.value.afterLookup(activity, requested, returned).delta(activity)) {
            entries.update { it.afterLookup(activity, requested, returned) }
        }

    /**
     * A page of the AppView's [comments] as the viewer left them: without those deleted, hidden or unhidden as the viewer did. A screen
     * passes the [held] changes it last collected, so what it shows stays in step with what it will be told of next.
     */
    fun applyTo(comments: List<CommentView>, held: Map<AtUri, Entry> = entries.value): List<CommentView> = comments.mapNotNull { c ->
        when (val want = held[c.uri]?.want) {
            null -> c
            Want.Gone -> null
            is Want.Present -> if ((c.hidden == true) == want.hidden) c else c.copy(hidden = true.takeIf { want.hidden })
        }
    }

    /**
     * A [thread] as the viewer left it: [applyTo] over its top-level comment and replies, its reply count lowered by the replies the viewer
     * deleted. A top-level comment the viewer deleted leaves a placeholder, which the caller drops when nothing is left under it.
     */
    fun applyTo(thread: ThreadView, held: Map<AtUri, Entry> = entries.value): ThreadView {
        val replies = applyTo(thread.replies, held)
        return thread.copy(
            root = thread.root?.let { applyTo(listOf(it), held).singleOrNull() },
            replies = replies,
            replyCount = thread.replyCount - (thread.replies.size - replies.size),
        )
    }

    /** The comments the viewer sent on [activity] that the AppView doesn't list yet, oldest first. */
    fun sent(activity: AtUri): List<SentComment> =
        entries.value.values
            .filter { it.activity == activity && it.sent != null && it.want is Want.Present && it.seen == Seen.Absent }
            .sortedBy { it.order }
            .mapNotNull { it.sent }
}

/** A lookup's answer, not yet reported: [delta] is what [LocalComments.delta] will be once it's [report]ed. */
class Lookup internal constructor(val delta: Int, private val apply: () -> Unit) {
    fun report() = apply()
}

private typealias Held = Map<AtUri, LocalComments.Entry>

private fun Held.delta(activity: AtUri): Int = values.sumOf { if (it.activity == activity) it.delta else 0 }

/** [this] after a lookup of [requested] on [activity] answered [returned] ([LocalComments.lookup]). */
private fun Held.afterLookup(activity: AtUri, requested: Collection<AtUri>, returned: List<CommentView>): Held {
    val listed = returned.associateBy { it.uri }
    val asked = requested.toSet()
    val stale = filter { (uri, entry) -> entry.activity == activity && uri !in asked && !entry.settled }
    return settle(stale.map { (uri, entry) -> uri to entry.copy(seen = entry.caughtUp) }).let { held ->
        held.settle(
            asked.mapNotNull { uri ->
                val entry = held[uri] ?: return@mapNotNull null
                uri to entry.copy(seen = listed[uri]?.let { LocalComments.Seen.Listed(it.hidden == true) } ?: LocalComments.Seen.Absent)
            }
        )
    }
}

/** [this] with [changed] laid over it, dropping those the AppView has caught up with, except deletes. */
private fun Held.settle(changed: List<Pair<AtUri, LocalComments.Entry>>): Held {
    if (changed.isEmpty()) return this
    val (settled, kept) = changed.partition { it.second.settled && it.second.want != LocalComments.Want.Gone }
    return this - settled.map { it.first }.toSet() + kept
}
