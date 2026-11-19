package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.ops.Readiness
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database

/** The readiness check (dev/worker-telemetry): ready while the database answers; liveness never touches it. */
class ReadinessTest {
    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val config = AppViewConfig.fromEnv(emptyMap())
    private val verifier = ServiceAuthVerifier(config.serviceDid, keys = { null })

    /** A database nothing answers at. */
    private val unreachable = Database.connect("jdbc:h2:tcp://127.0.0.1:1/nothing;MODE=PostgreSQL")

    @Test
    fun readyWhileTheDatabaseAnswers() = testApplication {
        application { appView(config, db, verifier) }
        val ready = client.get("/xrpc/_ready")
        assertEquals(HttpStatusCode.OK, ready.status)
        assertEquals("""{"status":{"ok":true,"database":true}}""", ready.bodyAsText())
    }

    @Test
    fun notReadyWhenTheDatabaseIsDownWhileHealthStaysOk() = testApplication {
        application { appView(config, db, verifier, readiness = Readiness(unreachable)) }
        val ready = client.get("/xrpc/_ready")
        assertEquals(HttpStatusCode.ServiceUnavailable, ready.status)
        assertEquals("""{"status":{"ok":false,"database":false}}""", ready.bodyAsText())
        val health = client.get("/xrpc/_health")
        assertEquals(HttpStatusCode.OK, health.status)
        assertEquals("""{"status":{"ok":true}}""", health.bodyAsText())
    }

    @Test
    fun aDatabaseThatDoesntAnswerInTimeIsDown() = runBlocking {
        val readiness = Readiness({ awaitCancellation() }, timeout = 50.milliseconds)
        assertEquals(false, readiness.databaseUp())
    }

    private fun TestScope.counting(pings: AtomicInteger) =
        Readiness({ pings.incrementAndGet() }, timeSource = testScheduler.timeSource, dispatcher = StandardTestDispatcher(testScheduler))

    @Test
    fun aFloodOfChecksQueriesTheDatabaseOnce() = runTest {
        val pings = AtomicInteger()
        val readiness = counting(pings)
        val answers = List(100) { async { readiness.databaseUp() } }.awaitAll()
        assertEquals(List(100) { true }, answers)
        assertEquals(1, pings.get())
    }

    @Test
    fun anAnswerIsReusedForFiveSeconds() = runTest {
        val pings = AtomicInteger()
        val readiness = counting(pings)
        readiness.databaseUp()
        advanceTimeBy(4.seconds)
        readiness.databaseUp()
        assertEquals(1, pings.get())
        advanceTimeBy(2.seconds)
        readiness.databaseUp()
        assertEquals(2, pings.get())
    }
}
