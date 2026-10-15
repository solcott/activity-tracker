package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.AppViewSigningKey
import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityStreams
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.CommentHides
import io.github.solcott.activitytracker.appview.db.Comments
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.db.Likes
import io.github.solcott.activitytracker.appview.db.RecordShadows
import io.github.solcott.activitytracker.appview.db.SpaceSync
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.appview.sync.SpaceSyncer
import io.github.solcott.activitytracker.atproto.commentHideRkey
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.space.SignedDelegationTokens
import io.github.solcott.activitytracker.atproto.space.SpaceCredentials
import io.github.solcott.activitytracker.atproto.space.SpaceMember
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.NotifySpace
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.Space
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** The AppView as a member and syncer of athletes' tier spaces (`spaces/appview-syncer`), against a fake Spaces PDS. */
class SpaceSyncTest {
    private val service = "did:web:localhost%3A8081"
    private val alice = "did:plc:alice"
    private val bob = "did:plc:bob"
    private val carol = "did:plc:carol"
    private val keys = mapOf(alice to TestAccountKey(), bob to TestAccountKey(), carol to TestAccountKey())

    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val clock = MutableClock(Instant.parse("2026-10-14T12:00:00Z"))
    private val indexer = Indexer(db, { did -> did.value.substringAfterLast(':') + ".test" }, clock)
    private val config = AppViewConfig.fromEnv(emptyMap())
    private val verifier = ServiceAuthVerifier(service, keys = { keys[it.value]?.multibase })
    private val pds = FakeSpacesPds(clock)
    private val key = AppViewSigningKey(TEST_SIGNING_KEY)
    private val credentials =
        SpaceCredentials(
            SignedDelegationTokens(Did(config.serviceDid), key.jwtAlg, key::sign),
            pds.client,
            IdentityResolver(pds.client, "http://plc.test", null),
        )
    private val syncer = SpaceSyncer(db, indexer, SpaceMember(credentials, pds.client), config.syncerService, clock)

    private fun space(did: String, tier: String) = SpaceUri.of(Did(did), Nsid.SPACE_ATHLETE, tier)

    private val aliceFollowers = space(alice, AthleteSpace.FOLLOWERS)

    private fun activity(title: String = "run") =
        """{"${'$'}type":"${Nsid.FEED_ACTIVITY}","sport":"run","title":"$title","startedAt":"2026-10-14T07:00:00Z",""" +
            """"elapsedTime":1800,"createdAt":"2026-10-14T07:31:00Z"}"""

    private fun streams(rkey: String, visibility: String) =
        """{"${'$'}type":"${Nsid.FEED_ACTIVITY_STREAMS}","activity":"at://$alice/${Nsid.FEED_ACTIVITY}/$rkey",""" +
            """"visibility":"$visibility",""" +
            """"channels":["heartRate"],"createdAt":"2026-10-14T07:31:00Z",""" +
            """"data":{"${'$'}type":"blob","ref":{"${'$'}link":"bafkreihwzzyxpay7qndclffr6usq5te3of4fra7yucklue2b5ckxhq7aeq"},""" +
            """"mimeType":"application/octet-stream","size":10}}"""

    private fun like(subjectRkey: String) =
        """{"${'$'}type":"${Nsid.FEED_LIKE}","subject":{"uri":"at://$alice/${Nsid.FEED_ACTIVITY}/$subjectRkey",""" +
            """"cid":"bafyreihwzzyxpay7qndclffr6usq5te3of4fra7yucklue2b5ckxhq7aeq"},"createdAt":"2026-10-14T08:00:00Z"}"""

    private fun comment(subjectRkey: String, audience: String? = null) =
        """{"${'$'}type":"${Nsid.FEED_COMMENT}","subject":{"uri":"at://$alice/${Nsid.FEED_ACTIVITY}/$subjectRkey",""" +
            """"cid":"bafyreihwzzyxpay7qndclffr6usq5te3of4fra7yucklue2b5ckxhq7aeq"},"text":"Nice!",""" +
            audience.let { if (it == null) "" else """"audience":"$it",""" } +
            """"createdAt":"2026-10-14T08:00:00Z"}"""

    /** Alice's hide of bob's comment [commentRkey], and its rkey. */
    private fun hide(commentRkey: String): Pair<String, String> {
        val comment = AtUri("at://$bob/${Nsid.FEED_COMMENT}/$commentRkey")
        val record = """{"${'$'}type":"${Nsid.FEED_COMMENT_HIDE}","subject":"${comment.value}","createdAt":"2026-10-14T08:00:00Z"}"""
        return runBlocking { commentHideRkey(comment) } to record
    }

    private fun runDue() = runBlocking { syncer.runDue(this) }

    /** (author, origin) of each indexed like, by rkey. */
    private fun likes(): Map<String, Pair<String, String>> =
        transaction(db) {
            Likes.selectAll().associate { it[Likes.uri].substringAfterLast('/') to (it[Likes.authorDid] to it[Likes.origin]) }
        }

    /** (origin, audience) of each indexed comment, by rkey. */
    private fun comments(): Map<String, Pair<String, String>> =
        transaction(db) {
            Comments.selectAll().associate { it[Comments.uri].substringAfterLast('/') to (it[Comments.origin] to it[Comments.audience]) }
        }

    /** The (rkey, origin) of each indexed comment hide, by the rkey of the comment it hides. */
    private fun hides(): Map<String, Set<String>> =
        transaction(db) {
            CommentHides.selectAll()
                .groupBy({ it[CommentHides.commentUri].substringAfterLast('/') }, { it[CommentHides.origin] })
                .mapValues { it.value.toSet() }
        }

    /** (visibility, origin) of each indexed activity, by rkey. */
    private fun activities(): Map<String, Pair<String, String>> =
        transaction(db) {
            Activities.selectAll().associate {
                it[Activities.uri].substringAfterLast('/') to (it[Activities.visibility] to it[Activities.origin])
            }
        }

    private fun syncState(space: SpaceUri) =
        transaction(db) { SpaceSync.selectAll().where { SpaceSync.space eq space.value }.singleOrNull() }

    private fun commit(did: String, collection: String, rkey: String, record: String?) = runBlocking {
        indexer.handle(
            JetstreamEvent.Commit(
                did = did,
                time = "2026-10-14T12:00:00.000000Z",
                operation = if (record == null) "delete" else "create",
                collection = collection,
                rkey = rkey,
                record = record?.let { AtJson.parseToJsonElement(it).jsonObject },
                cid = "bafyrepo$rkey",
            )
        )
    }

    private suspend fun HttpClient.notifySpace(space: SpaceUri, caller: String = space.authority.value): HttpResponse =
        post("/xrpc/${NotifySpace.method.nsid}") {
            header(HttpHeaders.Authorization, "Bearer ${keys.getValue(caller).serviceJwt(caller, service, NotifySpace.method.nsid)}")
            contentType(ContentType.Application.Json)
            setBody("""{"space":"${space.value}"}""")
        }

    private suspend fun HttpClient.notifyWrite(
        space: SpaceUri,
        issuer: String? = space.authority.value,
        aud: String = config.syncerService,
        rev: String = pds.rev(space),
    ): HttpResponse =
        post("/xrpc/${Space.NotifyWrite.method.nsid}") {
            issuer?.let {
                header(HttpHeaders.Authorization, "Bearer ${keys.getValue(it).serviceJwt(it, aud, Space.NotifyWrite.method.nsid)}")
            }
            contentType(ContentType.Application.Json)
            setBody(
                """{"space":"${space.value}","repo":"${space.authority.value}","repoRev":"$rev",""" +
                    """"hash":{"${'$'}bytes":"AAAA"},"spaceRev":"$rev"}"""
            )
        }

    private fun ApplicationTestBuilder.start() {
        application { appView(config, db, verifier, syncer = syncer) }
    }

    @Test
    fun announcedSpaceIsJoinedRegisteredAndBackfilled() = testApplication {
        start()
        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf1", activity())
        assertEquals(HttpStatusCode.OK, client.notifySpace(aliceFollowers).status)
        runDue()

        assertEquals(mapOf("3kf1" to (Visibility.FOLLOWERS to aliceFollowers.value)), activities())
        val registration = pds.registrations.single()
        assertEquals(aliceFollowers.value, registration["space"]?.jsonPrimitive?.content)
        assertEquals("$service#activitytracker_space_syncer", registration["service"]?.jsonPrimitive?.content)
        val exchange = pds.requests.first { it.url.encodedPath.endsWith("getSpaceCredential") }
        val delegation = exchange.headers[HttpHeaders.Authorization]!!.removePrefix("Bearer ")
        val claims = AtJson.parseToJsonElement(String(Base64.getUrlDecoder().decode(delegation.split('.')[1]))).jsonObject
        assertEquals(service, claims["iss"]?.jsonPrimitive?.content, "the AppView reads as itself")
        assertEquals(pds.rev(aliceFollowers), syncState(aliceFollowers)!![SpaceSync.repoRev])
    }

    @Test
    fun withoutASigningKeyNoSpaceIsJoined() = testApplication {
        // As main() wires it when APPVIEW_SIGNING_KEY is unset: no key, so no syncer.
        application { appView(config, db, verifier) }
        commit(alice, Nsid.FEED_ACTIVITY, "3kp1", activity("public"))
        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf1", activity())
        assertEquals(HttpStatusCode.OK, client.notifySpace(aliceFollowers).status)
        assertEquals(null, syncState(aliceFollowers))
        assertEquals(emptyList(), pds.requests, "nothing contacted")
        val did = AtJson.parseToJsonElement(client.get("/.well-known/did.json").bodyAsText()).jsonObject
        assertEquals(null, did["verificationMethod"])
        val view = client.get("/xrpc/${GetActivity.method.nsid}?uri=at://$alice/${Nsid.FEED_ACTIVITY}/3kp1")
        assertEquals(HttpStatusCode.OK, view.status, "public views are served as before")
    }

    @Test
    fun announcementsAreCheckedAndIdempotent() = testApplication {
        start()
        val someoneElses = client.notifySpace(aliceFollowers, caller = bob)
        assertEquals(HttpStatusCode.BadRequest, someoneElses.status)
        assertTrue(someoneElses.bodyAsText().contains(NotifySpace.NOT_SPACE_AUTHORITY))
        assertEquals(HttpStatusCode.BadRequest, client.notifySpace(space(alice, AthleteSpace.PUBLIC)).status)
        assertEquals(HttpStatusCode.BadRequest, client.notifySpace(space(alice, "friends")).status)
        assertEquals(HttpStatusCode.BadRequest, client.notifySpace(SpaceUri.of(Did(alice), "app.other.space", "followers")).status)
        assertEquals(null, syncState(aliceFollowers), "nothing joined")

        assertEquals(HttpStatusCode.OK, client.notifySpace(aliceFollowers).status)
        assertEquals(HttpStatusCode.OK, client.notifySpace(aliceFollowers).status)
        assertEquals(1, transaction(db) { SpaceSync.selectAll().count() })
    }

    @Test
    fun onlyActivitiesAndStreamsAreStored() = testApplication {
        start()
        val self = space(alice, AthleteSpace.SELF)
        pds.put(self, Nsid.ACTOR_PRIVATE_DETAILS, "self", """{"details":{"birthDate":"1990-04-01","weight":61500}}""")
        pds.put(self, Nsid.ACTOR_ACTIVITY_DEFAULTS, "self", """{"visibility":"self"}""")
        pds.put(self, Nsid.GRAPH_FOLLOW_DECLINE, bob, """{"subject":"$bob","createdAt":"2026-10-14T07:00:00Z"}""")
        pds.put(self, Nsid.FEED_ACTIVITY, "3ks1", activity())
        pds.put(self, Nsid.FEED_ACTIVITY_STREAMS, "3ks1", streams("3ks1", Visibility.PUBLIC))
        client.notifySpace(self)
        runDue()
        // A later op of each kind, through the pull rather than the backfill.
        pds.put(self, Nsid.ACTOR_PRIVATE_DETAILS, "self", """{"details":{"birthDate":"1991-04-01"}}""")
        client.notifyWrite(self)
        runDue()

        assertEquals(mapOf("3ks1" to (Visibility.SELF to self.value)), activities())
        val streams = transaction(db) { ActivityStreams.selectAll().single() }
        assertEquals(Visibility.SELF, streams[ActivityStreams.visibility], "never wider than the tier")
        assertEquals(0, transaction(db) { RecordShadows.selectAll().count() })
        val actor = transaction(db) { Actors.selectAll().where { Actors.did eq alice }.single() }
        assertEquals(null, actor[Actors.birthDate])
        assertEquals(null, actor[Actors.weightGrams])
    }

    @Test
    fun notificationsPullNewWritesAndDeletes() = testApplication {
        start()
        client.notifySpace(aliceFollowers)
        runDue()
        assertEquals(emptyMap(), activities())

        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf1", activity())
        assertEquals(HttpStatusCode.OK, client.notifyWrite(aliceFollowers).status)
        runDue()
        assertEquals(setOf("3kf1"), activities().keys)
        // The same notification again changes nothing.
        assertEquals(HttpStatusCode.OK, client.notifyWrite(aliceFollowers).status)
        runDue()
        assertEquals(setOf("3kf1"), activities().keys)

        pds.delete(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf1")
        client.notifyWrite(aliceFollowers)
        runDue()
        assertEquals(emptyMap(), activities())
    }

    @Test
    fun aNotificationDuringAPullIsNotLost() = testApplication {
        start()
        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf0", activity())
        client.notifySpace(aliceFollowers)
        runDue()
        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf1", activity())
        pds.duringNextPull = {
            pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf2", activity())
            runBlocking { syncer.notified(aliceFollowers) }
        }
        client.notifyWrite(aliceFollowers)
        runDue()
        assertEquals(setOf("3kf0", "3kf1"), activities().keys, "the pull read the log before 3kf2")
        runDue()
        assertEquals(setOf("3kf0", "3kf1", "3kf2"), activities().keys, "pulled again at once, not at the next reconcile")
    }

    @Test
    fun forgedNotificationsAreRejectedAndFetchNothing() = testApplication {
        start()
        client.notifySpace(aliceFollowers)
        runDue()
        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf1", activity())
        // Not yet due again, so only a valid notification triggers a pull.
        val before = pds.requests.size

        assertEquals(HttpStatusCode.Unauthorized, client.notifyWrite(aliceFollowers, issuer = null).status)
        assertEquals(HttpStatusCode.Unauthorized, client.notifyWrite(aliceFollowers, issuer = bob).status)
        assertEquals(HttpStatusCode.Unauthorized, client.notifyWrite(aliceFollowers, aud = config.managingAppService).status)
        assertEquals(HttpStatusCode.Unauthorized, client.notifyWrite(aliceFollowers, aud = service).status)
        runDue()
        assertEquals(before, pds.requests.size, "nothing fetched")
        assertEquals(emptyMap(), activities())
    }

    @Test
    fun reconcilePicksUpWritesWhoseNotificationWasLost() = testApplication {
        start()
        client.notifySpace(aliceFollowers)
        runDue()
        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf1", activity())
        runDue()
        assertEquals(emptyMap(), activities(), "not due yet")
        clock.now += 15.minutes
        runDue()
        assertEquals(setOf("3kf1"), activities().keys)
    }

    @Test
    fun aSpaceThatHasNotSyncedForAWhileIsBackfilledAgain() = testApplication {
        start()
        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf1", activity())
        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf2", activity())
        client.notifySpace(aliceFollowers)
        runDue()
        assertEquals(setOf("3kf1", "3kf2"), activities().keys)

        // While the AppView is away, 3kf1 is deleted and the op log compacted past the delete.
        pds.delete(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf1")
        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf3", activity())
        pds.compactedTo[aliceFollowers] = pds.rev(aliceFollowers)
        clock.now += 2.hours
        runDue()
        assertEquals(setOf("3kf2", "3kf3"), activities().keys)
    }

    @Test
    fun registrationIsRenewedBeforeItExpires() = testApplication {
        start()
        client.notifySpace(aliceFollowers)
        runDue()
        assertEquals(1, pds.registrations.size)
        clock.now += 15.minutes
        runDue()
        assertEquals(1, pds.registrations.size, "24 h left")
        clock.now += 22.hours
        runDue()
        assertEquals(2, pds.registrations.size, "within 2 h of expiring")
    }

    @Test
    fun deletedSpaceIsDroppedWithItsActivities() = testApplication {
        start()
        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf1", activity())
        client.notifySpace(aliceFollowers)
        runDue()
        assertEquals(setOf("3kf1"), activities().keys)

        pds.deleted += aliceFollowers
        clock.now += 15.minutes
        // The credential is still cached; a new one is needed to learn the space is gone.
        credentials.invalidate(aliceFollowers)
        runDue()
        assertEquals(emptyMap(), activities())
        assertEquals(null, syncState(aliceFollowers))
    }

    @Test
    fun unreachablePdsDoesNotHoldUpOtherSpaces() = testApplication {
        start()
        val carolMutuals = space(carol, AthleteSpace.MUTUALS)
        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf1", activity())
        pds.put(carolMutuals, Nsid.FEED_ACTIVITY, "3kc1", activity())
        pds.unreachable += Did(alice)
        client.notifySpace(aliceFollowers)
        client.notifySpace(carolMutuals)
        runDue()

        assertEquals(setOf("3kc1"), activities().keys)
        val failed = syncState(aliceFollowers)!!
        assertEquals(1, failed[SpaceSync.failures])
        assertTrue(failed[SpaceSync.nextAttemptAt] > clock.now.toEpochMilliseconds(), "backed off")

        pds.unreachable.clear()
        clock.now += 1.hours
        runDue()
        assertEquals(setOf("3kc1", "3kf1"), activities().keys)
        assertEquals(0, syncState(aliceFollowers)!![SpaceSync.failures])
    }

    @Test
    fun narrowerCopyWinsMidMoveAndTheOtherTakesOver() = testApplication {
        start()
        client.notifySpace(aliceFollowers)
        runDue()

        // Public → Followers: the followers copy is written first, then the repo copy deleted.
        commit(alice, Nsid.FEED_ACTIVITY, "3km1", activity("public"))
        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3km1", activity("followers"))
        client.notifyWrite(aliceFollowers)
        runDue()
        assertEquals(mapOf("3km1" to (Visibility.FOLLOWERS to aliceFollowers.value)), activities(), "briefly in two places: the narrower")
        commit(alice, Nsid.FEED_ACTIVITY, "3km1", null)
        assertEquals(mapOf("3km1" to (Visibility.FOLLOWERS to aliceFollowers.value)), activities())
        assertEquals(0, transaction(db) { RecordShadows.selectAll().count() })

        // Followers → Public: the repo copy arrives while the followers one still holds the row, then the followers one is deleted.
        commit(alice, Nsid.FEED_ACTIVITY, "3km1", activity("public again"))
        assertEquals(mapOf("3km1" to (Visibility.FOLLOWERS to aliceFollowers.value)), activities())
        pds.delete(aliceFollowers, Nsid.FEED_ACTIVITY, "3km1")
        client.notifyWrite(aliceFollowers)
        runDue()
        assertEquals(mapOf("3km1" to (Visibility.PUBLIC to "repo")), activities(), "the repo copy took over")
        val record = transaction(db) { Activities.selectAll().single()[Activities.record] }
        assertTrue(record.contains("public again"))
        assertEquals(0, transaction(db) { RecordShadows.selectAll().count() })
    }

    @Test
    fun interactionsSpaceIsSyncedForItsLikesOnly() = testApplication {
        start()
        val bobInteractions = space(bob, AthleteSpace.INTERACTIONS)
        pds.put(bobInteractions, Nsid.FEED_LIKE, "3kl1", like("3kf1"))
        pds.put(bobInteractions, Nsid.FEED_ACTIVITY, "3kb1", activity())
        assertEquals(HttpStatusCode.OK, client.notifySpace(bobInteractions).status)
        runDue()
        assertEquals(mapOf("3kl1" to (bob to bobInteractions.value)), likes())
        assertEquals(emptyMap(), activities(), "an activity in an interactions space is ignored")
        assertEquals(bobInteractions.value, pds.registrations.single()["space"]?.jsonPrimitive?.content)

        pds.put(bobInteractions, Nsid.FEED_LIKE, "3kl2", like("3kf2"))
        client.notifyWrite(bobInteractions)
        runDue()
        // The same notification again changes nothing.
        client.notifyWrite(bobInteractions)
        runDue()
        assertEquals(setOf("3kl1", "3kl2"), likes().keys)

        pds.delete(bobInteractions, Nsid.FEED_LIKE, "3kl1")
        client.notifyWrite(bobInteractions)
        runDue()
        assertEquals(setOf("3kl2"), likes().keys)
    }

    @Test
    fun someoneElsesInteractionsSpaceIsRejected() = testApplication {
        start()
        val bobInteractions = space(bob, AthleteSpace.INTERACTIONS)
        val response = client.notifySpace(bobInteractions, caller = alice)
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains(NotifySpace.NOT_SPACE_AUTHORITY))
        assertEquals(null, syncState(bobInteractions), "nothing joined")
    }

    @Test
    fun likesInATierSpaceAreIgnored() = testApplication {
        start()
        val bobFollowers = space(bob, AthleteSpace.FOLLOWERS)
        pds.put(bobFollowers, Nsid.FEED_LIKE, "3kl1", like("3kf1"))
        client.notifySpace(bobFollowers)
        runDue()
        pds.put(bobFollowers, Nsid.FEED_LIKE, "3kl2", like("3kf2"))
        client.notifyWrite(bobFollowers)
        runDue()
        assertEquals(emptyMap(), likes())
    }

    @Test
    fun deletedInteractionsSpaceDropsOnlyItsLikes() = testApplication {
        start()
        val bobInteractions = space(bob, AthleteSpace.INTERACTIONS)
        commit(bob, Nsid.FEED_LIKE, "3kr1", like("3kp1"))
        pds.put(bobInteractions, Nsid.FEED_LIKE, "3kl1", like("3kf1"))
        client.notifySpace(bobInteractions)
        runDue()
        assertEquals(mapOf("3kr1" to (bob to "repo"), "3kl1" to (bob to bobInteractions.value)), likes())
        // A delete from the other place never removes a like.
        commit(bob, Nsid.FEED_LIKE, "3kl1", null)
        assertEquals(setOf("3kr1", "3kl1"), likes().keys)

        pds.deleted += bobInteractions
        clock.now += 15.minutes
        credentials.invalidate(bobInteractions)
        runDue()
        assertEquals(mapOf("3kr1" to (bob to "repo")), likes(), "bob's repo like on a public activity stays")
        assertEquals(null, syncState(bobInteractions))
    }

    @Test
    fun interactionsSpaceIsSyncedForItsComments() = testApplication {
        start()
        val bobInteractions = space(bob, AthleteSpace.INTERACTIONS)
        // Already there when the space is first synced: the backfill picks it up.
        pds.put(bobInteractions, Nsid.FEED_COMMENT, "3kc1", comment("3kf1", Visibility.FOLLOWERS))
        client.notifySpace(bobInteractions)
        runDue()
        assertEquals(mapOf("3kc1" to (bobInteractions.value to Visibility.FOLLOWERS)), comments())

        pds.put(bobInteractions, Nsid.FEED_COMMENT, "3kc2", comment("3kf2", "friends"))
        pds.put(bobInteractions, Nsid.FEED_COMMENT, "3kc3", comment("3kf3"))
        client.notifyWrite(bobInteractions)
        runDue()
        // The same notification again changes nothing.
        client.notifyWrite(bobInteractions)
        runDue()
        assertEquals(
            mapOf(
                "3kc1" to (bobInteractions.value to Visibility.FOLLOWERS),
                "3kc2" to (bobInteractions.value to Visibility.SELF),
                "3kc3" to (bobInteractions.value to Visibility.PUBLIC),
            ),
            comments(),
            "an unknown audience is the athlete's only, and none is public",
        )

        // A delete of the same rkey from the repo, where it isn't, leaves it.
        commit(bob, Nsid.FEED_COMMENT, "3kc1", null)
        assertEquals(setOf("3kc1", "3kc2", "3kc3"), comments().keys)
        pds.delete(bobInteractions, Nsid.FEED_COMMENT, "3kc1")
        client.notifyWrite(bobInteractions)
        runDue()
        assertEquals(setOf("3kc2", "3kc3"), comments().keys)

        // A comment deleted while the space went unsynced long enough to be backfilled again is dropped then.
        pds.delete(bobInteractions, Nsid.FEED_COMMENT, "3kc2")
        clock.now += 2.hours
        credentials.invalidate(bobInteractions)
        runDue()
        assertEquals(setOf("3kc3"), comments().keys)
    }

    @Test
    fun commentsInATierSpaceAreIgnored() = testApplication {
        start()
        val bobFollowers = space(bob, AthleteSpace.FOLLOWERS)
        pds.put(bobFollowers, Nsid.FEED_COMMENT, "3kc1", comment("3kf1", Visibility.FOLLOWERS))
        client.notifySpace(bobFollowers)
        runDue()
        pds.put(bobFollowers, Nsid.FEED_COMMENT, "3kc2", comment("3kf2", Visibility.FOLLOWERS))
        client.notifyWrite(bobFollowers)
        runDue()
        assertEquals(emptyMap(), comments())
    }

    @Test
    fun deletedInteractionsSpaceDropsOnlyItsComments() = testApplication {
        start()
        val bobInteractions = space(bob, AthleteSpace.INTERACTIONS)
        commit(bob, Nsid.FEED_COMMENT, "3kr1", comment("3kp1"))
        pds.put(bobInteractions, Nsid.FEED_COMMENT, "3kc1", comment("3kf1", Visibility.MUTUALS))
        client.notifySpace(bobInteractions)
        runDue()
        assertEquals(
            mapOf("3kr1" to ("repo" to Visibility.PUBLIC), "3kc1" to (bobInteractions.value to Visibility.MUTUALS)),
            comments(),
        )

        pds.deleted += bobInteractions
        clock.now += 15.minutes
        credentials.invalidate(bobInteractions)
        runDue()
        assertEquals(mapOf("3kr1" to ("repo" to Visibility.PUBLIC)), comments(), "bob's repo comment on a public activity stays")
    }

    @Test
    fun interactionsSpaceIsSyncedForItsCommentHides() = testApplication {
        start()
        val aliceInteractions = space(alice, AthleteSpace.INTERACTIONS)
        val (rkey1, hide1) = hide("3kc1")
        val (rkey2, hide2) = hide("3kc2")
        // Already there when the space is first synced: the backfill picks it up, with the space's likes and comments.
        pds.put(aliceInteractions, Nsid.FEED_COMMENT_HIDE, rkey1, hide1)
        client.notifySpace(aliceInteractions)
        runDue()
        assertEquals(mapOf("3kc1" to setOf(aliceInteractions.value)), hides())

        pds.put(aliceInteractions, Nsid.FEED_COMMENT_HIDE, rkey2, hide2)
        client.notifyWrite(aliceInteractions)
        runDue()
        assertEquals(setOf("3kc1", "3kc2"), hides().keys)

        // A delete of the same rkey from the repo, where it isn't, leaves it.
        commit(alice, Nsid.FEED_COMMENT_HIDE, rkey1, null)
        assertEquals(setOf("3kc1", "3kc2"), hides().keys)
        pds.delete(aliceInteractions, Nsid.FEED_COMMENT_HIDE, rkey1)
        client.notifyWrite(aliceInteractions)
        runDue()
        assertEquals(setOf("3kc2"), hides().keys)

        // A hide deleted while the space went unsynced long enough to be backfilled again is dropped then.
        pds.delete(aliceInteractions, Nsid.FEED_COMMENT_HIDE, rkey2)
        clock.now += 2.hours
        credentials.invalidate(aliceInteractions)
        runDue()
        assertEquals(emptyMap(), hides())
    }

    @Test
    fun commentHidesInATierSpaceAreIgnored() = testApplication {
        start()
        val (rkey, hide) = hide("3kc1")
        pds.put(aliceFollowers, Nsid.FEED_COMMENT_HIDE, rkey, hide)
        client.notifySpace(aliceFollowers)
        runDue()
        assertEquals(emptyMap(), hides())
    }

    @Test
    fun deletedInteractionsSpaceDropsOnlyItsCommentHides() = testApplication {
        start()
        val aliceInteractions = space(alice, AthleteSpace.INTERACTIONS)
        val (repoRkey, repoHide) = hide("3kr1")
        val (spaceRkey, spaceHide) = hide("3kc1")
        commit(alice, Nsid.FEED_COMMENT_HIDE, repoRkey, repoHide)
        pds.put(aliceInteractions, Nsid.FEED_COMMENT_HIDE, spaceRkey, spaceHide)
        client.notifySpace(aliceInteractions)
        runDue()
        assertEquals(mapOf("3kr1" to setOf("repo"), "3kc1" to setOf(aliceInteractions.value)), hides())

        pds.deleted += aliceInteractions
        clock.now += 15.minutes
        credentials.invalidate(aliceInteractions)
        runDue()
        assertEquals(mapOf("3kr1" to setOf("repo")), hides(), "alice's repo hide stays")
    }

    @Test
    fun selfLikesInAnInteractionsSpaceAreRejected() = testApplication {
        start()
        val aliceInteractions = space(alice, AthleteSpace.INTERACTIONS)
        pds.put(aliceInteractions, Nsid.FEED_LIKE, "3kl1", like("3kf1"))
        client.notifySpace(aliceInteractions)
        runDue()
        assertEquals(emptyMap(), likes())
    }
}

class MutableClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}
