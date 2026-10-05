package io.github.solcott.activitytracker.data.profile

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.FollowRecord
import io.github.solcott.activitytracker.lexicon.app.GetFollowers
import io.github.solcott.activitytracker.lexicon.app.GetFollows
import io.github.solcott.activitytracker.lexicon.app.GetProfile
import io.github.solcott.activitytracker.lexicon.app.ProfileRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.atproto.DeleteRecord
import io.github.solcott.activitytracker.lexicon.atproto.GetRecord
import io.github.solcott.activitytracker.lexicon.atproto.PutRecord
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
     * The signed-in athlete's last saved profile record, until the AppView shows it. [profile] lays its display name and bio over the
     * AppView's view of the athlete so an edit shows at once.
     */
    private var pendingOwnProfile: ProfileRecord? = null

    suspend fun profile(actor: String): ProfileView {
        val view: ProfileView = clients.appView.query(GetProfile.method, GetProfile.Params(actor))
        val pending = pendingOwnProfile
        return when {
            view.did != did || pending == null -> {
                view
            }

            view.displayName == pending.displayName && view.description == pending.description -> {
                pendingOwnProfile = null
                view
            }

            else -> {
                view.copy(displayName = pending.displayName, description = pending.description)
            }
        }
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

    suspend fun saveProfile(record: ProfileRecord) {
        clients.pds.procedure(
            PutRecord.method,
            PutRecord.Input(
                repo = clients.did,
                collection = Nsid.ACTOR_PROFILE,
                rkey = ProfileRecord.RKEY,
                record = AtJson.encodeToJsonElement(ProfileRecord.serializer(), record),
            ),
        )
        pendingOwnProfile = record
    }

    /**
     * Sets the display name and bio on the signed-in athlete's profile record; a blank value clears that field. Reads the record first
     * because `putRecord` replaces it whole, and the other fields (units, sports) must survive.
     */
    suspend fun saveDetails(displayName: String, description: String) {
        val record = ownProfileRecord() ?: ProfileRecord(createdAt = clock.now())
        saveProfile(
            record.copy(
                displayName = displayName.trim().ifEmpty { null },
                description = description.trim().ifEmpty { null },
            )
        )
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
