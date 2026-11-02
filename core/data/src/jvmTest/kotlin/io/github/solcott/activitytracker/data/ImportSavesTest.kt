package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.db.ImportSaveEntity
import io.github.solcott.activitytracker.data.db.inMemoryDatabase
import io.github.solcott.activitytracker.data.importsave.testImportSaves
import io.github.solcott.activitytracker.lexicon.Did
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest

/** Batch saves app-wide (feed/batch-import): one runner per account at a time, and an account's saves dropped when it signs out. */
class ImportSavesTest {
    private val alice = Did("did:plc:alice")
    private val bob = Did("did:plc:bob")

    @Test
    fun oneRunnerPerAccountAtATime() = runTest {
        val saves = testImportSaves(inMemoryDatabase())
        val holding = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async {
            saves.exclusive(alice) {
                holding.complete(Unit)
                release.await()
                "first"
            }
        }
        holding.await()

        assertNull(saves.exclusive(alice) { "second" }, "a second runner for the account leaves the saves to the first")
        assertEquals("other", saves.exclusive(bob) { "other" }, "another account's saves run alongside")
        release.complete(Unit)
        assertEquals("first", first.await())
        assertEquals("again", saves.exclusive(alice) { "again" }, "once the first ends, the next runs")
    }

    @Test
    fun signingOutStopsTheAccountsSavesAndDropsThem() = runTest {
        val db = inMemoryDatabase()
        val saves = testImportSaves(db)
        db.importSaves()
            .plan(ImportSaveEntity("3kalice", alice.value, total = 2, createdAt = 0, choicesJson = "{}"), emptyList(), emptyList())
        db.importSaves().plan(ImportSaveEntity("3kbob", bob.value, total = 2, createdAt = 0, choicesJson = "{}"), emptyList(), emptyList())
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Boolean>()
        saves.launch(alice) {
            try {
                started.complete(Unit)
                awaitCancellation()
            } finally {
                stopped.complete(true)
            }
        }
        started.await()

        saves.signedOut(alice)

        assertTrue(stopped.await(), "the running save stops")
        assertNull(db.importSaves().save("3kalice"), "and is dropped")
        assertEquals("3kbob", db.importSaves().save("3kbob")?.batchId, "another account's save stays")
    }
}
