package io.github.solcott.activitytracker.atproto

import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.crypto.base58Btc
import io.github.solcott.activitytracker.atproto.crypto.p256DidKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** Vectors from `@atproto/crypto`'s `P256Keypair.did()` in the PDS image pinned in docker/pds.Dockerfile. */
class DidKeyTest {
    @Test
    fun matchesAtprotoCryptoForOddY() {
        // RFC 6979 A.2.5's P-256 key.
        val point =
            "0460fed4ba255a9d31c961eb74c6356d68c049b8923b61fa6ce669622e60f29fb67903fe1008b8bc99a41ae9e95628bc64f2f1b20c2d7e9f5177a3c294d4462299"
        assertEquals("did:key:zDnaepBuvsQ8cpsWrVKw8fbpGpvPeNSjVPTWoq6cRqaYzBKVP", p256DidKey(point.hexToByteArray()))
    }

    @Test
    fun matchesAtprotoCryptoForEvenY() {
        val point =
            "04632227ac4cf6ad31e46017b09636a946fdc2b9f8a6f112b905199be2faaf30e74a0ce0611273a0b5882fb4fc4659a2e53242908f79db00a9d049869edd1743e6"
        assertEquals("did:key:zDnaeX6wcdRPuJgPVYv6Lq2eu5ifZGMiDgYHRDx3iYteUAWni", p256DidKey(point.hexToByteArray()))
    }

    @Test
    fun matchesAtprotoCryptoForAnotherOddY() {
        val point =
            "048572256e008ec9005f1d927fbd64d6005fb5097c8a6b0a9d87d197031c3bf31a97961fe3b93676b1e0bea0ef597b3ca8a88b05c8d545cc3b7c0b5e18920d41f1"
        assertEquals("did:key:zDnaereCbJ52TzJg4v6kU8QWy2ZraTK7qxfTakdy8EimUX4qo", p256DidKey(point.hexToByteArray()))
    }

    @Test
    fun base58KeepsLeadingZeros() {
        assertEquals("", byteArrayOf().base58Btc())
        assertEquals("11", byteArrayOf(0, 0).base58Btc())
        assertEquals("1112", byteArrayOf(0, 0, 0, 1).base58Btc())
        // "Hello World!" from the base58 draft (draft-msporny-base58).
        assertEquals("2NEpo7TZRRrLZSi2U", "Hello World!".encodeToByteArray().base58Btc())
    }

    @Test
    fun generatedKeyHasP256DidKey() = runTest {
        val didKey = DpopKey.generate().didKey
        assertTrue(didKey.startsWith("did:key:zDna"), didKey)
    }
}
