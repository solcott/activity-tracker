package io.github.solcott.activitytracker.app.background

import io.github.solcott.activitytracker.app.di.AppGraph
import io.github.solcott.activitytracker.data.activity.PacerOutcome

/**
 * One background upload pass for the signed-in account (data/outbox), as a phone's background job runs it: restores the session, sends what
 * it can through a session graph of its own (sharing the app's per-account lock and pauses), and says how it ended. Null when no one is
 * signed in, so the job stops asking to run.
 */
suspend fun AppGraph.uploadQueuedWrites(): PacerOutcome? {
    val session = authRepository.restore() ?: return null
    return createSessionGraph(session).outboxPacer.runUntilIdleOrPaused()
}
