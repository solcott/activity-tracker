package io.github.solcott.activitytracker.data.profile

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.ownBlobUrl
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.FollowRecord
import io.github.solcott.activitytracker.lexicon.app.GetFollowers
import io.github.solcott.activitytracker.lexicon.app.GetFollows
import io.github.solcott.activitytracker.lexicon.app.GetProfile
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.ProfileRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.DeleteRecord
import io.github.solcott.activitytracker.lexicon.atproto.GetRecord
import io.github.solcott.activitytracker.lexicon.atproto.PutRecord
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import kotlin.time.Clock

@SingleIn(SessionScope::class)
@Inject
class ProfileRepository(
    private val clients: SessionClients,
    internal val clock: Clock,
    logger: Logger,
) {
    private val logger = logger.withTag("ProfileRepository")

    val did
        get() = clients.did

    /** The tier spaces holding non-public details: the athlete's own, and others' Mutuals tiers. */
    private val tiers = AthleteSpaces(clients.spaces, clients::managingApp)

    /**
     * The signed-in athlete's last save, until the AppView shows it. [profile] lays it over the AppView's view of the athlete so an edit
     * shows at once. [Pending.details] is null after a save that didn't touch the details (a units change).
     */
    private class Pending(val record: ProfileRecord, val details: ProfileDetails?)

    private var pendingOwnProfile: Pending? = null

    private val pendingFollows = PendingFollows()

    /**
     * [actor]'s profile as the AppView shows it (public details only), with the non-public details this athlete may see laid under the
     * public ones: all of their own, and a followed athlete's Mutuals details when that athlete's PDS admits them as a mutual.
     */
    suspend fun profile(actor: String): ProfileView {
        val view = withHiddenDetails(pendingFollows.applyTo(clients.appView.query(GetProfile.method, GetProfile.Params(actor))))
        val pending = pendingOwnProfile
        if (view.did != did || pending == null) return view
        val avatarCid = pending.record.avatar?.ref?.link
        val shown =
            view.copy(
                displayName = pending.record.displayName,
                description = pending.record.description,
                details = if (pending.details == null) view.details else pending.details.takeUnless { it == ProfileDetails() },
                avatar =
                    if (avatarCid == view.avatar?.substringAfterLast('/')) view.avatar
                    else avatarCid?.let { ownBlobUrl(clients.pdsUrl, did, it) },
            )
        if (shown == view) pendingOwnProfile = null
        return shown
    }

    /**
     * [view] with the tier details this athlete may see. Only a follower can be a mutual, so nobody else's client asks for a credential. A
     * refusal, a missing record or a failed read all leave the public details, so a PDS that's down never breaks a profile.
     */
    private suspend fun withHiddenDetails(view: ProfileView): ProfileView {
        val readable =
            when {
                view.did == did -> AthleteSpaces.TIERS
                view.viewer?.following != null -> listOf(AthleteSpace.MUTUALS)
                else -> return view
            }
        val hidden =
            readable.fold(ProfileDetails()) { acc, tier ->
                runSuspendCatching { tiers.details(view.did, tier) }
                    .onFailure { e -> logger.w(e) { "Couldn't read the $tier details of ${view.did.value}" } }
                    .map { under(acc, it) }
                    .getOrDefault(acc)
            }
        return view.copy(details = under(view.details ?: ProfileDetails(), hidden).takeUnless { it == ProfileDetails() })
    }

    /** One page of athletes following [actor] (a DID or handle). */
    suspend fun followers(actor: String, cursor: String? = null): GetFollowers.Output =
        clients.appView.query(GetFollowers.method, GetFollowers.Params(actor, cursor = cursor))

    /** One page of athletes [actor] (a DID or handle) follows. */
    suspend fun follows(actor: String, cursor: String? = null): GetFollows.Output =
        clients.appView.query(GetFollows.method, GetFollows.Params(actor, cursor = cursor))

    /** The signed-in athlete's own profile record, read from their PDS (not the AppView, which may lag); null if they have none. */
    suspend fun ownProfileRecord(): ProfileRecord? {
        val output =
            try {
                clients.pds.query(GetRecord.method, GetRecord.Params(clients.did.value, Nsid.ACTOR_PROFILE, ProfileRecord.RKEY))
            } catch (e: XrpcException) {
                if (e.error == RECORD_NOT_FOUND) return null
                throw e
            }
        return AtJson.decodeFromJsonElement(ProfileRecord.serializer(), output.value)
    }

    /**
     * The signed-in athlete's profile for editing: the record from their PDS (the AppView may lag) and their non-public details from their
     * tier spaces. A session signed in before the `space:` scope can't read those, so it gets the public details and
     * [OwnProfile.needsSignIn].
     */
    suspend fun ownProfile(): OwnProfile {
        val record = ownProfileRecord()
        val hidden =
            try {
                tiers.own()
            } catch (e: XrpcException) {
                if (e.error != SpaceErrors.SCOPE_MISSING) throw e
                null
            }
        val (details, visibility) = ownDetails(record, hidden.orEmpty())
        val avatar = record?.avatar
        return OwnProfile(
            record?.displayName,
            record?.description,
            avatar,
            avatar?.let { ownBlobUrl(clients.pdsUrl, did, it.ref.link) },
            details,
            visibility,
            needsSignIn = hidden == null,
        )
    }

    /** Replaces the profile record whole. Read it first ([ownProfileRecord]) so fields this save doesn't change survive. */
    suspend fun saveProfile(record: ProfileRecord) {
        putProfileRecord(record)
        pendingOwnProfile = Pending(record, pendingOwnProfile?.details)
    }

    /**
     * Saves [edit]. Each detail goes in exactly one place by its visibility: the public record, or the tier space of that visibility. A
     * detail that moves is written to its new home before its old one is trimmed, so a failure part-way leaves it in both places, seen by
     * the wider of the audience it had and the one it's getting, rather than losing it.
     */
    suspend fun save(edit: ProfileEdit) {
        val existing = ownProfileRecord()
        val record = existing ?: ProfileRecord(createdAt = clock.now())
        val current = tiers.own()
        // The groups this edit doesn't own keep their current values and visibility.
        val kept = DetailGroup.entries - edit.groups
        val (allDetails, allVisibility) =
            if (kept.isEmpty()) {
                edit.details to edit.visibility
            } else {
                val (currentDetails, currentVisibility) = ownDetails(existing, current)
                kept.fold(edit.details.only(edit.groups) to edit.visibility.only(edit.groups)) { (details, visibility), group ->
                    group.copy(currentDetails, details) to group.withVisibility(visibility, group.visibility(currentVisibility))
                }
            }
        val placed = DetailGroup.entries.filter { it.isSet(allDetails) }.groupBy { it.visibility(allVisibility) ?: Visibility.SELF }
        val final = AthleteSpaces.TIERS.associateWith { tier -> allDetails.only(placed[tier].orEmpty()) }
        // Every tier left holding details gets a current managing app, even when this save doesn't change its record: re-saving is how an
        // athlete whose mutuals were refused (the AppView's DID changed) lets them back in.
        for (tier in AthleteSpaces.TIERS) if (final.getValue(tier) != ProfileDetails()) tiers.ensure(tier)
        // 1. Every detail into its new home, keeping the copy in a tier it's moving out of (a cleared detail goes in step 3).
        val written =
            AthleteSpaces.TIERS.associateWith { tier ->
                val now = current.getValue(tier)
                val target = final.getValue(tier)
                val leaving = DetailGroup.entries.filter { it.isSet(now) && !it.isSet(target) && it.isSet(allDetails) }
                val first = leaving.fold(target) { acc, group -> group.copy(now, acc) }
                if (first != now && first != ProfileDetails()) tiers.put(tier, first) else now
            }
        // 2. The public record.
        val avatar =
            when (val change = edit.avatar) {
                AvatarEdit.Keep -> record.avatar
                AvatarEdit.Remove -> null
                is AvatarEdit.Replace -> clients.pds.uploadBlob(change.bytes, change.mimeType)
            }
        val saved =
            record.copy(
                displayName = edit.displayName.trim().ifEmpty { null },
                description = edit.description.trim().ifEmpty { null },
                avatar = avatar,
                details = allDetails.only(placed[Visibility.PUBLIC].orEmpty()).takeUnless { it == ProfileDetails() },
            )
        putProfileRecord(saved)
        // 3. Trim the copies left behind, and drop what was cleared.
        for (tier in AthleteSpaces.TIERS) {
            val target = final.getValue(tier)
            when {
                target == written.getValue(tier) -> Unit
                target == ProfileDetails() -> tiers.delete(tier)
                else -> tiers.put(tier, target)
            }
        }
        pendingOwnProfile = Pending(saved, allDetails)
    }

    private suspend fun putProfileRecord(record: ProfileRecord) {
        clients.pds.procedure(
            PutRecord.method,
            PutRecord.Input(
                repo = clients.did,
                collection = Nsid.ACTOR_PROFILE,
                rkey = ProfileRecord.RKEY,
                record = AtJson.encodeToJsonElement(ProfileRecord.serializer(), record),
            ),
        )
    }

    /** Follows [profile]'s athlete, and returns [profile] as it now stands (following, one more follower). */
    suspend fun follow(profile: ProfileView): ProfileView {
        if (profile.viewer?.following != null) return profile
        val rkey = Tid.next(clock)
        clients.pds.procedure(
            PutRecord.method,
            PutRecord.Input(
                repo = clients.did,
                collection = Nsid.GRAPH_FOLLOW,
                rkey = rkey,
                record =
                    AtJson.encodeToJsonElement(
                        FollowRecord.serializer(),
                        FollowRecord(profile.did, clock.now()),
                    ),
            ),
        )
        pendingFollows.wrote(profile.did, AtUri("at://${clients.did.value}/${Nsid.GRAPH_FOLLOW}/$rkey"))
        return pendingFollows.applyTo(profile)
    }

    /** Unfollows [profile]'s athlete, and returns [profile] as it now stands (not following, one fewer follower). */
    suspend fun unfollow(profile: ProfileView): ProfileView {
        val rkey = profile.viewer?.following?.rkey ?: return profile
        clients.pds.procedure(
            DeleteRecord.method,
            DeleteRecord.Input(clients.did, Nsid.GRAPH_FOLLOW, rkey),
        )
        pendingFollows.wrote(profile.did, null)
        return pendingFollows.applyTo(profile)
    }

    private companion object {
        const val RECORD_NOT_FOUND = "RecordNotFound"
    }
}

/**
 * Every detail the athlete has set, from their [record] and their [tiers]' records, and an effective visibility for each group (see
 * [OwnProfile]). A detail a failed save left in two places shows where it's seen most widely.
 */
private fun ownDetails(record: ProfileRecord?, tiers: Map<String, ProfileDetails>): Pair<ProfileDetails, DetailsVisibility> {
    val sources = listOf(Visibility.PUBLIC to (record?.details ?: ProfileDetails())) + tiers.toList()
    var details = ProfileDetails()
    var visibility = DetailsVisibility()
    for (group in DetailGroup.entries) {
        val (shownTo, source) = sources.firstOrNull { (_, held) -> group.isSet(held) } ?: (Visibility.SELF to ProfileDetails())
        details = group.copy(source, details)
        visibility = group.withVisibility(visibility, shownTo)
    }
    return details to visibility
}

/** [top] with each group it doesn't set taken from [bottom]. */
private fun under(top: ProfileDetails, bottom: ProfileDetails): ProfileDetails =
    DetailGroup.entries.filterNot { it.isSet(top) }.fold(top) { acc, group -> group.copy(bottom, acc) }
