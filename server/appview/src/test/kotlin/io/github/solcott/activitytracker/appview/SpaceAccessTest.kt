package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.index.HandleLookup
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.appview.labels.LabelStore
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetProfile
import io.github.solcott.activitytracker.lexicon.atproto.ManagedSpace.CheckUserAccess
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database

/** `checkUserAccess`: alice's PDS asks whether a user may read or write one of her tier spaces. */
class SpaceAccessTest {
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
    private val indexer = Indexer(db, HandleLookup { did -> did.value.substringAfterLast(':') + ".test" })
    private val config = AppViewConfig.fromEnv(emptyMap())
    private val verifier = ServiceAuthVerifier(service, keys = { keys[it.value]?.multibase })
    private val nsid = CheckUserAccess.method.nsid

    private fun commit(did: String, collection: String, rkey: String, record: String?, op: String = "create") = runBlocking {
        indexer.handle(
            JetstreamEvent.Commit(
                did = did,
                time = "2026-09-30T12:00:00.000000Z",
                operation = op,
                collection = collection,
                rkey = rkey,
                record = record?.let { AtJson.parseToJsonElement(it).jsonObject },
                cid = "bafy$rkey",
            )
        )
    }

    private fun followRkey(author: String, subject: String) = "3kf${author.last()}${subject.last()}"

    private fun follow(author: String, subject: String, collection: String = Nsid.GRAPH_FOLLOW) =
        commit(author, collection, followRkey(author, subject), """{"subject":"$subject","createdAt":"2026-09-30T09:00:00Z"}""")

    private fun unfollow(author: String, subject: String) = commit(author, Nsid.GRAPH_FOLLOW, followRkey(author, subject), null, "delete")

    private fun approve(author: String, subject: String, rkey: String = subject) =
        commit(author, Nsid.GRAPH_FOLLOW_APPROVAL, rkey, """{"subject":"$subject","createdAt":"2026-09-30T09:00:00Z"}""")

    private fun unapprove(author: String, subject: String) = commit(author, Nsid.GRAPH_FOLLOW_APPROVAL, subject, null, "delete")

    /** [did]'s profile record, with [policy] as its followPolicy. */
    private fun policy(did: String, policy: String?) =
        commit(did, Nsid.ACTOR_PROFILE, "self", """{"displayName":"x"${policy?.let { ""","followPolicy":"$it"""" }.orEmpty()}}""")

    private fun space(tier: String, type: String = Nsid.SPACE_ATHLETE) = SpaceUri.of(Did(alice), type, tier).value

    /** A callout as alice's PDS makes it: signed by [issuer], addressed to [aud]. */
    @Suppress("LongParameterList") // One named default per part of the callout a test varies.
    private suspend fun HttpClient.check(
        user: String,
        tier: String = AthleteSpace.MUTUALS,
        access: String? = CheckUserAccess.READ,
        space: String = space(tier),
        issuer: String? = alice,
        aud: String = config.managingAppService,
        clientId: String? = null,
        lxm: String? = nsid,
    ): HttpResponse =
        get("/xrpc/$nsid") {
            parameter("space", space)
            parameter("user", user)
            access?.let { parameter("access", it) }
            clientId?.let { parameter("clientId", it) }
            issuer?.let { header(HttpHeaders.Authorization, "Bearer ${keys.getValue(it).serviceJwt(it, aud, lxm)}") }
        }

    @Suppress("LongParameterList") // As check.
    private suspend fun HttpClient.authorized(
        user: String,
        tier: String = AthleteSpace.MUTUALS,
        access: String = CheckUserAccess.READ,
        space: String = space(tier),
        issuer: String = alice,
        clientId: String? = null,
    ): Boolean {
        val response = check(user, tier, access, space, issuer, clientId = clientId)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return AtJson.decodeFromString(CheckUserAccess.method.output, response.bodyAsText()).authorized
    }

    private fun ApplicationTestBuilder.start() {
        application { appView(config, db, verifier) }
    }

    /** [author] blocks [subject] with a `graph.block` in their interactions space, or with [remove] deletes it. */
    private fun block(author: String, subject: String, remove: Boolean = false) = runBlocking {
        val interactions = SpaceUri.of(Did(author), Nsid.SPACE_ATHLETE, AthleteSpace.INTERACTIONS)
        if (remove) {
            indexer.spaceDelete(interactions, Nsid.GRAPH_BLOCK, subject)
        } else {
            val json = """{"subject":"$subject","createdAt":"2026-10-21T09:00:00Z"}"""
            indexer.spaceWrite(interactions, Nsid.GRAPH_BLOCK, subject, "bafyb", AtJson.parseToJsonElement(json).jsonObject)
        }
    }

    @Test
    fun aTakenDownAccountsTiersAdmitNoOneUntilNegated() = testApplication {
        val labeler = "did:plc:labeler"
        application { appView(config.copy(labeler = AppViewConfig.Labeler(labeler, "http://labeler.test")), db, verifier) }
        follow(bob, alice)
        assertEquals(true, client.authorized(bob, AthleteSpace.FOLLOWERS))
        val store = LabelStore(db, labeler, null)
        fun takedown(neg: Boolean, cts: String) = runBlocking {
            store.apply(labeler, 1, listOf(testLabel(labeler, alice, "!takedown", cts, labelExtras(neg = neg))))
        }
        takedown(neg = false, cts = "2026-10-21T10:00:00Z")
        for (tier in listOf(AthleteSpace.PUBLIC, AthleteSpace.FOLLOWERS)) {
            assertEquals(false, client.authorized(bob, tier), "alice is taken down: $tier")
        }
        takedown(neg = true, cts = "2026-10-21T11:00:00Z")
        assertEquals(true, client.authorized(bob, AthleteSpace.FOLLOWERS), "negated: decided by tier again")
    }

    /** [did]'s hosting status, as a Jetstream `#account` event reports it. */
    private fun account(did: String, active: Boolean, status: String? = null) = runBlocking {
        indexer.handle(
            JetstreamEvent.Account(did, "2026-10-21T12:00:00.000000Z", account = JetstreamEvent.Account.Body(did, active, status))
        )
    }

    @Test
    fun anInactiveAccountsTiersAdmitOnlyTheAppViewUntilReactivated() = testApplication {
        start()
        policy(alice, null) // A status is kept only for an account with a row.
        follow(bob, alice)
        account(alice, active = false, status = "suspended")
        assertEquals(false, client.authorized(bob, AthleteSpace.FOLLOWERS), "alice is suspended")
        for (tier in listOf(AthleteSpace.PUBLIC, AthleteSpace.FOLLOWERS, AthleteSpace.MUTUALS, AthleteSpace.SELF)) {
            assertEquals(true, client.authorized(config.serviceDid, tier), "the AppView keeps syncing: $tier")
        }
        assertEquals(true, client.authorized(config.serviceDid, AthleteSpace.INTERACTIONS))
        account(alice, active = false, status = "deactivated")
        assertEquals(false, client.authorized(carol, AthleteSpace.PUBLIC), "alice is deactivated")
        account(alice, active = true)
        assertEquals(true, client.authorized(bob, AthleteSpace.FOLLOWERS), "reactivated: decided by tier again")
    }

    @Test
    fun anInactiveReaderIsDecidedByTier() = testApplication {
        start()
        policy(bob, null)
        follow(bob, alice)
        account(bob, active = false, status = "suspended")
        assertEquals(true, client.authorized(bob, AthleteSpace.FOLLOWERS))
    }

    @Test
    fun blockedUserIsDeniedEveryTier() = testApplication {
        start()
        follow(bob, alice)
        follow(alice, bob)
        assertEquals(true, client.authorized(bob, AthleteSpace.MUTUALS))
        block(alice, bob)
        for (tier in listOf(AthleteSpace.PUBLIC, AthleteSpace.FOLLOWERS, AthleteSpace.MUTUALS)) {
            assertEquals(false, client.authorized(bob, tier), "alice blocks bob: $tier")
        }
        block(alice, bob, remove = true)
        assertEquals(true, client.authorized(bob, AthleteSpace.MUTUALS), "unblocked: decided by tier again")
        assertEquals(true, client.authorized(bob, AthleteSpace.PUBLIC))
    }

    @Test
    fun blockByTheUserOrOnBlueskyAlsoDenies() = testApplication {
        start()
        follow(bob, alice)
        block(bob, alice)
        assertEquals(false, client.authorized(bob, AthleteSpace.FOLLOWERS), "bob blocks alice")
        assertEquals(false, client.authorized(bob, AthleteSpace.PUBLIC))
        block(bob, alice, remove = true)
        assertEquals(true, client.authorized(bob, AthleteSpace.FOLLOWERS))

        policy(alice, null) // Only an athlete's Bluesky blocks are kept.
        commit(alice, Nsid.BSKY_GRAPH_BLOCK, "3kbsky", """{"subject":"$bob","createdAt":"2026-01-01T00:00:00Z"}""")
        assertEquals(false, client.authorized(bob, AthleteSpace.FOLLOWERS), "alice blocks bob on Bluesky")
        commit(alice, Nsid.BSKY_GRAPH_BLOCK, "3kbsky", null, "delete")
        assertEquals(true, client.authorized(bob, AthleteSpace.FOLLOWERS))
    }

    @Test
    fun publicTierAdmitsAnyone() = testApplication {
        start()
        assertEquals(true, client.authorized(carol, AthleteSpace.PUBLIC))
    }

    @Test
    fun mutualsTierNeedsFollowsBothWays() = testApplication {
        start()
        follow(alice, bob)
        follow(bob, alice)
        follow(carol, alice)
        follow(alice, "did:plc:dave")
        assertEquals(true, client.authorized(bob))
        assertEquals(false, client.authorized(carol), "carol follows alice only")
        assertEquals(false, client.authorized("did:plc:dave"), "alice follows dave only")
    }

    @Test
    fun unfollowEndsAccess() = testApplication {
        start()
        follow(alice, bob)
        follow(bob, alice)
        assertEquals(true, client.authorized(bob))
        unfollow(bob, alice)
        assertEquals(false, client.authorized(bob))
    }

    @Test
    fun blueskyFollowsDontCount() = testApplication {
        start()
        follow(alice, bob, collection = "app.bsky.graph.follow")
        follow(bob, alice, collection = "app.bsky.graph.follow")
        assertEquals(false, client.authorized(bob))
        assertEquals(false, client.authorized(bob, AthleteSpace.FOLLOWERS))
    }

    @Test
    fun followersTierAdmitsEveryFollowerOfAnOpenAthlete() = testApplication {
        start()
        follow(bob, alice)
        assertEquals(true, client.authorized(bob, AthleteSpace.FOLLOWERS))
        assertEquals(false, client.authorized(carol, AthleteSpace.FOLLOWERS), "carol doesn't follow alice")
    }

    @Test
    fun followersTierNeedsApprovalWhenAliceApproves() = testApplication {
        start()
        policy(alice, "approve")
        follow(bob, alice)
        follow(carol, alice)
        assertEquals(false, client.authorized(bob, AthleteSpace.FOLLOWERS), "bob is pending")
        approve(alice, bob)
        assertEquals(true, client.authorized(bob, AthleteSpace.FOLLOWERS))
        assertEquals(false, client.authorized(carol, AthleteSpace.FOLLOWERS), "carol is still pending")
        // An approval at any rkey but the follower's DID doesn't count.
        approve(alice, carol, rkey = "3kapprove")
        assertEquals(false, client.authorized(carol, AthleteSpace.FOLLOWERS))
    }

    @Test
    fun removedFollowerLosesTheFollowersTier() = testApplication {
        start()
        policy(alice, "approve")
        follow(bob, alice)
        approve(alice, bob)
        assertEquals(true, client.authorized(bob, AthleteSpace.FOLLOWERS))
        unapprove(alice, bob)
        assertEquals(false, client.authorized(bob, AthleteSpace.FOLLOWERS))
    }

    @Test
    fun mutualsNeedEffectiveFollowsBothWays() = testApplication {
        start()
        policy(alice, "approve")
        follow(alice, bob)
        follow(bob, alice)
        assertEquals(false, client.authorized(bob), "bob's follow of alice is pending")
        approve(alice, bob)
        assertEquals(true, client.authorized(bob))
        // Turning approval off makes every follow count again.
        unapprove(alice, bob)
        policy(alice, null)
        assertEquals(true, client.authorized(bob))
    }

    @Test
    fun selfUnknownTiersAndOtherTypesAreDenied() = testApplication {
        start()
        follow(alice, bob)
        follow(bob, alice)
        assertEquals(false, client.authorized(bob, AthleteSpace.SELF))
        assertEquals(false, client.authorized(bob, "friends"))
        assertEquals(false, client.authorized(carol, AthleteSpace.FOLLOWERS), "carol follows nobody")
        assertEquals(false, client.authorized(bob, space = space(AthleteSpace.PUBLIC, type = "app.activitytracker.space.other")))
    }

    @Test
    fun appViewReadsEveryTier() = testApplication {
        start()
        // Nobody follows anybody: the AppView syncs every tier whatever the follow graph says.
        for (tier in listOf(AthleteSpace.PUBLIC, AthleteSpace.FOLLOWERS, AthleteSpace.MUTUALS, AthleteSpace.SELF)) {
            assertEquals(true, client.authorized(config.serviceDid, tier), tier)
        }
    }

    @Test
    fun interactionsSpaceAdmitsNoOneButTheAppView() = testApplication {
        start()
        follow(alice, bob)
        follow(bob, alice)
        assertEquals(false, client.authorized(bob, AthleteSpace.INTERACTIONS), "bob is a mutual")
        assertEquals(false, client.authorized(carol, AthleteSpace.INTERACTIONS))
        assertEquals(true, client.authorized(config.serviceDid, AthleteSpace.INTERACTIONS))
        assertEquals(false, client.authorized(config.serviceDid, AthleteSpace.INTERACTIONS, access = CheckUserAccess.WRITE))
        assertEquals(false, client.authorized(bob, AthleteSpace.INTERACTIONS, access = CheckUserAccess.WRITE))
    }

    @Test
    fun appViewIsDeniedWritesUnknownTiersAndOtherTypes() = testApplication {
        start()
        val appView = config.serviceDid
        assertEquals(false, client.authorized(appView, AthleteSpace.FOLLOWERS, access = CheckUserAccess.WRITE))
        assertEquals(false, client.authorized(appView, AthleteSpace.SELF, access = CheckUserAccess.WRITE))
        assertEquals(false, client.authorized(appView, "friends"))
        assertEquals(false, client.authorized(appView, space = space(AthleteSpace.SELF, type = "app.activitytracker.space.other")))
        assertEquals(false, client.authorized(appView, AthleteSpace.SELF, issuer = carol), "only the authority's PDS asks")
    }

    @Test
    fun onlyReadsAreEverAuthorized() = testApplication {
        start()
        follow(alice, bob)
        follow(bob, alice)
        assertEquals(false, client.authorized(bob, access = CheckUserAccess.WRITE))
        assertEquals(false, client.authorized(bob, AthleteSpace.PUBLIC, access = CheckUserAccess.WRITE))
        assertEquals(false, client.authorized(bob, access = "admin"))
    }

    @Test
    fun clientIdDoesNotChangeTheDecision() = testApplication {
        start()
        follow(alice, bob)
        follow(bob, alice)
        assertEquals(true, client.authorized(bob, clientId = "https://app.test/client-metadata.json"))
        assertEquals(false, client.authorized(carol, clientId = "https://app.test/client-metadata.json"))
    }

    @Test
    fun issuerOtherThanTheAuthorityIsDenied() = testApplication {
        start()
        follow(alice, bob)
        follow(bob, alice)
        assertEquals(false, client.authorized(bob, issuer = carol))
    }

    @Test
    fun malformedParamsAreBadRequests() = testApplication {
        start()
        assertEquals(HttpStatusCode.BadRequest, client.check(bob, access = null).status)
        assertEquals(HttpStatusCode.BadRequest, client.check("bob.test").status)
        assertEquals(HttpStatusCode.BadRequest, client.check(bob, space = "at://$alice/app.activitytracker.feed.activity/3k").status)
    }

    @Test
    fun calloutsNeedTheManagingAppAudience() = testApplication {
        start()
        assertEquals(HttpStatusCode.Unauthorized, client.check(bob, issuer = null).status)
        assertEquals(HttpStatusCode.Unauthorized, client.check(bob, aud = service).status)
        assertEquals(HttpStatusCode.Unauthorized, client.check(bob, aud = "$service#activity_appview").status)
        assertEquals(HttpStatusCode.Unauthorized, client.check(bob, lxm = null).status)
        assertEquals(HttpStatusCode.Unauthorized, client.check(bob, lxm = GetProfile.method.nsid).status)
        assertEquals(HttpStatusCode.Unauthorized, client.check(bob, lxm = "com.atproto.simplespace.checkUserAccess").status)
    }

    @Test
    fun theOldMethodNameIsNotServed() = testApplication {
        start()
        val old = "com.atproto.simplespace.checkUserAccess"
        val response =
            client.get("/xrpc/$old") {
                parameter("space", space(AthleteSpace.MUTUALS))
                parameter("user", bob)
                parameter("access", CheckUserAccess.READ)
                header(HttpHeaders.Authorization, "Bearer ${keys.getValue(alice).serviceJwt(alice, config.managingAppService, old)}")
            }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun managingAppTokensDontWorkOnOtherRoutes() = testApplication {
        start()
        val getProfile = GetProfile.method.nsid
        val token = keys.getValue(bob).serviceJwt(bob, config.managingAppService, getProfile)
        val response = client.get("/xrpc/$getProfile?actor=$alice") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
