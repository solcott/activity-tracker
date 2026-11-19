package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamConsumer
import io.github.solcott.activitytracker.appview.ops.WorkerNames
import io.github.solcott.activitytracker.appview.ops.WorkerRegistry
import io.github.solcott.activitytracker.appview.ops.WorkerSupervisor
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * A rewind through the supervisor (admin/ingest-health): the cursor written while Jetstream is stopped is the one its next connection uses.
 */
class JetstreamRewindTest {
    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }

    private val start = Instant.parse("2026-11-18T12:00:00Z")

    /** An event Jetstream witnessed [n] seconds after [start], in a collection the indexer skips. */
    private fun event(n: Int): String {
        val at = start + n.seconds
        return """{"${'$'}type":"message","payload":{"${'$'}type":"network.bsky.jetstream.subscribeEvents#commit",""" +
            """"did":"did:plc:alice","time":"$at","witnessedAt":"$at","operation":"create","collection":"app.example.other","rkey":"3k$n"}}"""
    }

    @Test
    fun aRewindWhileTheConsumerIsSavingKeepsTheChosenCursor() = runBlocking {
        val urls = CopyOnWriteArrayList<String>()
        val consumer =
            JetstreamConsumer("ws://jetstream.test", db, Indexer(db, { "x.test" })) { url, onText ->
                urls += url
                if (urls.size > 1) awaitCancellation()
                // The first connection streams events until it's cancelled, saving the cursor every 100.
                var n = 0
                while (true) {
                    onText(event(n++))
                    if (n % 10 == 0) delay(1)
                }
            }
        val app = CoroutineScope(SupervisorJob())
        val supervisor = WorkerSupervisor(app, WorkerRegistry())
        try {
            supervisor.stream(WorkerNames.JETSTREAM) { consumer.run(it) }
            withTimeout(10.seconds) { while ((consumer.storedCursorUs() ?: 0) < (start + 150.seconds).toUs()) delay(5) }
            val target = (start + 30.seconds).toUs()
            assertNull(supervisor.restart(WorkerNames.JETSTREAM) { consumer.storeCursor(target) })
            withTimeout(10.seconds) { while (urls.size < 2) delay(5) }
            assertEquals(target, consumer.storedCursorUs())
            assertTrue(urls[1].endsWith("cursor=${target - JetstreamConsumer.REWIND_US}"), urls[1])
            assertEquals(1, supervisor.registry[WorkerNames.JETSTREAM]!!.manualRestarts)
        } finally {
            app.cancel()
        }
    }

    private fun Instant.toUs() = epochSeconds * 1_000_000 + nanosecondsOfSecond / 1_000
}
