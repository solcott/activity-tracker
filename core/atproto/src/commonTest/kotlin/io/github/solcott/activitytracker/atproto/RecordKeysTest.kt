package io.github.solcott.activitytracker.atproto

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RecordKeysTest {
    private val comment = AtUri("at://did:plc:bob/${Nsid.FEED_COMMENT}/3kcom")

    @Test
    fun commentHideRkeyMatchesPythonVector() = runTest {
        // base64.b32encode(hashlib.sha256(uri.encode()).digest()[:20]).decode().lower()
        assertEquals("f76xwufewjetwtn3vcykhcyjnqlvcfes", commentHideRkey(comment))
    }

    @Test
    fun commentHideRkeyIsAValidRecordKey() = runTest {
        val rkey = commentHideRkey(AtUri("at://did:web:localhost%3A8080/${Nsid.FEED_COMMENT}/3kcom"))
        assertEquals(32, rkey.length)
        assertTrue(rkey.all { it in 'a'..'z' || it in '2'..'7' }, rkey)
    }

    @Test
    fun commentHideRkeyIsStableAndPerComment() = runTest {
        assertEquals(commentHideRkey(comment), commentHideRkey(AtUri(comment.value)))
        assertNotEquals(commentHideRkey(comment), commentHideRkey(AtUri("at://did:plc:carol/${Nsid.FEED_COMMENT}/3kcom")))
    }
}
