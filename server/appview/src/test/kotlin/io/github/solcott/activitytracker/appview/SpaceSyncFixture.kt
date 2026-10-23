package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.auth.AppViewSigningKey
import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.CommentHides
import io.github.solcott.activitytracker.appview.db.Comments
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.db.Likes
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
import io.github.solcott.activitytracker.lexicon.app.NotifySpace
import io.github.solcott.activitytracker.lexicon.atproto.Space
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import java.util.UUID
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** The AppView as a member and syncer of athletes' spaces, against a fake Spaces PDS: what [SpaceSyncTest] and [SpaceSyncMacTest] share. */
open class SpaceSyncFixture {
    protected val service = "did:web:localhost%3A8081"
    protected val alice = "did:plc:alice"
    protected val bob = "did:plc:bob"
    protected val carol = "did:plc:carol"
    protected val keys = mapOf(alice to TestAccountKey(), bob to TestAccountKey(), carol to TestAccountKey())

    protected val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    protected val clock = MutableClock(Instant.parse("2026-10-14T12:00:00Z"))
    protected val indexer = Indexer(db, { did -> did.value.substringAfterLast(':') + ".test" }, clock)
    protected val config = AppViewConfig.fromEnv(emptyMap())
    protected val verifier = ServiceAuthVerifier(service, keys = { keys[it.value]?.multibase })
    protected val pds = FakeSpacesPds(clock)
    protected val key = AppViewSigningKey(TEST_SIGNING_KEY)
    protected val credentials =
        SpaceCredentials(
            SignedDelegationTokens(Did(config.serviceDid), key.jwtAlg, key::sign),
            pds.client,
            IdentityResolver(pds.client, "http://plc.test", null),
        )
    protected val syncer = SpaceSyncer(db, indexer, SpaceMember(credentials, pds.client), config.syncerService, clock)

    protected fun space(did: String, tier: String) = SpaceUri.of(Did(did), Nsid.SPACE_ATHLETE, tier)

    protected val aliceFollowers = space(alice, AthleteSpace.FOLLOWERS)

    protected fun activity(title: String = "run") =
        """{"${'$'}type":"${Nsid.FEED_ACTIVITY}","sport":"run","title":"$title","startedAt":"2026-10-14T07:00:00Z",""" +
            """"elapsedTime":1800,"createdAt":"2026-10-14T07:31:00Z"}"""

    protected fun streams(rkey: String, visibility: String) =
        """{"${'$'}type":"${Nsid.FEED_ACTIVITY_STREAMS}","activity":"at://$alice/${Nsid.FEED_ACTIVITY}/$rkey",""" +
            """"visibility":"$visibility",""" +
            """"channels":["heartRate"],"createdAt":"2026-10-14T07:31:00Z",""" +
            """"data":{"${'$'}type":"blob","ref":{"${'$'}link":"bafkreihwzzyxpay7qndclffr6usq5te3of4fra7yucklue2b5ckxhq7aeq"},""" +
            """"mimeType":"application/octet-stream","size":10}}"""

    protected fun like(subjectRkey: String) =
        """{"${'$'}type":"${Nsid.FEED_LIKE}","subject":{"uri":"at://$alice/${Nsid.FEED_ACTIVITY}/$subjectRkey",""" +
            """"cid":"bafyreihwzzyxpay7qndclffr6usq5te3of4fra7yucklue2b5ckxhq7aeq"},"createdAt":"2026-10-14T08:00:00Z"}"""

    protected fun comment(subjectRkey: String, audience: String? = null) =
        """{"${'$'}type":"${Nsid.FEED_COMMENT}","subject":{"uri":"at://$alice/${Nsid.FEED_ACTIVITY}/$subjectRkey",""" +
            """"cid":"bafyreihwzzyxpay7qndclffr6usq5te3of4fra7yucklue2b5ckxhq7aeq"},"text":"Nice!",""" +
            audience.let { if (it == null) "" else """"audience":"$it",""" } +
            """"createdAt":"2026-10-14T08:00:00Z"}"""

    /** Alice's hide of bob's comment [commentRkey], and its rkey. */
    protected fun hide(commentRkey: String): Pair<String, String> {
        val comment = AtUri("at://$bob/${Nsid.FEED_COMMENT}/$commentRkey")
        val record = """{"${'$'}type":"${Nsid.FEED_COMMENT_HIDE}","subject":"${comment.value}","createdAt":"2026-10-14T08:00:00Z"}"""
        return runBlocking { commentHideRkey(comment) } to record
    }

    protected fun runDue() = runBlocking { syncer.runDue(this) }

    /** (author, origin) of each indexed like, by rkey. */
    protected fun likes(): Map<String, Pair<String, String>> =
        transaction(db) {
            Likes.selectAll().associate { it[Likes.uri].substringAfterLast('/') to (it[Likes.authorDid] to it[Likes.origin]) }
        }

    /** (origin, audience) of each indexed comment, by rkey. */
    protected fun comments(): Map<String, Pair<String, String>> =
        transaction(db) {
            Comments.selectAll().associate { it[Comments.uri].substringAfterLast('/') to (it[Comments.origin] to it[Comments.audience]) }
        }

    /** The (rkey, origin) of each indexed comment hide, by the rkey of the comment it hides. */
    protected fun hides(): Map<String, Set<String>> =
        transaction(db) {
            CommentHides.selectAll()
                .groupBy({ it[CommentHides.commentUri].substringAfterLast('/') }, { it[CommentHides.origin] })
                .mapValues { it.value.toSet() }
        }

    /** (visibility, origin) of each indexed activity, by rkey. */
    protected fun activities(): Map<String, Pair<String, String>> =
        transaction(db) {
            Activities.selectAll().associate {
                it[Activities.uri].substringAfterLast('/') to (it[Activities.visibility] to it[Activities.origin])
            }
        }

    protected fun syncState(space: SpaceUri) =
        transaction(db) { SpaceSync.selectAll().where { SpaceSync.space eq space.value }.singleOrNull() }

    protected fun commit(did: String, collection: String, rkey: String, record: String?) = runBlocking {
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

    protected suspend fun HttpClient.notifySpace(space: SpaceUri, caller: String = space.authority.value): HttpResponse =
        post("/xrpc/${NotifySpace.method.nsid}") {
            header(HttpHeaders.Authorization, "Bearer ${keys.getValue(caller).serviceJwt(caller, service, NotifySpace.method.nsid)}")
            contentType(ContentType.Application.Json)
            setBody("""{"space":"${space.value}"}""")
        }

    protected suspend fun HttpClient.notifyWrite(
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
                    """"data":{"${'$'}bytes":"AAAA"},"spaceRev":"$rev"}"""
            )
        }

    protected fun HttpRequestData.jsonBody() = AtJson.parseToJsonElement((body as TextContent).text).jsonObject

    protected fun ApplicationTestBuilder.start() {
        application { appView(config, db, verifier, syncer = syncer) }
    }
}
