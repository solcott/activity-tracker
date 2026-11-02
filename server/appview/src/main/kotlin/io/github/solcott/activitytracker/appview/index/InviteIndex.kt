package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.api.follows
import io.github.solcott.activitytracker.appview.api.isBlocked
import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityInvitePairs
import io.github.solcott.activitytracker.appview.db.ActivityInvites
import io.github.solcott.activitytracker.appview.segments.pairVisibility
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityInviteRecord
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

// Indexing for activity invites (feed/activity-invites): invites from the inviter's `self` space, and the pair of an invite's activity
// with the invitee's first copy of it (feed.activity#copyOf).

/**
 * [author]'s invite at [uri], from their `self` space: kept only for their own activity, under the rkey [ActivityInviteRecord.rkey] gives
 * its activity and invitee, and never naming themselves. Whether it counts (a follow, no block, among the first [ActivityInviteRecord.MAX])
 * is checked when it's read ([inviteLive]), so a follow coming back revives it.
 */
internal fun JdbcTransaction.upsertInvite(record: ActivityInviteRecord, author: Did, uri: AtUri, now: Long) {
    val subject = record.subject
    val rkey = requireNotNull(uri.rkey) { "an invite URI without an rkey" }
    require(subject.authority == author.value && subject.collection == Nsid.FEED_ACTIVITY) { "invite to another's activity" }
    require(rkey == ActivityInviteRecord.rkey(requireNotNull(subject.rkey) { "invite to no activity" }, record.invitee)) {
        "invite under another rkey"
    }
    require(record.invitee != author) { "invite to oneself" }
    require(rkey.length <= MAX_URI_LENGTH && subject.value.length <= MAX_URI_LENGTH && record.invitee.value.length <= MAX_DID_LENGTH) {
        "invite too long"
    }
    // The rkey names the activity and invitee, so a rewrite can only change when it was made.
    if (ActivityInvites.selectAll().where { (ActivityInvites.inviterDid eq author.value) and (ActivityInvites.rkey eq rkey) }.empty()) {
        ActivityInvites.insert {
            it[inviterDid] = author.value
            it[ActivityInvites.rkey] = rkey
            it[activityUri] = subject.value
            it[inviteeDid] = record.invitee.value
            it[createdAt] = record.createdAt.toEpochMilliseconds()
            it[indexedAt] = now
        }
    } else {
        ActivityInvites.update({ (ActivityInvites.inviterDid eq author.value) and (ActivityInvites.rkey eq rkey) }) {
            it[createdAt] = record.createdAt.toEpochMilliseconds()
        }
    }
}

/** [uri]'s invite deleted, and with it its pair: the invitee's copy stays an activity of its own. */
internal fun JdbcTransaction.deleteInvite(uri: AtUri) {
    val rkey = uri.rkey ?: return
    ActivityInvites.deleteWhere { (inviterDid eq uri.authority) and (ActivityInvites.rkey eq rkey) }
    ActivityInvitePairs.deleteWhere { (inviterDid eq uri.authority) and (inviteRkey eq rkey) }
}

/**
 * Every invite [author] made and its pairs, as deleting their `self` space (which holds the invites) calls for. Pairs where they're the
 * invitee stand on another athlete's invite, so they stay.
 */
internal fun JdbcTransaction.dropInvitesOf(author: Did) {
    ActivityInvites.deleteWhere { inviterDid eq author.value }
    ActivityInvitePairs.deleteWhere { inviterDid eq author.value }
}

/** Everything about invites [author] is in, either side, as deleting their account calls for. */
internal fun JdbcTransaction.dropInvitesOfAccount(author: Did) {
    ActivityInvites.deleteWhere { (inviterDid eq author.value) or (inviteeDid eq author.value) }
    ActivityInvitePairs.deleteWhere { (inviterDid eq author.value) or (inviteeDid eq author.value) }
}

/** Every invite pair between an activity of [a] and one of [b]'s, as a block between them calls for. */
internal fun JdbcTransaction.dropInvitePairsBetween(a: Did, b: Did) {
    ActivityInvitePairs.deleteWhere {
        ((inviterDid eq a.value) and (inviteeDid eq b.value)) or ((inviterDid eq b.value) and (inviteeDid eq a.value))
    }
}

/**
 * Whether [invite] counts now: its invitee effectively follows its inviter, neither blocks the other, its activity is indexed, and it's
 * among the first [ActivityInviteRecord.MAX] invites to that activity by when they were made.
 */
@Suppress("ReturnCount") // Each reason it doesn't count.
internal fun JdbcTransaction.inviteLive(invite: ResultRow): Boolean {
    val inviter = invite[ActivityInvites.inviterDid]
    val invitee = invite[ActivityInvites.inviteeDid]
    val activity = invite[ActivityInvites.activityUri]
    if (!follows(invitee, inviter) || isBlocked(inviter, invitee)) return false
    if (Activities.select(Activities.uri).where { Activities.uri eq activity }.empty()) return false
    val createdAt = invite[ActivityInvites.createdAt]
    val earlier =
        ActivityInvites.select(ActivityInvites.rkey)
            .where {
                (ActivityInvites.activityUri eq activity) and
                    ((ActivityInvites.createdAt less createdAt) or
                        ((ActivityInvites.createdAt eq createdAt) and (ActivityInvites.rkey less invite[ActivityInvites.rkey])))
            }
            .count()
    return earlier < ActivityInviteRecord.MAX
}

/**
 * After [copy] by [author] was indexed with [copyOf] (its untrusted `copyOf`, or null): a pair it was in with anything else goes; one it's
 * already in is re-tiered; and otherwise it's paired with the activity it names when an invite of [author] to that activity counts
 * ([inviteLive]) and no copy is paired for that invite yet, so only the first copy is.
 */
@Suppress("ReturnCount") // Each reason the copy pairs with nothing new.
internal fun JdbcTransaction.copyIndexed(copy: AtUri, author: Did, copyOf: String?, now: Long) {
    ActivityInvitePairs.deleteWhere { (copyUri eq copy.value) and (originalUri neq copyOf.orEmpty()) }
    val original = copyOf?.let(::AtUri) ?: return
    val originalRkey = original.rkey ?: return
    val copyRow = Activities.selectAll().where { Activities.uri eq copy.value }.firstOrNull() ?: return
    val originalRow = Activities.selectAll().where { Activities.uri eq original.value }.firstOrNull() ?: return
    val paired =
        !ActivityInvitePairs.select(ActivityInvitePairs.copyUri)
            .where { (ActivityInvitePairs.copyUri eq copy.value) and (ActivityInvitePairs.originalUri eq original.value) }
            .empty()
    if (paired) return retierInvitePairs(copyRow)
    val rkey = ActivityInviteRecord.rkey(originalRkey, author)
    val invite =
        ActivityInvites.selectAll()
            .where { (ActivityInvites.inviterDid eq original.authority) and (ActivityInvites.rkey eq rkey) }
            .firstOrNull() ?: return
    if (!inviteLive(invite)) return
    val taken =
        !ActivityInvitePairs.select(ActivityInvitePairs.copyUri)
            .where { (ActivityInvitePairs.inviterDid eq original.authority) and (ActivityInvitePairs.inviteRkey eq rkey) }
            .empty()
    if (taken) return
    // Ignored when a copy indexed at the same time, under another URI's lock, took the invite first.
    ActivityInvitePairs.insertIgnore {
        it[originalUri] = original.value
        it[copyUri] = copy.value
        it[inviterDid] = original.authority
        it[inviteeDid] = author.value
        it[inviteRkey] = rkey
        it[originalVisibility] = pairVisibility(originalRow)
        it[copyVisibility] = pairVisibility(copyRow)
        it[pairedAt] = now
    }
}

/** Sets [activity]'s side of each invite pair it's in to its current partner tier ([pairVisibility]). */
internal fun JdbcTransaction.retierInvitePairs(activity: ResultRow) {
    val uri = activity[Activities.uri]
    val inPair =
        !ActivityInvitePairs.select(ActivityInvitePairs.copyUri)
            .where { (ActivityInvitePairs.originalUri eq uri) or (ActivityInvitePairs.copyUri eq uri) }
            .empty()
    if (!inPair) return
    val visibility = pairVisibility(activity)
    ActivityInvitePairs.update({ ActivityInvitePairs.originalUri eq uri }) { it[originalVisibility] = visibility }
    ActivityInvitePairs.update({ ActivityInvitePairs.copyUri eq uri }) { it[copyVisibility] = visibility }
}

/** Every invite pair [activity] is in, once it's gone. */
internal fun JdbcTransaction.dropInvitePairsOf(activity: String) {
    ActivityInvitePairs.deleteWhere { (originalUri eq activity) or (copyUri eq activity) }
}

private const val MAX_DID_LENGTH = 255
