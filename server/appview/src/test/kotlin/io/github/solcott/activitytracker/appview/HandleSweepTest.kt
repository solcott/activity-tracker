package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.index.HandleSweep
import io.github.solcott.activitytracker.appview.index.HandleVerifier
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/** identity/handle-verification: handles are checked again once their check is due. */
class HandleSweepTest {
    private val alice = "did:plc:alice"
    private val bob = "did:plc:bob"
    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val clock = MutableClock(Clock.System.now())
    private val identities =
        FakeIdentities().apply {
            claims[alice] = "alice.example.com"
            handles["alice.example.com"] = alice
            claims[bob] = "bob.example.com"
            handles["bob.example.com"] = bob
        }
    private val indexer = Indexer(db, HandleVerifier(identities.resolver), clock)
    private val sweep = HandleSweep(db, indexer, clock)

    private suspend fun profile(did: String) =
        indexer.handle(
            JetstreamEvent.Commit(
                did = did,
                time = "2026-10-26T12:00:00.000000Z",
                operation = "create",
                collection = Nsid.ACTOR_PROFILE,
                rkey = "self",
                record = AtJson.parseToJsonElement("""{"displayName":"x"}""").jsonObject,
                cid = "bafy${did.last()}",
            )
        )

    private fun stored(did: String) =
        transaction(db) { Actors.selectAll().where { Actors.did eq did }.single().let { it[Actors.handle] to it[Actors.handleCheckDueAt] } }

    @Test
    fun dueAccountsAreCheckedAgainAndOthersAreNot() = runTest {
        profile(alice)
        profile(bob)
        // Both change their handle without an event; only alice is due.
        identities.claims[alice] = "alice.new.example"
        identities.handles["alice.new.example"] = alice
        identities.claims[bob] = "bob.new.example"
        identities.handles["bob.new.example"] = bob
        transaction(db) { Actors.update({ Actors.did eq alice }) { it[handleCheckDueAt] = clock.now.toEpochMilliseconds() } }

        sweep.runDue()
        assertEquals("alice.new.example" to (clock.now + 24.hours).toEpochMilliseconds(), stored(alice))
        assertEquals("bob.example.com", stored(bob).first)

        clock.now += 25.hours
        sweep.runDue()
        assertEquals("bob.new.example", stored(bob).first)
    }

    @Test
    fun rowsFromBeforeHandleChecksAreDueAtOnce() = runTest {
        profile(alice)
        identities.claims[alice] = "bob.example.com"
        transaction(db) { Actors.update({ Actors.did eq alice }) { it[handleCheckDueAt] = null } }
        sweep.runDue()
        assertEquals("handle.invalid", stored(alice).first)
    }

    @Test
    fun anUnreachableCheckIsRetriedInAnHour() = runTest {
        profile(alice)
        identities.lookupsDown = true
        clock.now += 25.hours
        sweep.runDue()
        assertEquals("alice.example.com" to (clock.now + 1.hours).toEpochMilliseconds(), stored(alice))
    }
}
