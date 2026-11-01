package io.github.solcott.activitytracker.app.background

import io.github.solcott.activitytracker.app.di.AppGraph
import io.github.solcott.activitytracker.app.di.SessionGraph
import io.github.solcott.activitytracker.data.activity.PacerOutcome
import io.github.solcott.activitytracker.lexicon.Did
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One background upload pass for the signed-in account (data/outbox), as a phone's background job runs it: restores the session, sends what
 * it can through a session graph of its own (sharing the app's per-account lock and pauses), and says how it ended. Null when no one is
 * signed in, so the job stops asking to run.
 */
suspend fun AppGraph.uploadQueuedWrites(): PacerOutcome? {
    val session = authRepository.restore() ?: return null
    return backgroundGraph(session.did) { createSessionGraph(session) }.outboxPacer.runUntilIdleOrPaused()
}

private val backgroundLock = Mutex()
private var background: Pair<Did, SessionGraph>? = null

/** The background passes' one session graph for [did], so a process that outlives many passes doesn't build one per pass. */
private suspend fun backgroundGraph(did: Did, create: () -> SessionGraph): SessionGraph = backgroundLock.withLock {
    background?.takeIf { it.first == did }?.second ?: create().also { background = did to it }
}
