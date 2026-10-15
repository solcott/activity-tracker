package io.github.solcott.activitytracker.data.profile

import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ProfileRecord
import io.github.solcott.activitytracker.lexicon.atproto.GetRecord

/**
 * Runs before the athlete's training is saved at a narrower visibility: whatever reveals it more widely (their activities' zones) is
 * narrowed first, so a failure leaves it narrower than the training rather than wider. Throws to stop the save.
 */
fun interface TrainingNarrowing {
    suspend fun narrowTo(visibility: String)
}

/**
 * Where the signed-in athlete's training details are as saved now, from their PDS rather than the AppView: a [Visibility] value, or null
 * when they have none. Training held in two places (a failed move) counts at the wider.
 */
internal suspend fun SessionClients.ownTrainingVisibility(): String? {
    val (details, visibility) = ownDetails(ownProfileRecord(), if (canUseSpaces) tiers.own() else emptyMap())
    return if (DetailGroup.Training.isSet(details)) DetailGroup.Training.visibility(visibility) else null
}

/** The signed-in athlete's own profile record, read from their PDS (not the AppView, which may lag); null if they have none. */
internal suspend fun SessionClients.ownProfileRecord(): ProfileRecord? {
    val output =
        try {
            pds.query(GetRecord.method, GetRecord.Params(did.value, Nsid.ACTOR_PROFILE, ProfileRecord.RKEY))
        } catch (e: XrpcException) {
            if (e.error == RECORD_NOT_FOUND) return null
            throw e
        }
    return AtJson.decodeFromJsonElement(ProfileRecord.serializer(), output.value)
}

private const val RECORD_NOT_FOUND = "RecordNotFound"
