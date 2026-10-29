package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.sync.LtHash
import java.util.Base64
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** [LtHash] against `@atproto/space`'s `tests/lthash.test.ts`, and the empty repo's `data` the Oct 29 alpha gives. */
class LtHashTest {
    private val zeros = ByteArray(LtHash.STATE_BYTES)

    @Test
    fun startsAsZeroBytesWhoseDigestIsTheEmptyRepos() {
        assertContentEquals(zeros, LtHash().state())
        assertEquals(
            "e5a00aa9991ac8a5ee3109844d84a55583bd20572ad3ffcd42792f3c36b183ad",
            LtHash().digest().joinToString("") { String.format(Locale.ROOT, "%02x", it) },
        )
    }

    /** A repo with one record, its `data` as the Oct 29 alpha PDS reported it in `listRepoOps`. */
    @Test
    fun matchesTheReferencePds() {
        val element = LtHash.element("app.activitytracker.smoke.note", "v1", "bafyreibtwq4s6ptpjjrt27lc7hleaofjiufnaeeyoisbatkdvhuj4fsalm")
        assertEquals(
            "LyVhi7oWSXFvsGrxh1lYsl70WSGi/pG9ITvEeXhKANw",
            Base64.getEncoder().withoutPadding().encodeToString(LtHash().add(element).digest()),
        )
    }

    @Test
    fun addThenRemoveReturnsToZero() {
        val hash = LtHash().add("a")
        assertFalse(hash.state().contentEquals(zeros))
        assertContentEquals(zeros, hash.remove("a").state())
    }

    @Test
    fun isOrderIndependent() {
        assertContentEquals(LtHash().add("a").add("b").digest(), LtHash().add("b").add("a").digest())
    }

    @Test
    fun distinguishesElements() {
        assertFalse(LtHash().add("a").state().contentEquals(LtHash().add("b").state()))
    }

    @Test
    fun isAMultiset() {
        val hash = LtHash().add("a").add("a")
        assertFalse(hash.state().contentEquals(zeros))
        assertContentEquals(LtHash().add("a").state(), hash.remove("a").state())
    }

    @Test
    fun removingFromEmptyWrapsAround() {
        assertContentEquals(zeros, LtHash().remove("a").add("a").state())
    }

    @Test
    fun resumesFromItsState() {
        val hash = LtHash().add("a").add("b")
        assertContentEquals(hash.state(), LtHash(hash.state()).state())
        assertContentEquals(LtHash().add("a").add("b").add("c").state(), LtHash(hash.state()).add("c").state())
    }

    @Test
    fun rejectsAStateOfTheWrongLength() {
        assertFailsWith<IllegalArgumentException> { LtHash(ByteArray(32)) }
    }

    @Test
    fun doesNotAliasItsState() {
        val state = ByteArray(LtHash.STATE_BYTES).also { it[0] = 0xff.toByte() }
        val hash = LtHash(state)
        state[0] = 0
        assertEquals(0xff.toByte(), hash.state()[0])
        hash.state()[1] = 1
        assertEquals(0, hash.state()[1].toInt())
    }

    @Test
    fun elementIsThePathAndCid() {
        assertEquals(
            "app.activitytracker.feed.activity/3kf1/bafyreiabc",
            LtHash.element("app.activitytracker.feed.activity", "3kf1", "bafyreiabc"),
        )
    }
}
