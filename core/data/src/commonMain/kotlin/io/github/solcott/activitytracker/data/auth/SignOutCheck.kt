package io.github.solcott.activitytracker.data.auth

import androidx.compose.runtime.Immutable
import dev.zacsweers.metro.Inject
import io.github.solcott.activitytracker.data.activity.UploadQueue
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.importsave.ImportSaves
import kotlinx.coroutines.flow.first

/**
 * What ending the session would leave behind on this device: [changes] not uploaded (waiting, uploading or refused), which stay and upload
 * when the account signs in here again, and whether an import is still [importSaving], whose unsaved activities signing out drops.
 */
@Immutable data class PendingWork(val changes: Int, val importSaving: Boolean)

/** Asks, at the moment the athlete chooses to end the session, what would be left behind (auth/account-page). */
@Inject
class SignOutCheck(private val queue: UploadQueue, private val saves: ImportSaves, private val clients: SessionClients) {
    /** What's pending for this account now, or null with nothing to ask about. */
    suspend fun pending(): PendingWork? {
        val summary = queue.summary.first()
        val changes = summary.own + summary.imported + summary.other + summary.refused
        val importSaving = saves.hasSaves(clients.did)
        return if (changes == 0 && !importSaving) null else PendingWork(changes, importSaving)
    }
}
