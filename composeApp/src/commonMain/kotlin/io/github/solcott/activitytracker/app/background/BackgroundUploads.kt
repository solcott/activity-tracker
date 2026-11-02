package io.github.solcott.activitytracker.app.background

import io.github.solcott.activitytracker.app.di.AppGraph
import io.github.solcott.activitytracker.app.di.SessionGraph
import io.github.solcott.activitytracker.data.activity.ImportProgress
import io.github.solcott.activitytracker.data.activity.PacerOutcome
import io.github.solcott.activitytracker.lexicon.Did
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One background upload pass for the signed-in account (data/outbox), as a phone's background job runs it: restores the session, sends what
 * it can through a session graph of its own (sharing the app's per-account lock and pauses), and says how it ended. Null when no one is
 * signed in, so the job stops asking to run. [onImportProgress], when given, hears the imports' progress as the pass goes
 * (data/upload-queue), null once none is uploading.
 */
suspend fun AppGraph.uploadQueuedWrites(onImportProgress: (suspend (ImportProgress?) -> Unit)? = null): PacerOutcome? {
    val session = authRepository.restore() ?: return null
    val graph = backgroundGraph(session.did) { createSessionGraph(session) }
    return coroutineScope {
        val watching = onImportProgress?.let { listen -> launch { graph.uploadQueue.importProgress.collect { listen(it) } } }
        try {
            graph.outboxPacer.runUntilIdleOrPaused()
        } finally {
            watching?.cancel()
        }
    }
}

private val backgroundLock = Mutex()
private var background: Pair<Did, SessionGraph>? = null

/** The background passes' one session graph for [did], so a process that outlives many passes doesn't build one per pass. */
private suspend fun backgroundGraph(did: Did, create: () -> SessionGraph): SessionGraph = backgroundLock.withLock {
    background?.takeIf { it.first == did }?.second ?: create().also { background = did to it }
}
