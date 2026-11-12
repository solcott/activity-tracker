package io.github.solcott.activitytracker.app.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import com.slack.circuit.retained.rememberRetainedStateHolder
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.collect

/**
 * Runs [content] as a composition with no UI until cancelled: the presenters it calls produce states, and nothing is drawn. Molecule also
 * sends apply notifications for state written outside the composition, so text a UI sets in a state's field reaches the presenter.
 */
internal suspend fun compose(clock: HostClock, content: @Composable () -> Unit) {
    val mode =
        when (clock) {
            HostClock.Frame -> RecompositionMode.ContextClock
            HostClock.Immediate -> RecompositionMode.Immediate
        }
    moleculeFlow(mode) { content() }.collect()
}

/**
 * Runs the presenters of [running], and keeps what each record's presenter retained (`rememberRetained`) or saved (`rememberSaveable`,
 * `rememberTextFieldState`) for as long as the record is in [all], whether or not it is running: a record that stops and starts again,
 * because it was covered or its tab wasn't chosen, gets its state back, and one that left [all] is forgotten. This is what Circuit's
 * `NavigableCircuitContent` does for a back stack's records, without the UI.
 */
@Composable
internal fun RunRecords(all: ImmutableList<HostRecord>, running: ImmutableList<HostRecord>) {
    val saved = rememberSaveableStateHolder()
    val retained = rememberRetainedStateHolder()
    running.forEach { record ->
        key(record.key) { saved.SaveableStateProvider(record.key) { retained.RetainedStateProvider(record.key) { RunRecord(record) } } }
    }
    // After the composition is applied, so a record that just left has already stopped and handed its state to the holders.
    val known = remember { mutableSetOf<String>() }
    val keys = all.map { it.key }
    SideEffect {
        (known - keys.toSet()).forEach { gone ->
            saved.removeState(gone)
            retained.removeState(gone)
        }
        known.clear()
        known.addAll(keys)
    }
}

/**
 * Runs [record]'s presenter and publishes each state it produces, then each sub-screen a UI asked it for ([HostRecord.sub]) and each
 * screen it embeds ([HostRecord.embed]). The
 * sub-screens run inside the record, as `SubCircuitContent` runs inside a screen's UI, so what they retain and save is kept and dropped
 * with the record's, and each under its own key, so one that was closed or replaced never gets its old state back.
 */
@Composable
internal fun RunRecord(record: HostRecord) {
    val state = record.presenter.present()
    SideEffect { record.publish(state) }
    val subs by record.subs.collectAsState()
    subs.forEach { sub -> key(sub.key) { RunSub(sub) } }
    // Embedded screens run inside the record as `CircuitContent` runs inside a screen's UI, each under its own key.
    val embedded by record.embedded.collectAsState()
    embedded.forEach { inner -> key(inner.key) { RunRecord(inner) } }
}

@Composable
private fun RunSub(sub: HostSubRecord) {
    val state = sub.presenter.present(sub.forward)
    SideEffect { sub.publish(state) }
}

/** Whether the UI reported [record] visible; read in composition so a change reruns the records. */
@Composable internal fun isReportedVisible(record: HostRecord): Boolean = record.visible.collectAsState().value
