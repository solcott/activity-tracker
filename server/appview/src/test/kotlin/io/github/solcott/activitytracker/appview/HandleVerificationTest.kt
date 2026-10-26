package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.index.HandleVerifier
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetProfile
import io.github.solcott.activitytracker.lexicon.app.SearchActors
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** identity/handle-verification end to end: what the indexer stores for each check, and who lookups by handle find. */
class HandleVerificationTest {
    private val service = "did:web:localhost%3A8081"
    private val alice = "did:plc:alice"
    private val bob = "did:plc:bob"
    private val mallory = "did:plc:mallory"
    private val keys = mapOf(alice to TestAccountKey())
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
    private val config = AppViewConfig.fromEnv(emptyMap())
    private val verifier = ServiceAuthVerifier(service, keys = { keys[it.value]?.multibase })

    private fun profile(did: String, name: String) = runBlocking {
        indexer.handle(
            JetstreamEvent.Commit(
                did = did,
                time = "2026-10-26T12:00:00.000000Z",
                operation = "create",
                collection = Nsid.ACTOR_PROFILE,
                rkey = "self",
                record = AtJson.parseToJsonElement("""{"displayName":"$name"}""").jsonObject,
                cid = "bafy$name",
            )
        )
    }

    private fun identityEvent(did: String, handle: String?) = runBlocking {
        indexer.handle(JetstreamEvent.Identity(did, "2026-10-26T12:00:00.000000Z", identity = JetstreamEvent.Identity.Body(did, handle)))
    }

    private fun stored(did: String) =
        transaction(db) { Actors.selectAll().where { Actors.did eq did }.single().let { it[Actors.handle] to it[Actors.handleCheckDueAt] } }

    private suspend fun HttpClient.profileHandle(actor: String): String? {
        val response = get("/xrpc/${GetProfile.method.nsid}?actor=$actor")
        if (response.status != HttpStatusCode.OK) {
            assertTrue(response.bodyAsText().contains("ProfileNotFound"), response.bodyAsText())
            return null
        }
        return AtJson.decodeFromString(GetProfile.method.output, response.bodyAsText()).handle.value
    }

    private suspend fun HttpClient.search(q: String): List<String> {
        val nsid = SearchActors.method.nsid
        val response =
            get("/xrpc/$nsid?q=$q") { header(HttpHeaders.Authorization, "Bearer ${keys.getValue(alice).serviceJwt(alice, service, nsid)}") }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return AtJson.decodeFromString(SearchActors.Output.serializer(), response.bodyAsText()).actors.map {
            "${it.did.value} ${it.handle}"
        }
    }

    @Test
    fun anImpersonatingDidIsStoredAndShownAsHandleInvalid() = testApplication {
        application { appView(config, db, verifier) }
        identities.claims[mallory] = "alice.example.com"
        profile(alice, "Alice")
        profile(mallory, "Mallory")

        assertEquals("alice.example.com", stored(alice).first)
        assertEquals("handle.invalid", stored(mallory).first)
        assertEquals("alice.example.com", client.profileHandle("alice.example.com"))
        assertEquals("handle.invalid", client.profileHandle(mallory), "found by DID")
        assertEquals(null, client.profileHandle("handle.invalid"))
        assertEquals(listOf("$alice alice.example.com"), client.search("alice"))
        assertEquals(listOf("$mallory handle.invalid"), client.search("mallory"), "found by display name")
        assertEquals(emptyList(), client.search("handle.invalid"))
    }

    @Test
    fun aHandleNoOneCanBeFoundUnderIsntFound() = testApplication {
        application { appView(config, db, verifier) }
        identities.claims.remove(alice)
        identities.claims[mallory] = "alice.example.com"
        profile(mallory, "Mallory")
        assertEquals(null, client.profileHandle("alice.example.com"))
    }

    @Test
    fun anIdentityEventsHandleIsNotTrusted() {
        profile(alice, "Alice")
        identityEvent(alice, "bob.example.com")
        assertEquals("alice.example.com", stored(alice).first)

        // A real handle change is picked up from the DID document.
        identities.claims[alice] = "alice.new.example"
        identities.handles["alice.new.example"] = alice
        identityEvent(alice, null)
        assertEquals("alice.new.example", stored(alice).first)

        // An event for an account the AppView has never indexed stores nothing.
        identityEvent(bob, "bob.example.com")
        assertEquals(0L, transaction(db) { Actors.selectAll().where { Actors.did eq bob }.count() })
    }

    @Test
    fun anUnreachableRecheckKeepsTheLastVerifiedHandle() {
        profile(alice, "Alice")
        assertEquals("alice.example.com" to (clock.now + 24.hours).toEpochMilliseconds(), stored(alice))
        identities.lookupsDown = true
        identityEvent(alice, null)
        assertEquals("alice.example.com" to (clock.now + 1.hours).toEpochMilliseconds(), stored(alice))
    }

    @Test
    fun aNewAccountWhoseCheckIsUnreachableIsHandleInvalidUntilACheckSucceeds() {
        identities.lookupsDown = true
        profile(alice, "Alice")
        assertEquals("handle.invalid" to (clock.now + 1.hours).toEpochMilliseconds(), stored(alice))
        identities.lookupsDown = false
        identityEvent(alice, null)
        assertEquals("alice.example.com", stored(alice).first)
    }
}
