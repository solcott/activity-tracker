package io.github.solcott.activitytracker.data.moderation

import androidx.compose.runtime.Immutable
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.xrpc.atprotoProxy
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.atproto.CreateReport
import io.github.solcott.activitytracker.lexicon.atproto.ReasonType
import io.github.solcott.activitytracker.lexicon.atproto.ReportSubject

/** What a report is about (`moderation/reports`). Its [author] is who the app offers to block afterwards. */
@Immutable
sealed interface ReportTarget {
    val author: Did

    /** An activity, by the URI and CID its view carries. */
    data class Activity(val uri: AtUri, val cid: Cid, override val author: Did) : ReportTarget

    /** One photo of an activity: labels can't name a photo, so the report names the activity and the note says which. */
    data class Photo(val activity: Activity, val position: Int) : ReportTarget {
        override val author: Did
            get() = activity.author
    }

    data class Comment(val uri: AtUri, val cid: Cid, override val author: Did) : ReportTarget

    data class Account(val did: Did) : ReportTarget {
        override val author: Did
            get() = did
    }
}

/** The report sheet's reasons, each sent as its `com.atproto.moderation.defs#reasonType`. */
enum class ReportReason(val reasonType: String) {
    SPAM(ReasonType.SPAM),
    SEXUAL(ReasonType.SEXUAL),
    VIOLENCE(ReasonType.VIOLATION),
    HARASSMENT(ReasonType.RUDE),
    MISLEADING(ReasonType.MISLEADING),
    OTHER(ReasonType.OTHER);

    /** "Other" says nothing on its own, so it needs a note. */
    val requiresNote: Boolean
        get() = this == OTHER
}

/**
 * Sends reports to the app's labeler: `createReport` to the athlete's own PDS with `atproto-proxy: <labelerDid>#atproto_labeler`, which
 * forwards it with service auth of its own. Nothing is stored locally.
 */
@SingleIn(SessionScope::class)
@Inject
class ReportRepository(private val clients: SessionClients) {
    /** Whether a labeler is configured; without one the app offers no Report. */
    val canReport: Boolean
        get() = clients.labelerDid != null

    /**
     * Reports [target] for [reason] with [note] (trimmed, at most [MAX_NOTE_LENGTH]). Refuses ([IllegalArgumentException]) a
     * [ReportReason.OTHER] without a note, the athlete's own content, and any report when no labeler is configured
     * ([IllegalStateException]).
     */
    suspend fun report(target: ReportTarget, reason: ReportReason, note: String = "") {
        val labeler = checkNotNull(clients.labelerDid) { "No labeler is configured" }
        val text = note.trim().take(MAX_NOTE_LENGTH)
        require(!reason.requiresNote || text.isNotEmpty()) { "A report for another reason needs a note" }
        require(target.author != clients.did) { "Athletes can't report themselves" }
        val input = CreateReport.Input(reason.reasonType, subjectOf(target), reasonText(target, text))
        clients.pds.procedure(CreateReport.method, input, atprotoProxy(labeler, LABELER_SERVICE))
    }

    companion object {
        /** The note's limit in the sheet; a photo's position is added in front, within the lexicon's 2000. */
        const val MAX_NOTE_LENGTH = 1900

        private const val LABELER_SERVICE = "atproto_labeler"

        internal fun subjectOf(target: ReportTarget): ReportSubject =
            when (target) {
                is ReportTarget.Activity -> ReportSubject.Record(target.uri, target.cid)
                is ReportTarget.Photo -> subjectOf(target.activity)
                is ReportTarget.Comment -> ReportSubject.Record(target.uri, target.cid)
                is ReportTarget.Account -> ReportSubject.Repo(target.did)
            }

        /** The note, led for a photo by which one it is (1-based, as the gallery counts). */
        internal fun reasonText(target: ReportTarget, note: String): String? =
            when (target) {
                is ReportTarget.Photo -> listOf("Photo ${target.position}", note).filter(String::isNotEmpty).joinToString("\n\n")
                else -> note.ifEmpty { null }
            }
    }
}
