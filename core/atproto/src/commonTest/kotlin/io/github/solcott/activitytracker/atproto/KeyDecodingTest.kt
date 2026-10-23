package io.github.solcott.activitytracker.atproto

import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.crypto.base58Btc
import io.github.solcott.activitytracker.atproto.crypto.base58BtcDecode
import io.github.solcott.activitytracker.atproto.crypto.bytes
import io.github.solcott.activitytracker.atproto.crypto.p256DidKey
import io.github.solcott.activitytracker.atproto.crypto.p256PublicKey
import io.github.solcott.activitytracker.lexicon.Cid
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

/** Multikey, `did:key` and CID decoding, against [SpaceMacVectors]. */
class KeyDecodingTest {
    @Test
    fun base58RoundTrips() {
        assertContentEquals(byteArrayOf(0, 0, 0, 1), base58BtcDecode("1112"))
        assertEquals("Hello World!", base58BtcDecode("2NEpo7TZRRrLZSi2U").decodeToString())
        val bytes = byteArrayOf(0, 0x7F, -1, 0x12, 0, 0x34)
        assertContentEquals(bytes, base58BtcDecode(bytes.base58Btc()))
    }

    @Test
    fun decodesKemMultikeyToItsPoint() {
        assertEquals(SpaceMacVectors.AUTHOR_RAW, p256PublicKey(SpaceMacVectors.AUTHOR_MULTIKEY).toHexString())
        assertEquals(SpaceMacVectors.AUTHOR_RAW, p256PublicKey(SpaceMacVectors.AUTHOR_DID_KEY).toHexString())
        assertEquals(SpaceMacVectors.EPHEMERAL_RAW, p256PublicKey(SpaceMacVectors.KEY_SHARE).toHexString())
    }

    @Test
    fun roundTripsDidKeyOutput() = runTest {
        // Both parities, from DidKeyTest's vectors, then generated keys.
        val odd =
            "0460fed4ba255a9d31c961eb74c6356d68c049b8923b61fa6ce669622e60f29fb6" +
                "7903fe1008b8bc99a41ae9e95628bc64f2f1b20c2d7e9f5177a3c294d4462299"
        val even =
            "04632227ac4cf6ad31e46017b09636a946fdc2b9f8a6f112b905199be2faaf30e7" +
                "4a0ce0611273a0b5882fb4fc4659a2e53242908f79db00a9d049869edd1743e6"
        for (point in listOf(odd, even)) {
            assertEquals(point, p256PublicKey(p256DidKey(point.hexToByteArray())).toHexString())
        }
        repeat(4) {
            val key = DpopKey.generate()
            assertEquals(key.didKey, p256DidKey(p256PublicKey(key.didKey)))
        }
    }

    @Test
    fun rejectsOtherKeys() {
        assertFailsWith<IllegalArgumentException> { p256PublicKey(SpaceMacVectors.K256_DID_KEY) }
        assertFailsWith<IllegalArgumentException> { p256PublicKey(SpaceMacVectors.AUTHOR_MULTIKEY.removePrefix("z")) }
        assertFailsWith<IllegalArgumentException> { p256PublicKey("did:key:zDnae0OIl") }
        // A valid prefix and length whose X has no point on the curve.
        val offCurve = byteArrayOf(0x80.toByte(), 0x24, 0x02) + ByteArray(31) + byteArrayOf(1)
        assertFailsWith<IllegalArgumentException> { p256PublicKey("z" + offCurve.base58Btc()) }
    }

    @Test
    fun cidBytes() {
        assertEquals(SpaceMacVectors.CID_BYTES, Cid(SpaceMacVectors.CID).bytes().toHexString())
        assertFailsWith<IllegalArgumentException> { Cid(SpaceMacVectors.CID_V0).bytes() }
        assertFailsWith<IllegalArgumentException> { Cid("z" + SpaceMacVectors.CID.drop(1)).bytes() }
        assertFailsWith<IllegalArgumentException> { Cid("bafy1").bytes() }
    }
}
