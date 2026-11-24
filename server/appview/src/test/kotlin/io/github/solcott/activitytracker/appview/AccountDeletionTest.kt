package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.index.Indexer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every table with a column naming an account is cleared when that account is deleted (`Indexer.deleteActor`), through one of its
 * registries, or is listed here with why it stays. Read from the migrated schema, so a new table can't be missed by forgetting it here.
 */
class AccountDeletionTest {
    private val h2 = TestH2()

    @AfterTest fun close() = h2.close()

    @Test
    fun everyTableOfAnAccountsRowsGoesWithIt() {
        val accountTables =
            h2.dataSource.connection.use { connection ->
                connection
                    .prepareStatement(
                        "SELECT DISTINCT LOWER(TABLE_NAME) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = 'PUBLIC' AND " +
                            "LOWER(COLUMN_NAME) IN (${ACCOUNT_COLUMNS.joinToString { "'$it'" }})",
                    )
                    .executeQuery()
                    .use { rows -> buildSet { while (rows.next()) add(rows.getString(1)) } }
            }
        assertTrue("activities" in accountTables && "segment_pins" in accountTables, "the schema is read: $accountTables")
        val covered =
            (Indexer.ACCOUNT_ROWS.map { it.table } + Indexer.HELD_ROWS.map { it.table } + Indexer.ACCOUNT_ROWS_DELETED_BY.keys)
                .map { it.tableName.lowercase() }
                .toSet()
        assertEquals(emptySet(), accountTables - covered - EXEMPT.keys, "tables of an account's rows that deleteActor misses")
        assertEquals(emptySet(), EXEMPT.keys intersect covered, "exempt tables that are deleted after all")
    }

    private companion object {
        /** Column names that hold an account's DID. */
        val ACCOUNT_COLUMNS =
            listOf(
                "did",
                "author_did",
                "author_a",
                "author_b",
                "inviter_did",
                "invitee_did",
                "voter_did",
                "proposer_did",
                "authority_did",
                "moderator_did",
                "admin_did",
            )

        /** Tables kept when an account goes, and why. */
        val EXEMPT =
            mapOf(
                "segment_pins" to "operator data, not the athlete's (segments/segment-pins); a pinned proposal's proposer is only an id",
                "moderation_access" to "the audit log of moderators' views, kept for its retention (moderation/private-subjects)",
                "admin_sessions" to "an admin's own sign-in, which expires",
                "admin_actions" to "the admin audit log, kept for its retention",
                "repo_reindexes" to "an operator's runs, identifiers and counts only, pruned after 90 days (admin/repo-reindex)",
            )
    }
}
