package io.github.solcott.activitytracker.data.profile

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.NoParams
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.DetailsVisibility
import io.github.solcott.activitytracker.lexicon.app.FollowRecord
import io.github.solcott.activitytracker.lexicon.app.GetFollowers
import io.github.solcott.activitytracker.lexicon.app.GetFollows
import io.github.solcott.activitytracker.lexicon.app.GetPrivateDetails
import io.github.solcott.activitytracker.lexicon.app.GetProfile
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.app.ProfileRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.PutPrivateDetails
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.DeleteRecord
import io.github.solcott.activitytracker.lexicon.atproto.GetRecord
import io.github.solcott.activitytracker.lexicon.atproto.PutRecord
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import kotlin.time.Clock

@SingleIn(SessionScope::class)
@Inject
class ProfileRepository(
    private val clients: SessionClients,
    private val clock: Clock,
) {
    val did
        get() = clients.did

    /**
     * The signed-in athlete's last save, until the AppView shows it. [profile] lays it over the AppView's view of the athlete so an edit
     * shows at once. [Pending.details] is null after a save that didn't touch the details (a units change).
     */
    private class Pending(val record: ProfileRecord, val details: ProfileDetails?)

    private var pendingOwnProfile: Pending? = null

    suspend fun profile(actor: String): ProfileView {
        val view: ProfileView = clients.appView.query(GetProfile.method, GetProfile.Params(actor))
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
                    else avatarCid?.let { blobUrl(clients.pdsUrl, did, it) },
            )
        if (shown == view) pendingOwnProfile = null
        return shown
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
     * The signed-in athlete's profile for editing: the record from their PDS (the AppView may lag) and their non-public details from the
     * AppView, which is the only place those live.
     */
    suspend fun ownProfile(): OwnProfile {
        val record = ownProfileRecord()
        val hidden = clients.appView.query(GetPrivateDetails.method, NoParams)
        val public = record?.details ?: ProfileDetails()
        var details = ProfileDetails()
        var visibility = DetailsVisibility()
        for (group in DetailGroup.entries) {
            val (source, shownTo) =
                when {
                    group.isSet(public) -> public to Visibility.PUBLIC
                    group.isSet(hidden.details) -> hidden.details to (group.visibility(hidden.visibility) ?: Visibility.SELF)
                    else -> ProfileDetails() to Visibility.SELF
                }
            details = group.copy(source, details)
            visibility = group.withVisibility(visibility, shownTo)
        }
        val avatar = record?.avatar
        return OwnProfile(
            record?.displayName,
            record?.description,
            avatar,
            avatar?.let { blobUrl(clients.pdsUrl, did, it.ref.link) },
            details,
            visibility,
        )
    }

    /** Replaces the profile record whole. Read it first ([ownProfileRecord]) so fields this save doesn't change survive. */
    suspend fun saveProfile(record: ProfileRecord) {
        putProfileRecord(record)
        pendingOwnProfile = Pending(record, pendingOwnProfile?.details)
    }

    /**
     * Saves [edit]. Each detail goes in exactly one place by its visibility: the public record, or the AppView's private store. When a
     * detail moves, its new home is written before its old one is cleared, so a failure part-way leaves it in both places (where it counts
     * as public, which it was or is about to be) rather than losing it.
     */
    suspend fun save(edit: ProfileEdit) {
        val record = ownProfileRecord() ?: ProfileRecord(createdAt = clock.now())
        val (publicGroups, privateGroups) =
            DetailGroup.entries.filter { it.isSet(edit.details) }.partition { it.visibility(edit.visibility) == Visibility.PUBLIC }
        val hiddenDetails = edit.details.only(privateGroups)
        val hiddenVisibility = edit.visibility.only(privateGroups)
        // A detail going public stays in the private store (as Only me) until the record holds it.
        val goingPublic = publicGroups.filterNot { it.isSet(record.details ?: ProfileDetails()) }
        putPrivateDetails(
            hiddenDetails.let { d -> goingPublic.fold(d) { acc, group -> group.copy(edit.details, acc) } },
            goingPublic.fold(hiddenVisibility) { acc, group -> group.withVisibility(acc, Visibility.SELF) },
        )
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
                details = edit.details.only(publicGroups).takeUnless { it == ProfileDetails() },
            )
        putProfileRecord(saved)
        if (goingPublic.isNotEmpty()) putPrivateDetails(hiddenDetails, hiddenVisibility)
        pendingOwnProfile = Pending(saved, edit.details)
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

    private suspend fun putPrivateDetails(details: ProfileDetails, visibility: DetailsVisibility) {
        clients.appView.procedure(PutPrivateDetails.method, PutPrivateDetails.Input(details, visibility))
    }

    suspend fun follow(profile: ProfileView) {
        if (profile.viewer?.following != null) return
        clients.pds.procedure(
            PutRecord.method,
            PutRecord.Input(
                repo = clients.did,
                collection = Nsid.GRAPH_FOLLOW,
                rkey = Tid.next(clock),
                record =
                    AtJson.encodeToJsonElement(
                        FollowRecord.serializer(),
                        FollowRecord(profile.did, clock.now()),
                    ),
            ),
        )
    }

    suspend fun unfollow(profile: ProfileView) {
        val rkey = profile.viewer?.following?.rkey ?: return
        clients.pds.procedure(
            DeleteRecord.method,
            DeleteRecord.Input(clients.did, Nsid.GRAPH_FOLLOW, rkey),
        )
    }

    private companion object {
        const val RECORD_NOT_FOUND = "RecordNotFound"
    }
}

/** A blob on the athlete's own PDS, for showing a new avatar before the AppView has indexed it. */
private fun blobUrl(pdsUrl: String, did: Did, cid: String): String =
    URLBuilder(pdsUrl)
        .apply {
            appendPathSegments("xrpc", "com.atproto.sync.getBlob")
            parameters.append("did", did.value)
            parameters.append("cid", cid)
        }
        .buildString()
