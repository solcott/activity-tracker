package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.admin.AccountLookup
import io.github.solcott.activitytracker.appview.admin.AccountSummary
import io.github.solcott.activitytracker.appview.admin.CollectionCounts
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.BskyBlockBackfillFailures
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.appview.db.GroupPrefs
import io.github.solcott.activitytracker.appview.db.SegmentPrefs
import io.github.solcott.activitytracker.appview.db.SpaceSync
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.JetstreamEvent
import io.github.solcott.activitytracker.appview.index.repoHoldingCount
import io.github.solcott.activitytracker.appview.index.repoHoldings
import io.github.solcott.activitytracker.appview.index.spaceRkeyCount
import io.github.solcott.activitytracker.appview.index.spaceRkeys
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GroupMode
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update

/** admin/account-tools: what the account page reads about one account, and the lookup that finds it. */
class AccountSummaryTest {
    private val bob = "did:plc:bob"
    private val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }
    private val indexer =
        Indexer(db, { did -> if (did.value == "did:plc:carol") "handle.invalid" else did.value.substringAfterLast(':') + ".test" })
    private val followers = SpaceUri.of(Did(bob), Nsid.SPACE_ATHLETE, AthleteSpace.FOLLOWERS)
    private val mutuals = SpaceUri.of(Did(bob), Nsid.SPACE_ATHLETE, AthleteSpace.MUTUALS)

    private fun activity(title: String) =
        AtJson.parseToJsonElement(
                """{"sport":"run","title":"$title","startedAt":"2026-10-01T07:00:00Z","elapsedTime":1800,""" +
                    """"createdAt":"2026-10-01T07:31:00Z"}""",
            )
            .jsonObject

    private suspend fun publicActivity(did: String, rkey: String) =
        indexer.handle(
            JetstreamEvent.Commit(
                did = did,
                time = "2026-10-01T08:00:00.000000Z",
                operation = "create",
                collection = Nsid.FEED_ACTIVITY,
                rkey = rkey,
                record = activity(rkey),
                cid = "bafy$rkey",
            ),
        )

    /** Bob: two public activities, one in his followers space, and one moved there from the repo (so its repo copy is shadowed). */
    private fun seedBob() = runBlocking {
        publicActivity(bob, "3ka1")
        publicActivity(bob, "3ka2")
        publicActivity(bob, "3kmoved")
        indexer.spaceWrite(followers, Nsid.FEED_ACTIVITY, "3kf1", "bafyf1", activity("f1"))
        indexer.spaceWrite(followers, Nsid.FEED_ACTIVITY, "3kmoved", "bafymoved2", activity("moved"))
    }

    @Test
    fun countHelpersMatchTheListings() {
        seedBob()
        runBlocking {
            db.tx {
                assertEquals(repoHoldings(Did(bob), Nsid.FEED_ACTIVITY).size, repoHoldingCount(Did(bob), Nsid.FEED_ACTIVITY))
                assertEquals(3, repoHoldingCount(Did(bob), Nsid.FEED_ACTIVITY), "two repo rows and one shadowed copy")
                assertEquals(spaceRkeys(followers, Nsid.FEED_ACTIVITY).size, spaceRkeyCount(followers, Nsid.FEED_ACTIVITY))
                assertEquals(2, spaceRkeyCount(followers, Nsid.FEED_ACTIVITY))
            }
        }
    }

    @Test
    fun countsByCollectionAndOrigin() = runBlocking {
        seedBob()
        val summary = AccountSummary.load(db, Did(bob))
        assertTrue(summary.indexed)
        val activities = summary.counts.single { it.collection == Nsid.FEED_ACTIVITY }
        assertEquals(CollectionCounts(Nsid.FEED_ACTIVITY, mapOf("repo" to 3, AthleteSpace.FOLLOWERS to 2)), activities)
        assertTrue(summary.counts.none { c -> c.byOrigin.values.any { it == 0 } }, "zero counts are left out")
        assertTrue(summary.counts.none { it.collection == Nsid.FEED_POST }, "collections with nothing are left out")
        assertEquals(listOf("repo", AthleteSpace.FOLLOWERS), summary.origins)
    }

    @Test
    fun statusHandleAndBackfillState() = runBlocking {
        seedBob()
        db.tx {
            Actors.update({ Actors.did eq bob }) {
                it[status] = "deactivated"
                it[handleCheckDueAt] = 42L
            }
            BskyBlockBackfillFailures.insert {
                it[did] = bob
                it[failures] = 2
                it[lastError] = "IOException: refused"
                it[nextAttemptAt] = 99L
            }
        }
        val summary = AccountSummary.load(db, Did(bob))
        assertEquals("deactivated", summary.status)
        assertEquals("bob.test", summary.handle)
        assertTrue(summary.handleVerified)
        assertEquals(42L, summary.handleCheckDueAt)
        assertEquals(null, summary.blocks.syncedAt)
        assertEquals(2, summary.blocks.failures)
        assertEquals("IOException: refused", summary.blocks.lastError)
        assertEquals(99L, summary.blocks.nextAttemptAt)
    }

    @Test
    fun anInvalidHandleIsShownAsSuch() = runBlocking {
        publicActivity("did:plc:carol", "3kc1")
        val summary = AccountSummary.load(db, Did("did:plc:carol"))
        assertEquals("handle.invalid", summary.handle)
        assertFalse(summary.handleVerified)
        assertEquals("active", summary.status)
    }

    @Test
    fun optInsAreYesOrNoOnly() = runBlocking {
        seedBob()
        assertFalse(AccountSummary.load(db, Did(bob)).grouping)
        db.tx {
            GroupPrefs.insert {
                it[did] = bob
                it[mode] = GroupMode.CONNECTED
                it[updatedAt] = 0
            }
            SegmentPrefs.insert {
                it[did] = bob
                it[matching] = true
                it[updatedAt] = 0
                it[hideFromLeaderboards] = true
            }
        }
        val on = AccountSummary.load(db, Did(bob))
        assertTrue(on.grouping)
        assertTrue(on.segmentMatching)
        db.tx { GroupPrefs.update({ GroupPrefs.did eq bob }) { it[mode] = "sometimes" } }
        assertFalse(AccountSummary.load(db, Did(bob)).grouping, "an unknown mode is off")
    }

    @Test
    fun aFailingSpaceIsListedWithoutItsSecrets() = runBlocking {
        seedBob()
        db.tx {
            SpaceSync.insert {
                it[space] = mutuals.value
                it[authorityDid] = bob
                it[failures] = 3
                it[lastError] = "IOException: timed out"
                it[lastErrorAt] = 7L
                it[notifyToken] = "SECRET-TOKEN"
                it[setHash] = ByteArray(4)
                it[nextAttemptAt] = 8L
                it[createdAt] = 0L
            }
        }
        val space = AccountSummary.load(db, Did(bob)).spaces.single()
        assertEquals(mutuals, space.space)
        assertEquals(3, space.failures)
        assertEquals("IOException: timed out", space.lastError)
        assertEquals(8L, space.nextAttemptAt)
        assertFalse("SECRET-TOKEN" in AccountSummary.load(db, Did(bob)).toString())
    }

    @Test
    fun anUnknownDidIsNotIndexed() = runBlocking {
        val summary = AccountSummary.load(db, Did("did:plc:nobody"))
        assertFalse(summary.indexed)
        assertEquals(null, summary.status)
        assertEquals(emptyList(), summary.counts)
    }

    @Test
    fun lookups() = runBlocking {
        seedBob()
        publicActivity("did:plc:carol", "3kc1")
        assertEquals(AccountLookup.Found(Did(bob)), AccountLookup.of(db, "@Bob.Test"))
        assertEquals(AccountLookup.Found(Did(bob)), AccountLookup.of(db, " $bob "))
        assertEquals(AccountLookup.NoMatch, AccountLookup.of(db, "handle.invalid"), "carol's invalid handle matches nothing")
        assertEquals(AccountLookup.NoMatch, AccountLookup.of(db, "dave.test"))
        assertEquals(AccountLookup.Found(Did("did:plc:nobody")), AccountLookup.of(db, "did:plc:nobody"))
        assertEquals(AccountLookup.Malformed, AccountLookup.of(db, "not a handle"))
        assertEquals(AccountLookup.Malformed, AccountLookup.of(db, "did:example:x/../y"))
    }
}
