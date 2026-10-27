package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.SpaceSync
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

/** A sync's writes count only while its space is synced: an account deleted meanwhile drops the space, and nothing comes back. */
class SyncedSpaceWriteTest : ModerationTestBase() {
    private val followers = SpaceUri.of(Did(bob), Nsid.SPACE_ATHLETE, Visibility.FOLLOWERS)
    private val run = """{"sport":"run","startedAt":"2026-10-02T07:00:00Z","elapsedTime":1800,"createdAt":"2026-10-02T07:31:00Z"}"""

    private fun write(rkey: String) = runBlocking {
        indexer.spaceWrite(followers, Nsid.FEED_ACTIVITY, rkey, "bafy$rkey", AtJson.parseToJsonElement(run).jsonObject, whileSynced = true)
    }

    private fun activities() = runBlocking { db.tx { Activities.selectAll().count() } }

    @Test
    fun aSyncWriteForASpaceNoLongerSyncedIsDropped() {
        profile(bob)
        write("3kgone")
        assertEquals(0L, activities(), "no space_sync row: the account was deleted, or the space dropped")

        runBlocking {
            db.tx {
                SpaceSync.insert {
                    it[SpaceSync.space] = followers.value
                    it[authorityDid] = bob
                    it[nextAttemptAt] = 0
                    it[createdAt] = 0
                }
            }
        }
        write("3kkept")
        assertEquals(1L, activities())
    }
}
