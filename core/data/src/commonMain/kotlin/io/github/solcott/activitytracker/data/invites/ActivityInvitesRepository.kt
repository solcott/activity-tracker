package io.github.solcott.activitytracker.data.invites

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.data.activity.requireSpacesFor
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.outcomeFlow
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.ActivityInviteDeclineRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityInviteRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetActivityInvite
import io.github.solcott.activitytracker.lexicon.app.GetActivityInvitees
import io.github.solcott.activitytracker.lexicon.app.GetActivityInvites
import io.github.solcott.activitytracker.lexicon.app.GetFollowers
import io.github.solcott.activitytracker.lexicon.app.InviteStatus
import io.github.solcott.activitytracker.lexicon.app.InviteView
import io.github.solcott.activitytracker.lexicon.app.InviteeView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.LocationSample
import io.github.solcott.activitytracker.location.TrackMath
import io.github.solcott.dataresult.Outcome
import io.github.solcott.dataresult.mapData
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject

/**
 * Activity invites (feed/activity-invites): followers the signed-in athlete adds to their own activities, and invites naming them. An
 * invite is an `feed.activityInvite` in the inviter's `self` space, keyed by the activity and the invitee, so adding is an idempotent put
 * and cancelling a delete. A decline is an `feed.activityInviteDecline` in the invitee's own `self` space, which the AppView never stores,
 * so [incoming] splits declines off itself. Adds, cancels and declines are direct calls: an invite that silently went out later would be
 * the wrong failure.
 */
@Suppress("TooManyFunctions") // Both sides of an invite: the owner's adds and cancels, the invitee's inbox, accept and decline.
@SingleIn(SessionScope::class)
@Inject
class ActivityInvitesRepository(
    private val clients: SessionClients,
    private val activities: ActivityRepository,
    private val feed: FeedRepository,
    private val clock: Clock,
    logger: Logger,
) {
    private val logger = logger.withTag("ActivityInvitesRepository")

    // Invites answered this session, laid over every read of the inbox: the AppView lists an accepted one until it indexes the copy.
    private val answered = MutableStateFlow(Answered())

    /** Whether this session can add people: not one signed in before its `space:` grant listed invites. */
    val canInvite: Boolean
        get() = clients.canWriteToSpaces(Nsid.FEED_ACTIVITY_INVITE)

    /** Whether this session can decline: not one signed in before its `space:` grant listed declines. */
    val canDecline: Boolean
        get() = clients.canWriteToSpaces(Nsid.FEED_ACTIVITY_INVITE_DECLINE)

    /** The followers the athlete added to their own [activity], oldest first, each Pending or Joined. */
    suspend fun invitees(activity: AtUri): List<InviteeView> =
        clients.appView.query(GetActivityInvitees.method, GetActivityInvitees.Params(activity)).invitees

    /**
     * Adds [invitee] to the athlete's own [activity] and returns them as the owner's list shows them, pending. Throws when the PDS can't be
     * reached, leaving them uninvited.
     */
    suspend fun add(activity: AtUri, invitee: ProfileViewBasic): InviteeView {
        val rkey = ActivityInviteRecord.rkey(requireNotNull(activity.rkey) { "an activity URI without an rkey" }, invitee.did)
        clients.requireSpacesFor(Visibility.SELF, Nsid.FEED_ACTIVITY_INVITE)
        val record = ActivityInviteRecord(subject = activity, invitee = invitee.did, createdAt = clock.now())
        clients.spaces.putRecord(
            clients.tiers.ensure(AthleteSpace.SELF),
            Nsid.FEED_ACTIVITY_INVITE,
            rkey,
            AtJson.encodeToJsonElement(ActivityInviteRecord.serializer(), record),
        )
        return InviteeView(AtUri.of(clients.did, Nsid.FEED_ACTIVITY_INVITE, rkey), invitee, InviteStatus.PENDING, record.createdAt)
    }

    /**
     * Who the athlete added to [activity], and their followers, who are the only athletes they may add: up to [MAX_PAGES] pages of them,
     * since the AppView lists followers newest first.
     */
    suspend fun people(activity: AtUri): People {
        val followers = mutableListOf<ProfileViewBasic>()
        var cursor: String? = null
        var pages = 0
        do {
            val page = clients.appView.query(GetFollowers.method, GetFollowers.Params(clients.did.value, PAGE_SIZE, cursor))
            followers += page.followers
            cursor = page.cursor?.takeIf { page.followers.isNotEmpty() }
        } while (cursor != null && ++pages < MAX_PAGES)
        return People(invitees(activity), followers.distinctBy { it.did })
    }

    fun peopleOutcome(activity: AtUri): Flow<Outcome<People>> = outcomeFlow { people(activity) }

    /** Deletes the athlete's invite at [invite]: a pending one is cancelled, a joined one removes the invitee from the group. */
    suspend fun cancel(invite: AtUri) {
        val rkey = requireNotNull(invite.rkey) { "an invite URI without an rkey" }
        clients.spaces.deleteRecord(clients.tiers.uri(AthleteSpace.SELF), Nsid.FEED_ACTIVITY_INVITE, rkey)
    }

    /**
     * The invites naming the athlete that they haven't accepted, split into [Incoming.pending] and [Incoming.declined], with this session's
     * answers laid over. Reads up to [MAX_PAGES] pages, since the AppView can't leave declines out.
     */
    suspend fun incoming(): Incoming = answered.value.applyTo(read())

    /** [incoming], updated as the athlete answers invites on any screen. */
    fun incomingOutcome(): Flow<Outcome<Incoming>> =
        combine(outcomeFlow { read() }, answered) { outcome, answers -> outcome.mapData { answers.applyTo(it) } }

    private suspend fun read(): Incoming {
        val declines = if (canDecline) declines() else null
        val all = mutableListOf<InviteView>()
        var cursor: String? = null
        var pages = 0
        do {
            val page = clients.appView.query(GetActivityInvites.method, GetActivityInvites.Params(PAGE_SIZE, cursor))
            all += page.invites
            cursor = page.cursor?.takeIf { page.invites.isNotEmpty() }
        } while (cursor != null && ++pages < MAX_PAGES)
        val (declined, pending) = all.distinctBy { it.uri }.partition { it.uri in declines.orEmpty() }
        return Incoming(pending, declined, needsSignIn = declines == null)
    }

    /** One invite naming the athlete, with what it lets them copy. */
    suspend fun invite(uri: AtUri): GetActivityInvite.Output =
        clients.appView.query(GetActivityInvite.method, GetActivityInvite.Params(uri))

    fun inviteOutcome(uri: AtUri): Flow<Outcome<GetActivityInvite.Output>> = outcomeFlow { invite(uri) }

    /**
     * Accepts [invite]: publishes the athlete's own copy of what it lets them copy ([copyOf]) at [choice], titled [title], naming the
     * original in `copyOf`, through the outbox like any new activity, and forgets any decline of it. Returns the copy's URI.
     */
    suspend fun accept(invite: GetActivityInvite.Output, choice: VisibilityChoice, title: String?): AtUri {
        val uri = activities.publish(copyOf(invite, title).withVisibility(choice))
        answered.update { it.copy(accepted = it.accepted + invite.invite.uri, declined = it.declined - invite.invite.uri) }
        if (canDecline) {
            runSuspendCatching { undecline(invite.invite.uri) }.onFailure { e -> logger.w(e) { "Couldn't clear a decline" } }
        }
        return uri
    }

    /**
     * Whether the athlete already has an activity whose time overlaps [record]'s by at least half of the shorter one, as import checks for
     * duplicates: they may have recorded part of it themselves.
     */
    suspend fun overlaps(record: ActivityRecord): Boolean {
        val start = record.startedAt
        val end = start + record.elapsedTime.seconds
        return feed.ownActivitiesStartedBetween(start - OVERLAP_LOOKBACK, end).any { own ->
            val ownStart = own.record.startedAt
            val ownEnd = ownStart + own.record.elapsedTime.seconds
            val overlap = minOf(end, ownEnd) - maxOf(start, ownStart)
            overlap.isPositive() && overlap >= minOf(end - start, ownEnd - ownStart) / 2
        }
    }

    /** Declines [invite], privately: the decline lives only in the athlete's `self` space. */
    suspend fun decline(invite: AtUri) {
        clients.requireSpacesFor(Visibility.SELF, Nsid.FEED_ACTIVITY_INVITE_DECLINE)
        val record = ActivityInviteDeclineRecord(subject = invite, createdAt = clock.now())
        clients.spaces.putRecord(
            clients.tiers.ensure(AthleteSpace.SELF),
            Nsid.FEED_ACTIVITY_INVITE_DECLINE,
            Tid.next(clock),
            AtJson.encodeToJsonElement(ActivityInviteDeclineRecord.serializer(), record),
        )
        answered.update { it.copy(declined = it.declined + invite) }
    }

    /** Forgets every decline of [invite], as accepting it from Declined does. */
    suspend fun undecline(invite: AtUri) {
        val self = clients.tiers.uri(AthleteSpace.SELF)
        val read = clients.spaces.listAllRecords(self, clients.did, Nsid.FEED_ACTIVITY_INVITE_DECLINE, PAGE_SIZE)
        if (read !is SpaceRead.Found) return
        read.value
            .filter { declineSubject(it.value) == invite }
            .forEach { clients.spaces.deleteRecord(self, Nsid.FEED_ACTIVITY_INVITE_DECLINE, it.rkey) }
    }

    /** The invites the athlete declined; empty when their `self` space doesn't exist yet. */
    private suspend fun declines(): Set<AtUri> =
        when (
            val read =
                clients.spaces.listAllRecords(
                    clients.tiers.uri(AthleteSpace.SELF),
                    clients.did,
                    Nsid.FEED_ACTIVITY_INVITE_DECLINE,
                    PAGE_SIZE,
                )
        ) {
            is SpaceRead.Found -> read.value.mapNotNull { declineSubject(it.value) }.toSet()
            SpaceRead.NotFound,
            SpaceRead.NoAccess -> emptySet()
        }

    private fun declineSubject(json: JsonObject?): AtUri? = json?.let {
        runCatching { AtJson.decodeFromJsonElement(ActivityInviteDeclineRecord.serializer(), it).subject }.getOrNull()
    }

    private companion object {
        const val MAX_PAGES = 5
        const val PAGE_SIZE = 100

        /** How long before an invite's activity another activity may have started and still overlap it. */
        val OVERLAP_LOOKBACK = 1.days
    }
}

/**
 * The athlete's own copy of what [invite] lets them copy (feed/activity-invites): the inviter's sport, start, times, distance, elevation,
 * speed and route, and their core and speed charts without laps, legs, lengths or sets, titled [title]. Its route is the inviter's trimmed
 * polyline, which publishing trims again by the athlete's own privacy zones; it has no track, so no segment track either.
 */
fun copyOf(invite: GetActivityInvite.Output, title: String?): ActivityDraft {
    val record = invite.invite.record
    val route =
        record.polyline?.let(TrackMath::decodePolylineOrNull).orEmpty().map { (lat, lon) ->
            LocationSample(lat, lon, altitude = null, speed = null, accuracy = null, time = record.startedAt)
        }
    return ActivityDraft(
        type = ActivityType(record.sport, record.subSport),
        title = title?.trim()?.ifEmpty { null },
        description = null,
        startedAt = record.startedAt,
        elapsed = record.elapsedTime.seconds,
        movingSeconds = record.movingTime,
        distanceMeters = record.distance?.toDouble(),
        elevationGainMeters = record.elevationGain?.toDouble(),
        elevationLossMeters = record.elevationLoss?.toDouble(),
        maxSpeedMps = record.maxSpeed?.let { it / MM_PER_M },
        utcOffsetMinutes = record.utcOffset,
        route = route,
        streams = invite.data?.copy(laps = null, legs = null, lengths = null, sets = null)?.takeIf { it.channels.isNotEmpty() },
        copyOf = invite.invite.activity,
    )
}

private const val MM_PER_M = 1000.0

/** Who the athlete added to an activity ([invitees]), and their [followers], whom they may add. */
data class People(val invitees: List<InviteeView>, val followers: List<ProfileViewBasic>)

/**
 * Invites naming the signed-in athlete that they haven't accepted. [needsSignIn]: the session predates the decline collection, so declines
 * couldn't be read and everything shows as [pending].
 */
data class Incoming(val pending: List<InviteView>, val declined: List<InviteView>, val needsSignIn: Boolean = false)

/** Invites the athlete [accepted] or [declined] this session, which the AppView or their `self` space may not show yet. */
private data class Answered(val accepted: Set<AtUri> = emptySet(), val declined: Set<AtUri> = emptySet()) {
    fun applyTo(incoming: Incoming): Incoming {
        val declinedNow = declined + incoming.declined.map { it.uri }
        val (nowDeclinedInvites, pending) =
            (incoming.pending + incoming.declined).filterNot { it.uri in accepted }.partition { it.uri in declinedNow }
        return incoming.copy(pending = pending, declined = nowDeclinedInvites)
    }
}
