package io.github.solcott.activitytracker.app.host

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.app.SessionMode
import io.github.solcott.activitytracker.app.sessionModeOf
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.activity.ActivityRepository
import io.github.solcott.activitytracker.data.activity.LegacyGroupMigration
import io.github.solcott.activitytracker.data.activity.OutboxPacer
import io.github.solcott.activitytracker.data.activity.UploadQueue
import io.github.solcott.activitytracker.data.auth.AccountMarks
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.export.DataExporter
import io.github.solcott.activitytracker.data.export.DataExports
import io.github.solcott.activitytracker.data.importsave.ImportSaveRunner
import io.github.solcott.activitytracker.data.importsave.ImportSaveStore
import io.github.solcott.activitytracker.data.importsave.ImportSaves
import io.github.solcott.activitytracker.data.profile.ProfileSetup
import io.github.solcott.activitytracker.data.segments.LeaderboardBracketsRepository
import io.github.solcott.activitytracker.data.units.UnitsRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * What a signed-in session shows and when its background work runs, decided once for every shell (dev/presenter-host): the Compose shell
 * (`App`) and the presenter host both read [mode] and keep [run] going for as long as the session is on screen.
 *
 * The rules are auth/leaving's. A leave recorded on this device shows only the leave, and one on another device only the choice to discard
 * or rejoin; neither runs the session's work, which writes to the PDS and would write behind the leave. Whether the athlete left on another
 * device is asked once, before any of that work starts.
 */
@SingleIn(SessionScope::class)
@Inject
class SessionRunner(private val steps: SessionSteps) {
    /** [SessionMode.Unknown] until Room answers whether a leave is recorded here. */
    val mode: StateFlow<SessionMode>
        field = MutableStateFlow(SessionMode.Unknown)

    /**
     * Runs the session until cancelled. Asking whether the PDS hosts spaces and reading the athlete's units start at once, whatever the
     * mode. The still-here check runs once [mode] is known not to be a leave here. The background work runs only while the mode is
     * [SessionMode.Open] and that check is done: it stops when the mode leaves open and starts again when it returns.
     */
    suspend fun run(): Unit = coroutineScope {
        // Null until known, like the mode.
        val leaving = MutableStateFlow<Boolean?>(null)
        val checked = MutableStateFlow(false)
        launch { steps.resolveSpacesSupport() }
        launch { steps.refreshUnits() }
        launch { steps.leaving.collect { leaving.value = it } }
        launch { combine(leaving, steps.left) { here, elsewhere -> sessionModeOf(here, elsewhere) }.collect { mode.value = it } }
        // collectLatest: a leave starting here mid-check cancels it, and it is asked again if the leave is undone.
        launch {
            leaving.collectLatest { here ->
                if (here == false && !checked.value) {
                    steps.checkStillHere()
                    checked.value = true
                }
            }
        }
        launch {
            combine(mode, checked) { mode, checked -> mode == SessionMode.Open && checked }
                .distinctUntilChanged()
                .collectLatest { open -> if (open) steps.runWork() }
        }
    }
}

/** What [SessionRunner] reads and starts. An interface so its rules are tested without a PDS; the app's is [RepositorySessionSteps]. */
interface SessionSteps {
    /** Whether a leave of this account is recorded on this device. Emits once Room answers. */
    val leaving: Flow<Boolean>

    /** Whether the athlete left on another device. */
    val left: StateFlow<Boolean>

    /** Whether the athlete's PDS hosts spaces at all; until it answers, the session's scope decides. */
    suspend fun resolveSpacesSupport()

    suspend fun refreshUnits()

    /** Asks, before anything is sent for the account, whether the athlete left on another device; the answer shows in [left]. */
    suspend fun checkStillHere()

    /** The session's background work. Each piece writes to the PDS or drains the outbox. Runs until cancelled. */
    suspend fun runWork()
}

/** [SessionSteps] over the session's repositories. */
@Suppress("LongParameterList") // One repository for each piece of work a session starts.
@ContributesBinding(SessionScope::class)
@Inject
class RepositorySessionSteps(
    private val session: OAuthSession,
    private val sessionClients: SessionClients,
    private val unitsRepository: UnitsRepository,
    private val accountMarks: AccountMarks,
    private val activityRepository: ActivityRepository,
    private val leaderboardBrackets: LeaderboardBracketsRepository,
    private val outboxPacer: OutboxPacer,
    private val uploadQueue: UploadQueue,
    private val importSaves: ImportSaves,
    private val importSaveStore: ImportSaveStore,
    private val importSaveRunner: ImportSaveRunner,
    private val dataExports: DataExports,
    private val dataExporter: DataExporter,
    private val legacyGroupMigration: LegacyGroupMigration,
    private val profileSetup: ProfileSetup,
) : SessionSteps {
    override val leaving: Flow<Boolean>
        get() = accountMarks.observeLeaving(session.did).map { it != null }

    override val left: StateFlow<Boolean>
        get() = sessionClients.accountLeft

    override suspend fun resolveSpacesSupport() = sessionClients.resolveSpacesSupport()

    override suspend fun refreshUnits() = unitsRepository.refresh()

    override suspend fun checkStillHere() {
        sessionClients.checkStillHere()
    }

    override suspend fun runWork(): Unit = coroutineScope {
        // Session-long, so a re-trim after a privacy zones save outlives the settings screen.
        launch { activityRepository.repairRoutesOnSave() }
        // Session-long too: leaderboard brackets refreshed at sign-in (a birthday) and after each profile save.
        launch { leaderboardBrackets.refreshOnSave() }
        // Session-long: queued writes go on their own, waiting out the PDS's rate limits (data/outbox).
        launch { outboxPacer.run() }
        // Finished uploads a week old leave the queue (data/upload-queue).
        launch { runSuspendCatching { uploadQueue.prune() } }
        // A batch save left unfinished (the app was killed, or quit) resumes, and what no save names any more is let go
        // (feed/batch-import).
        launch { runSuspendCatching { importSaves.resume(session.did, importSaveStore, importSaveRunner) } }
        // A data export left running (the app was killed, or quit) goes on; one a week past ready is deleted (auth/data-export).
        launch { runSuspendCatching { dataExports.resume(session.did) { dataExporter.runCurrent() } } }
        // Once per account: legacy `recordingOf` groups become combined activities (retried each sign-in until all succeed).
        launch { legacyGroupMigration.runOncePerSession() }
        // So the AppView knows this athlete (and others can find them) before they post anything.
        launch { profileSetup.ensureProfile() }
    }
}
