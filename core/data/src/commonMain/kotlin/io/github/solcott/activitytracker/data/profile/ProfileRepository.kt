package io.github.solcott.activitytracker.data.profile

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.FollowRecord
import io.github.solcott.activitytracker.lexicon.app.GetProfile
import io.github.solcott.activitytracker.lexicon.app.ProfileRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.atproto.DeleteRecord
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

    suspend fun profile(actor: String): ProfileView = clients.appView.query(GetProfile.method, GetProfile.Params(actor))

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
}
