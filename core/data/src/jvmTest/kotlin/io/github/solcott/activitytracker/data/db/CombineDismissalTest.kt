package io.github.solcott.activitytracker.data.db

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CombineDismissalTest {
    @Test
    fun dismissingAPairHidesItInEitherOrder() = runTest {
        val dao = inMemoryDatabase().combineDismissals()
        dao.put(CombineDismissalEntity.of("did:plc:alice", "at://alice/b", "at://alice/a"))

        assertTrue(dao.dismissed("did:plc:alice", "at://alice/a", "at://alice/b"))
        assertTrue(dao.dismissed("did:plc:alice", "at://alice/b", "at://alice/a"))
        assertFalse(dao.dismissed("did:plc:bob", "at://alice/a", "at://alice/b"))
        assertFalse(dao.dismissed("did:plc:alice", "at://alice/a", "at://alice/c"))
    }
}
