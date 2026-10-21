package io.github.solcott.activitytracker.data.moderation

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.NoParams
import io.github.solcott.activitytracker.lexicon.app.GetModerationRole
import io.github.solcott.activitytracker.lexicon.app.GetSubject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The moderator side (`moderation/private-subjects`): whether the athlete is on the app's labeler team as a moderator or admin, asked once
 * a session, and the subjects they open through the AppView.
 */
@SingleIn(SessionScope::class)
@Inject
class ModerationRepository(private val clients: SessionClients) {
    private val mutex = Mutex()
    private var moderator: Boolean? = null

    /** Whether the athlete may open subjects; false with no labeler configured, or when the AppView can't say. Asked once a session. */
    suspend fun canModerate(): Boolean = mutex.withLock {
        moderator
            ?: (clients.labelerDid != null &&
                    clients.appView.query(GetModerationRole.method, NoParams).role in GetModerationRole.MODERATOR_ROLES)
                .also { moderator = it }
    }

    /** The subject at [uri] as the AppView serves it to moderators; null when it answers `NotFound`, which means not under review. */
    suspend fun subject(uri: AtUri): GetSubject.Output? =
        try {
            clients.appView.query(GetSubject.method, GetSubject.Params(uri))
        } catch (e: XrpcException) {
            if (e.error == "NotFound") null else throw e
        }
}
