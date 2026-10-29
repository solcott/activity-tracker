package io.github.solcott.activitytracker.appview.sync

import java.security.MessageDigest
import org.bouncycastle.crypto.digests.Blake3Digest

/**
 * The homomorphic set hash of a space repo, as `@atproto/space`'s `LtHash`: each element expands with BLAKE3 in XOF mode to 1024
 * little-endian u16 lanes, summed into the state mod 2^16. Adding and removing commute, so the state depends only on the set, and its
 * [digest] is the `data` a space commit carries. A record's element is [element].
 */
class LtHash(state: ByteArray? = null) {
    private val lanes = IntArray(LANES)

    init {
        if (state != null) {
            require(state.size == STATE_BYTES) { "LtHash state must be $STATE_BYTES bytes, got ${state.size}" }
            for (i in 0 until LANES) lanes[i] = (state[2 * i].toInt() and BYTE) or ((state[2 * i + 1].toInt() and BYTE) shl Byte.SIZE_BITS)
        }
    }

    fun add(element: String): LtHash = apply { fold(element, 1) }

    fun remove(element: String): LtHash = apply { fold(element, -1) }

    /** The 2048-byte state, for storing; a new array each call. */
    fun state(): ByteArray =
        ByteArray(STATE_BYTES).also { out ->
            for (i in 0 until LANES) {
                out[2 * i] = lanes[i].toByte()
                out[2 * i + 1] = (lanes[i] shr Byte.SIZE_BITS).toByte()
            }
        }

    /** sha256 of the state: a commit's `data`. */
    fun digest(): ByteArray = MessageDigest.getInstance("SHA-256").digest(state())

    private fun fold(element: String, sign: Int) {
        val expanded = ByteArray(STATE_BYTES)
        Blake3Digest().run {
            val bytes = element.encodeToByteArray()
            update(bytes, 0, bytes.size)
            doFinal(expanded, 0, STATE_BYTES)
        }
        for (i in 0 until LANES) {
            val lane = (expanded[2 * i].toInt() and BYTE) or ((expanded[2 * i + 1].toInt() and BYTE) shl Byte.SIZE_BITS)
            lanes[i] = (lanes[i] + sign * lane) and LANE_MASK
        }
    }

    companion object {
        private const val LANES = 1024
        const val STATE_BYTES = LANES * 2
        private const val BYTE = 0xff
        private const val LANE_MASK = 0xffff

        /** A record's element: `{collection}/{rkey}/{cid}`, as `formatSetHashElement`. */
        fun element(collection: String, rkey: String, cid: String): String = "$collection/$rkey/$cid"
    }
}
