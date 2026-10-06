package io.github.solcott.activitytracker.data.profile

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ProfileRecord
import io.github.solcott.activitytracker.lexicon.atproto.CreateRecord
import kotlin.time.Clock

/**
 * Gives a newly signed-in athlete an (empty) `actor.profile` record. The AppView only knows accounts that have written one of this app's
 * records, so without it an athlete who hasn't posted, followed or edited their profile yet can't be found in search or opened.
 */
@SingleIn(SessionScope::class)
@Inject
class ProfileSetup(
    private val clients: SessionClients,
    private val profiles: ProfileRepository,
    private val clock: Clock,
    logger: Logger,
) {
    private val log = logger.withTag("ProfileSetup")

    /**
     * Creates the profile record if the athlete has none. Uses `createRecord`, which the PDS refuses when the record already exists, so a
     * profile saved meanwhile (on another device) is never replaced. Failures are logged: the next session start tries again.
     */
    suspend fun ensureProfile() {
        runSuspendCatching {
            if (profiles.ownProfileRecord() != null) return@runSuspendCatching
            clients.pds.procedure(
                CreateRecord.method,
                CreateRecord.Input(
                    repo = clients.did,
                    collection = Nsid.ACTOR_PROFILE,
                    record = AtJson.encodeToJsonElement(ProfileRecord.serializer(), ProfileRecord(createdAt = clock.now())),
                    rkey = ProfileRecord.RKEY,
                    validate = false,
                ),
            )
        }
            .onFailure { e -> log.w(e) { "Couldn't create the profile record" } }
    }
}
