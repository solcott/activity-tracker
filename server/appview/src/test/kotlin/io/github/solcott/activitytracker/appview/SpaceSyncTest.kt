package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityStreams
import io.github.solcott.activitytracker.appview.db.ActivityZones
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.Blocks
import io.github.solcott.activitytracker.appview.db.Mutes
import io.github.solcott.activitytracker.appview.db.RecordShadows
import io.github.solcott.activitytracker.appview.db.SpaceSync
import io.github.solcott.activitytracker.atproto.space.SpaceMac
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.NotifySpace
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** The AppView as a member and syncer of athletes' tier spaces (`spaces/appview-syncer`), against a fake Spaces PDS. */
class SpaceSyncTest : SpaceSyncFixture() {
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
        assertEquals(null, exchange.headers[HttpHeaders.Authorization], "the delegation travels in the body")
        val delegation = exchange.jsonBody()["delegationToken"]!!.jsonPrimitive.content
        val (header, payload) =
            delegation.split('.').take(2).map { AtJson.parseToJsonElement(String(Base64.getUrlDecoder().decode(it))).jsonObject }
        assertEquals(service, payload["iss"]?.jsonPrimitive?.content, "the AppView reads as itself")
        assertEquals("$alice#atproto_pds", payload["aud"]?.jsonPrimitive?.content)
        assertEquals("#atproto", header["kid"]?.jsonPrimitive?.content)
        assertEquals(pds.rev(aliceFollowers), syncState(aliceFollowers)!![SpaceSync.repoRev])
        val pulls = pds.requests.filter { it.url.encodedPath.endsWith("listRepoOps") }
        assertTrue(pulls.isNotEmpty())
        val shares = pulls.map { it.headers[SpaceMac.WANT_MAC_HEADER].orEmpty() }
        assertTrue(shares.all { it.startsWith("""type="atproto-space-repo", ver=1, key_share="did:key:z""") }, shares.toString())
        assertEquals(shares.size, shares.toSet().size, "a fresh key share per page")
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
    fun onlyActivitiesStreamsAndZonesAreStored() = testApplication {
        start()
        val self = space(alice, AthleteSpace.SELF)
        pds.put(self, Nsid.ACTOR_PRIVATE_DETAILS, "self", """{"details":{"birthDate":"1990-04-01","weight":61500}}""")
        pds.put(self, Nsid.ACTOR_ACTIVITY_DEFAULTS, "self", """{"visibility":"self"}""")
        pds.put(self, Nsid.GRAPH_FOLLOW_DECLINE, bob, """{"subject":"$bob","createdAt":"2026-10-14T07:00:00Z"}""")
        pds.put(self, Nsid.FEED_ACTIVITY, "3ks1", activity())
        pds.put(self, Nsid.FEED_ACTIVITY_STREAMS, "3ks1", streams("3ks1", Visibility.PUBLIC))
        pds.put(
            self,
            Nsid.FEED_ACTIVITY_ZONES,
            "3ks1",
            """{"activity":"at://$alice/${Nsid.FEED_ACTIVITY}/3ks1","powerZones":{"model":"coggan6","lows":[0,138]},""" +
                """"createdAt":"2026-10-14T07:00:00Z"}""",
        )
        client.notifySpace(self)
        runDue()
        // A later op of each kind, through the pull rather than the backfill.
        pds.put(self, Nsid.ACTOR_PRIVATE_DETAILS, "self", """{"details":{"birthDate":"1991-04-01"}}""")
        client.notifyWrite(self)
        runDue()

        assertEquals(mapOf("3ks1" to (Visibility.SELF to self.value)), activities())
        val streams = transaction(db) { ActivityStreams.selectAll().single() }
        assertEquals(Visibility.SELF, streams[ActivityStreams.visibility], "never wider than the tier")
        assertEquals(Visibility.SELF, transaction(db) { ActivityZones.selectAll().single()[ActivityZones.visibility] })
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
        assertEquals(HttpStatusCode.Unauthorized, client.notifyWrite(aliceFollowers, lxm = null).status)
        assertEquals(HttpStatusCode.Unauthorized, client.notifyWrite(aliceFollowers, lxm = NotifySpace.method.nsid).status)
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
    fun deletionNotificationDropsTheSpaceAtOnce() = testApplication {
        start()
        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf1", activity())
        client.notifySpace(aliceFollowers)
        val bobInteractions = space(bob, AthleteSpace.INTERACTIONS)
        pds.put(bobInteractions, Nsid.FEED_LIKE, "3kl1", like("3kf1"))
        pds.put(bobInteractions, Nsid.FEED_COMMENT, "3kc1", comment("3kf1", Visibility.FOLLOWERS))
        client.notifySpace(bobInteractions)
        runDue()
        assertEquals(setOf("3kf1"), activities().keys)
        assertEquals(setOf("3kc1"), comments().keys)

        pds.deleted += aliceFollowers
        // No sync runs: the notification alone drops the space, before it's answered.
        assertEquals(HttpStatusCode.OK, client.notifySpaceDeleted(aliceFollowers).status)
        assertEquals(emptyMap(), activities())
        assertEquals(null, syncState(aliceFollowers))
        assertEquals(setOf("3kc1"), comments().keys, "bob's space is untouched")
        // A repeat, and a space the AppView never synced, succeed with nothing to do.
        assertEquals(HttpStatusCode.OK, client.notifySpaceDeleted(aliceFollowers).status)
        assertEquals(HttpStatusCode.OK, client.notifySpaceDeleted(space(alice, AthleteSpace.MUTUALS)).status)
        assertEquals(setOf(bobInteractions.value), transaction(db) { SpaceSync.selectAll().map { it[SpaceSync.space] }.toSet() })
    }

    @Test
    fun forgedDeletionNotificationsAreRejected() = testApplication {
        start()
        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf1", activity())
        client.notifySpace(aliceFollowers)
        runDue()

        assertEquals(HttpStatusCode.Unauthorized, client.notifySpaceDeleted(aliceFollowers, issuer = null).status)
        assertEquals(HttpStatusCode.Unauthorized, client.notifySpaceDeleted(aliceFollowers, issuer = bob).status)
        assertEquals(HttpStatusCode.Unauthorized, client.notifySpaceDeleted(aliceFollowers, aud = config.managingAppService).status)
        assertEquals(HttpStatusCode.Unauthorized, client.notifySpaceDeleted(aliceFollowers, aud = service).status)
        assertEquals(setOf("3kf1"), activities().keys)
        assertTrue(syncState(aliceFollowers) != null, "still synced")
    }

    @Test
    fun aDeletionDuringAPullLeavesNothingBehind() = testApplication {
        start()
        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf1", activity())
        client.notifySpace(aliceFollowers)
        runDue()
        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf2", activity())
        var purge: Job? = null
        pds.duringNextPull = {
            // Runs until it waits on the space's lock, which the pull holds, so it purges only after the pull has indexed 3kf2.
            purge = CoroutineScope(Job()).launch(start = CoroutineStart.UNDISPATCHED) { syncer.deleted(aliceFollowers) }
        }
        client.notifyWrite(aliceFollowers)
        runDue()
        runBlocking { purge!!.join() }
        assertEquals(emptyMap(), activities())
        assertEquals(null, syncState(aliceFollowers))
    }

    @Test
    fun aRecreatedSpaceIsJoinedAsNew() = testApplication {
        start()
        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf1", activity())
        client.notifySpace(aliceFollowers)
        runDue()
        client.notifySpaceDeleted(aliceFollowers)
        val registrations = pds.registrations.size

        // Alice deletes the space and creates it again at the same URI, holding only a new activity.
        pds.delete(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf1")
        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf2", activity())
        credentials.invalidate(aliceFollowers)
        client.notifySpace(aliceFollowers)
        runDue()
        assertEquals(setOf("3kf2"), activities().keys)
        assertEquals(registrations + 1, pds.registrations.size, "registered again")
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
    fun blocksAndMutesAreSyncedFromTheInteractionsSpace() = testApplication {
        start()
        val bobInteractions = space(bob, AthleteSpace.INTERACTIONS)
        fun graph(collection: String, subject: String) =
            """{"${'$'}type":"$collection","subject":"$subject","createdAt":"2026-10-14T08:00:00Z"}"""
        pds.put(bobInteractions, Nsid.GRAPH_BLOCK, alice, graph(Nsid.GRAPH_BLOCK, alice))
        pds.put(bobInteractions, Nsid.GRAPH_BLOCK, "3kwrong", graph(Nsid.GRAPH_BLOCK, carol))
        pds.put(bobInteractions, Nsid.GRAPH_MUTE, carol, graph(Nsid.GRAPH_MUTE, carol))
        pds.put(aliceFollowers, Nsid.GRAPH_BLOCK, bob, graph(Nsid.GRAPH_BLOCK, bob))
        client.notifySpace(bobInteractions)
        client.notifySpace(aliceFollowers)
        runDue()
        fun blocks() = transaction(db) { Blocks.selectAll().map { it[Blocks.authorDid] to it[Blocks.subjectDid] } }
        fun mutes() = transaction(db) { Mutes.selectAll().map { it[Mutes.authorDid] to it[Mutes.subjectDid] } }
        assertEquals(listOf(bob to alice), blocks(), "only the interactions space's block at its subject's key")
        assertEquals(listOf(bob to carol), mutes())

        // Deleted while the AppView was away, past a compacted op log: a fresh backfill drops it.
        pds.delete(bobInteractions, Nsid.GRAPH_BLOCK, alice)
        pds.delete(bobInteractions, Nsid.GRAPH_MUTE, carol)
        pds.compactedTo[bobInteractions] = pds.rev(bobInteractions)
        clock.now += 2.hours
        runDue()
        assertEquals(emptyList(), blocks())
        assertEquals(emptyList(), mutes())
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
