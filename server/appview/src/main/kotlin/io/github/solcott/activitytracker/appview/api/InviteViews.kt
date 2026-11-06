package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityInvitePairs
import io.github.solcott.activitytracker.appview.db.ActivityInvites
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.inviteLive
import io.github.solcott.activitytracker.appview.segments.exactRecord
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityInviteRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.GetActivityInvite
import io.github.solcott.activitytracker.lexicon.app.GetActivityInvitees
import io.github.solcott.activitytracker.lexicon.app.GetActivityInvites
import io.github.solcott.activitytracker.lexicon.app.InviteStatus
import io.github.solcott.activitytracker.lexicon.app.InviteView
import io.github.solcott.activitytracker.lexicon.app.InviteeView
import io.github.solcott.activitytracker.lexicon.app.Paging
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * Serves activity invites (feed/activity-invites): an invitee's pending invites, one invite with what it lets them copy, and an athlete's
 * invitees on their own activity. An invite counts only while [inviteLive] holds, and is served only to its inviter and its invitee. What
 * an invite grants is read here and nowhere else, so `getActivity` and `getActivityStreams` keep serving by tier alone.
 */
class InviteViews(private val db: Database, private val avatars: AvatarUrls, private val streams: ActivityStreamsViews) {
    /**
     * [viewer]'s invites that count and that they haven't accepted, newest first. Paged by offset, since an athlete's invites are few;
     * whether each counts ([inviteLive]) is checked only up to the page and one more, to tell whether there's another.
     */
    suspend fun invites(viewer: Did, params: GetActivityInvites.Params): GetActivityInvites.Output = db.tx {
        val pageSize = Paging.clamp(params.limit)
        val offset = params.cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val unaccepted =
            ActivityInvites.selectAll()
                .where { (ActivityInvites.inviteeDid eq viewer.value) and notExists(pairOf()) }
                .orderBy(ActivityInvites.createdAt to SortOrder.DESC, ActivityInvites.rkey to SortOrder.ASC)
                .toList()
        val pending = unaccepted.asSequence().filter { inviteLive(it) }.take(offset + pageSize + 1).toList()
        val page = pending.drop(offset).take(pageSize)
        val inviters = basicProfiles(page.map { it[ActivityInvites.inviterDid] }, avatars)
        GetActivityInvites.Output(
            invites = page.mapNotNull { inviteView(it, inviters) },
            cursor = (offset + pageSize).takeIf { it < pending.size }?.toString(),
        )
    }

    /** The pair of the outer query's invite, if its copy is paired. */
    private fun pairOf() =
        ActivityInvitePairs.select(ActivityInvitePairs.copyUri).where {
            (ActivityInvitePairs.inviterDid eq ActivityInvites.inviterDid) and (ActivityInvitePairs.inviteRkey eq ActivityInvites.rkey)
        }

    /**
     * The invite at [GetActivityInvite.Params.uri] with what it lets [viewer] copy: the activity's core fields and its Start time, Map and
     * Pace/Speed ones from every tier ([granted]), and their charts. `NotFound` for anyone but its invitee, and once it no longer counts or
     * was accepted.
     */
    suspend fun invite(viewer: Did, params: GetActivityInvite.Params): GetActivityInvite.Output {
        val invite =
            db.tx {
                val row = inviteRow(params.uri)?.takeIf { it[ActivityInvites.inviteeDid] == viewer.value && inviteLive(it) && !joined(it) }
                row?.let { inviteView(it, basicProfiles(listOf(it[ActivityInvites.inviterDid]), avatars)) }
            } ?: throw notFound()
        val data = streams.granted(invite.activity, GRANTED)
        return GetActivityInvite.Output(invite = invite, channels = data?.channels, data = data)
    }

    /**
     * The followers [viewer] added to their own activity, oldest first: each Joined once their copy is paired, else Pending while their
     * invite counts. Empty for anyone else's activity.
     */
    suspend fun invitees(viewer: Did, params: GetActivityInvitees.Params): GetActivityInvitees.Output = db.tx {
        if (params.activity.authority != viewer.value) return@tx GetActivityInvitees.Output(emptyList())
        val rows =
            ActivityInvites.selectAll()
                .where { (ActivityInvites.inviterDid eq viewer.value) and (ActivityInvites.activityUri eq params.activity.value) }
                .sortedWith(compareBy<ResultRow> { it[ActivityInvites.createdAt] }.thenBy { it[ActivityInvites.rkey] })
                .mapNotNull { row ->
                    val copy = copyOf(row)
                    when {
                        copy != null -> row to copy
                        inviteLive(row) -> row to null
                        else -> null
                    }
                }
                .take(ActivityInviteRecord.MAX)
        val profiles = basicProfiles(rows.map { it.first[ActivityInvites.inviteeDid] }, avatars)
        GetActivityInvitees.Output(
            rows.mapNotNull { (row, copy) ->
                val invitee = profiles[row[ActivityInvites.inviteeDid]] ?: return@mapNotNull null
                InviteeView(
                    uri = uriOf(row),
                    invitee = invitee,
                    status = if (copy != null) InviteStatus.JOINED else InviteStatus.PENDING,
                    createdAt = Instant.fromEpochMilliseconds(row[ActivityInvites.createdAt]),
                    copy = copy?.let(::AtUri),
                )
            }
        )
    }

    @Suppress("ReturnCount") // Not an invite URI, no rkey, or not indexed.
    private fun JdbcTransaction.inviteRow(uri: AtUri): ResultRow? {
        if (uri.collection != Nsid.FEED_ACTIVITY_INVITE) return null
        val rkey = uri.rkey ?: return null
        return ActivityInvites.selectAll()
            .where { (ActivityInvites.inviterDid eq uri.authority) and (ActivityInvites.rkey eq rkey) }
            .firstOrNull()
    }

    /** The invitee's copy paired for [invite], if any. */
    private fun JdbcTransaction.copyOf(invite: ResultRow): String? =
        ActivityInvitePairs.select(ActivityInvitePairs.copyUri)
            .where {
                (ActivityInvitePairs.inviterDid eq invite[ActivityInvites.inviterDid]) and
                    (ActivityInvitePairs.inviteRkey eq invite[ActivityInvites.rkey])
            }
            .firstOrNull()
            ?.get(ActivityInvitePairs.copyUri)

    private fun JdbcTransaction.joined(invite: ResultRow): Boolean = copyOf(invite) != null

    /** Null when the inviter or the activity isn't indexed, or the activity isn't served to the invitee ([served]: a takedown). */
    @Suppress("ReturnCount") // The inviter or the activity isn't indexed or served.
    private fun JdbcTransaction.inviteView(invite: ResultRow, inviters: Map<String, ProfileViewBasic>): InviteView? {
        val inviter = inviters[invite[ActivityInvites.inviterDid]] ?: return null
        val activity = invite[ActivityInvites.activityUri]
        val invitee = Did(invite[ActivityInvites.inviteeDid])
        val row =
            Activities.selectAll()
                .where { (Activities.uri eq activity) and served(invitee, Activities.authorDid, Activities.uri, Activities.cid) }
                .firstOrNull() ?: return null
        return InviteView(
            uri = uriOf(invite),
            activity = AtUri(activity),
            inviter = inviter,
            record = granted(row),
            createdAt = Instant.fromEpochMilliseconds(invite[ActivityInvites.createdAt]),
        )
    }

    /**
     * The activity's record with every tier's details laid over it, narrowest winning, holding only what an invite grants: its core figures
     * and its Start time, Map and Pace/Speed fields. Nothing personal to the inviter (heart rate, power, calories, cadence, temperature,
     * dive data, gear, devices, photos, description) is kept.
     */
    private fun JdbcTransaction.granted(activity: ResultRow): ActivityRecord {
        val full = exactRecord(activity)
        return ActivityRecord(
            sport = full.sport,
            subSport = full.subSport,
            startedAt = full.startedAt,
            utcOffset = full.utcOffset,
            elapsedTime = full.elapsedTime,
            movingTime = full.movingTime,
            distance = full.distance,
            elevationGain = full.elevationGain,
            elevationLoss = full.elevationLoss,
            avgSpeed = full.avgSpeed,
            maxSpeed = full.maxSpeed,
            polyline = full.polyline,
            polylineGaps = full.polylineGaps,
            title = full.title,
            createdAt = full.createdAt,
        )
    }

    private fun uriOf(invite: ResultRow) =
        AtUri.of(Did(invite[ActivityInvites.inviterDid]), Nsid.FEED_ACTIVITY_INVITE, invite[ActivityInvites.rkey])

    private fun notFound() = notFound("No such invite")

    companion object {
        /** The categories an invite lets its invitee copy, besides core data. */
        val GRANTED = setOf(DetailCategory.START_TIME, DetailCategory.MAP, DetailCategory.SPEED)
    }
}
