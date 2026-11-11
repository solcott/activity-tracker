package io.github.solcott.activitytracker.app.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.screen.Screen
import com.slack.circuit.subcircuit.SubPresenterFactory
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.app.SessionMode
import io.github.solcott.activitytracker.app.nav.TopLevelDestination
import io.github.solcott.activitytracker.app.record.RecordScreen
import io.github.solcott.activitytracker.app.record.RecordScreenRequests
import io.github.solcott.activitytracker.app.settings.AccountScreen
import io.github.solcott.activitytracker.app.settings.AccountScreenRequests
import io.github.solcott.activitytracker.app.settings.LeaveScreen
import io.github.solcott.activitytracker.app.settings.LeftElsewhereScreen
import io.github.solcott.activitytracker.app.settings.SettingsScreen
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.location.ActivityRecorder
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * One signed-in account's screens, run with no UI (dev/presenter-host): a stack per tab that presenters navigate, the single screen a leave
 * shows in place of them, and the session's mode. A native UI shows what this holds and reports what the athlete did: chose a tab, went
 * back.
 *
 * Nothing runs until [run] is called. Main thread only.
 */
@Stable
@SingleIn(SessionScope::class)
@Inject
class SessionHost
// Internal for the same reason as AppHost's.
@Suppress("LongParameterList") // What the session graph injects: the presenters, the sub-screens', and one piece per request it follows.
internal constructor(
    session: OAuthSession,
    factories: Set<Presenter.Factory>,
    subFactories: Set<SubPresenterFactory>,
    private val runner: SessionRunner,
    private val recording: RecordingRecovery,
    private val recordRequests: RecordScreenRequests,
    private val accountRequests: AccountScreenRequests,
) {
    val did: Did = session.did

    private val presenters = Presenters(factories, subFactories)
    private val keys = RecordKeys()

    /** What the session shows; see [SessionMode]. The tabs are the app only while it is [SessionMode.Open]. */
    val mode: StateFlow<SessionMode>
        get() = runner.mode

    /** [mode], as a Swift UI follows it (see `SwiftFlows.kt`). */
    val shownMode: StateFlow<ShownMode> = runner.mode.mapState(::ShownMode)

    /** The athlete's unit system, which a native UI formats stats in (dev/shared-display-text). Wrapped for Swift, like [shownMode]. */
    val shownUnits: StateFlow<ShownUnits> = runner.units.mapState(::ShownUnits)

    /** One stack per tab, in the tabs' order. */
    val tabs: List<TabStack> = TopLevelDestination.entries.map { TabStack(it, it.screen(did.value), presenters, keys) }

    /** The tab on screen. */
    val selected: StateFlow<TopLevelDestination>
        field = MutableStateFlow(TopLevelDestination.Feed)

    /** [selected], as a Swift UI follows it (see `SwiftFlows.kt`). */
    val shownTab: StateFlow<ShownTab> = selected.mapState(::ShownTab)

    /** All that shows while a leave of this account is in progress on this device (auth/leaving): its progress, which resumes. */
    val leaving: HostRecord = single(LeaveScreen(resume = true))

    /** All that shows after the athlete left on another device: the choice to discard this device's work or rejoin. */
    val leftElsewhere: HostRecord = single(LeftElsewhereScreen)

    fun stack(destination: TopLevelDestination): TabStack = tabs[destination.ordinal]

    /** Chooses a tab. Choosing the one already on screen returns it to its first screen. */
    fun select(destination: TopLevelDestination) {
        if (destination == selected.value) stack(destination).popTo(1) else selected.value = destination
    }

    /**
     * Runs the session until cancelled: its mode and background work ([SessionRunner]), its presenters, and, while the app is open, the
     * requests to show Record or Account and the offer of an unfinished recording.
     */
    suspend fun run(clock: HostClock): Unit = coroutineScope {
        launch { runner.run() }
        launch { compose(clock) { Records() } }
        var open = false
        mode.collectLatest { mode ->
            if (mode == SessionMode.Open) {
                open = true
                whileOpen()
            } else if (open) {
                open = false
                closeTabs()
            }
        }
    }

    /** A screen shown on its own, with no stack to go back through; its presenter's navigator does nothing. */
    private fun single(screen: Screen): HostRecord =
        HostRecord(
            keys.next(),
            screen,
            isRoot = true,
            showsTabs = false,
            presenter = presenters.create(screen, Navigator.NoOp),
            presenters = presenters,
        )

    /** Anything but the open app drops the tabs' screens, as the Compose shell does: coming back starts from the feed. */
    private fun closeTabs() {
        tabs.forEach { it.reset() }
        selected.value = TopLevelDestination.Feed
    }

    private suspend fun whileOpen(): Unit = coroutineScope {
        // An unfinished recording of this account's, from before the app stopped, is offered back first, and one still going since its
        // session expired is shown as it is (record/gps-recording).
        launch { if (recording.recover(did)) current().goTo(RecordScreen) }
        // The recording notification was tapped.
        launch {
            recordRequests.requests.collect {
                recordRequests.handled()
                current().goTo(RecordScreen)
            }
        }
        // The "export ready" notification was tapped (auth/data-export): Settings, then its Account page.
        launch {
            accountRequests.requests.collect {
                accountRequests.handled()
                if (current().peek() != AccountScreen) {
                    current().goTo(SettingsScreen)
                    current().goTo(AccountScreen)
                }
            }
        }
    }

    private fun current(): TabStack = stack(selected.value)

    /** Which records' presenters run, by the mode: nothing until it is known, a leave's one screen, or the tabs' shown screens. */
    @Composable
    private fun Records() {
        val mode by runner.mode.collectAsState()
        when (mode) {
            SessionMode.Unknown -> Unit
            SessionMode.Leaving -> RunRecord(leaving)
            SessionMode.LeftElsewhere -> RunRecord(leftElsewhere)
            SessionMode.Open -> Tabs()
        }
    }

    /**
     * The open app's records: the top screen of the chosen tab runs, and so does any screen the UI reports visible. The rest are stopped
     * with their state kept. A page shown beside a record without being on the stack ([HostRecord.paneDefault]) is kept with that record
     * and runs only while reported visible.
     */
    @Composable
    private fun Tabs() {
        val chosen by selected.collectAsState()
        val all = mutableListOf<HostRecord>()
        val running = mutableListOf<HostRecord>()
        tabs.forEach { stack ->
            val records by stack.records.collectAsState()
            records.forEachIndexed { index, record ->
                all += record
                val top = stack.destination == chosen && index == records.lastIndex
                val visible = key(record.key) { isReportedVisible(record) }
                if (visible || top) running += record
                record.paneDefault?.let { beside ->
                    all += beside
                    if (key(beside.key) { isReportedVisible(beside) }) running += beside
                }
            }
        }
        RunRecords(all.toImmutableList(), running.toImmutableList())
    }
}

/**
 * A [SessionHost] over [factories] and [subFactories], with no recording to recover and no requests, for a test outside this module that
 * runs real presenters through it; the app's comes from its session graph. Not exported to Swift, as [appHostOf] isn't.
 */
fun sessionHostOf(
    session: OAuthSession,
    factories: Set<Presenter.Factory>,
    subFactories: Set<SubPresenterFactory>,
    steps: SessionSteps,
): SessionHost =
    SessionHost(session, factories, subFactories, SessionRunner(steps), { false }, RecordScreenRequests(), AccountScreenRequests())

/** Whether an unfinished recording of an account's should be shown when its session opens. */
fun interface RecordingRecovery {
    /** Brings back what was being recorded for [did] before the app stopped, and says whether Record should open on it. */
    suspend fun recover(did: Did): Boolean
}

/** [RecordingRecovery] over the app's recorder. */
@ContributesBinding(SessionScope::class)
@Inject
class RecorderRecovery(private val recorder: ActivityRecorder) : RecordingRecovery {
    override suspend fun recover(did: Did): Boolean {
        recorder.recover(did.value)
        val state = recorder.state.value
        return state.recovered || state.inProgressFor(did.value)
    }
}
