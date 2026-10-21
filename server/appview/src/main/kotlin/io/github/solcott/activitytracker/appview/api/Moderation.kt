package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.Labels
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.LabelView
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.stringParam
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select

/**
 * The labelers whose stored labels apply (`moderation/labels`), set once at startup from the config. Empty, as with no labeler configured
 * or the subscriber turned off, no stored label applies.
 */
internal object LabelSources {
    @Volatile var trusted: List<String> = emptyList()
}

/** Label values with an effect in the AppView. Every other value is the clients' to show, or ignored. */
internal object LabelValues {
    const val TAKEDOWN = "!takedown"
    const val HIDE = "!hide"
}

/**
 * A trusted, unexpired label [value] on [subject] (a record's AT-URI or an account's DID), correlated to the outer query when [subject] is
 * a column. A label naming a CID applies only while [cid] is that version; one without applies to any.
 */
internal fun labelled(subject: Expression<String>, value: String, cid: Column<String>? = null): Op<Boolean> {
    val sources = LabelSources.trusted
    if (sources.isEmpty()) return Op.FALSE
    val now = System.currentTimeMillis()
    return exists(
        Labels.select(Labels.src).where {
            (Labels.uri eq subject) and
                (Labels.value eq value) and
                (Labels.src inList sources) and
                (Labels.exp.isNull() or (Labels.exp greater now)) and
                (cid?.let { Labels.cid.isNull() or (Labels.cid eq it) } ?: Op.TRUE)
        }
    )
}

/**
 * Whether the account [author] is served: its hosting status is active (`actors.status` null) and it carries no `!takedown`. Correlated to
 * the outer query's author column.
 */
internal fun accountServed(author: Expression<String>): Op<Boolean> =
    not(exists(Actors.select(Actors.did).where { (Actors.did eq author) and Actors.status.isNotNull() })) and
        not(labelled(author, LabelValues.TAKEDOWN))

/**
 * Rows by [author] (and, when given, the record [record] at [cid]) that may be served to [viewer]: their author always sees their own;
 * anyone else, signed out included, only while the account is served ([accountServed]) and the record carries no `!takedown`. Sits in both
 * branches of [visibleTo], so every lookup, list and count follows it.
 */
internal fun served(viewer: Expression<String>?, author: Column<String>, record: Column<String>?, cid: Column<String>?): Op<Boolean> {
    val recordServed = record?.let { not(labelled(it, LabelValues.TAKEDOWN, cid)) } ?: Op.TRUE
    val served = accountServed(author) and recordServed
    return if (viewer == null) served else (author eq viewer) or served
}

/** [served] for a known [viewer]. */
internal fun served(viewer: Did?, author: Column<String>, record: Column<String>? = null, cid: Column<String>? = null): Op<Boolean> =
    served(viewer?.let { stringParam(it.value) }, author, record, cid)

/**
 * Rows a list may show [viewer]: not carrying `!hide` on the record [record] or on its [author]'s account, unless it's the viewer's own.
 * Lists and counts use it next to `notMuted` / `notBlocked`; direct lookups don't, so a hidden subject stays reachable by URI.
 */
internal fun unhidden(viewer: Did?, author: Column<String>, record: Column<String>? = null): Op<Boolean> {
    val shown = not(labelled(author, LabelValues.HIDE)) and (record?.let { not(labelled(it, LabelValues.HIDE)) } ?: Op.TRUE)
    return if (viewer == null) shown else (author eq viewer.value) or shown
}

/** An account a list may show [viewer]: [served] and [unhidden], for lists of accounts (likers, followers, search). */
internal fun listed(viewer: Did?, account: Column<String>): Op<Boolean> = served(viewer, account) and unhidden(viewer, account)

/** Whether [did]'s account is served to [viewer]: [accountServed], or the viewer is the account. */
internal fun JdbcTransaction.isAccountServed(did: String, viewer: Did?): Boolean =
    viewer?.value == did ||
        (Actors.select(Actors.status).where { Actors.did eq did }.firstOrNull()?.get(Actors.status) == null &&
            !labelledOnly(did, LabelValues.TAKEDOWN))

/** Whether [subject] carries a trusted, unexpired [value], outside a query. */
internal fun JdbcTransaction.labelledOnly(subject: String, value: String): Boolean =
    LabelSources.trusted.isNotEmpty() &&
        Labels.select(Labels.src)
            .where {
                (Labels.uri eq subject) and
                    (Labels.value eq value) and
                    (Labels.src inList LabelSources.trusted) and
                    (Labels.exp.isNull() or (Labels.exp greater System.currentTimeMillis()))
            }
            .limit(1)
            .any()

/** Whether [did]'s profile record carries `!takedown`. */
internal fun JdbcTransaction.isProfileTakenDown(did: String): Boolean =
    labelledOnly(AtUri.of(Did(did), Nsid.ACTOR_PROFILE, "self").value, LabelValues.TAKEDOWN)

/**
 * The labels views carry for each of [subjects] (a record's AT-URI with its current CID, or an account's DID with none), in one query: from
 * trusted labelers, unexpired, and for a record only those naming no CID or its current one. Subjects without labels are absent.
 */
internal fun JdbcTransaction.labelViews(subjects: Map<String, String?>): Map<String, List<LabelView>> {
    if (subjects.isEmpty() || LabelSources.trusted.isEmpty()) return emptyMap()
    val now = System.currentTimeMillis()
    return Labels.select(Labels.src, Labels.uri, Labels.value, Labels.cid)
        .where {
            (Labels.uri inList subjects.keys) and
                (Labels.src inList LabelSources.trusted) and
                (Labels.exp.isNull() or (Labels.exp greater now))
        }
        .filter { row -> row[Labels.cid].let { it == null || it == subjects[row[Labels.uri]] } }
        .groupBy({ it[Labels.uri] }, { LabelView(Did(it[Labels.src]), it[Labels.value]) })
        .mapValues { (_, labels) -> labels.sortedWith(compareBy({ it.src.value }, { it.value })) }
}

/** The labels on [did]'s account and on its profile record, for its profile view; null when there are none. */
internal fun JdbcTransaction.profileLabels(did: String): List<LabelView>? {
    val profile = AtUri.of(Did(did), Nsid.ACTOR_PROFILE, "self").value
    // A profile record label pinned to a CID is checked against none: the AppView doesn't keep the profile's CID.
    return labelViews(mapOf(did to null, profile to null)).values.flatten().ifEmpty { null }
}
